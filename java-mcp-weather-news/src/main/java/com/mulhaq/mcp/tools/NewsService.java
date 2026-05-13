package com.mulhaq.mcp.tools;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for fetching top news headlines from the BBC News RSS feed.
 *
 * No API key required. Uses standard Java DOM XML parsing (not Jackson XML)
 * to reliably handle CDATA sections in the BBC RSS feed, extracting the top 5
 * headlines with brief descriptions.
 *
 * This service is registered as an AI-callable tool in GroqConfig.
 */
@Service
public class NewsService {

    private static final Logger log = LoggerFactory.getLogger(NewsService.class);
    private static final String BBC_RSS = "https://feeds.bbci.co.uk/news/rss.xml";

    private final RestTemplate restTemplate;

    /**
     * Constructs NewsService with an HTTP client.
     *
     * @param restTemplate HTTP client for fetching the RSS feed
     */
    public NewsService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Fetches and returns the top 5 news headlines from BBC News RSS.
     * Uses DOM parsing to correctly handle CDATA-wrapped titles and descriptions.
     * Truncates descriptions to 120 characters for readability.
     *
     * @return Formatted string with numbered headlines and brief descriptions
     */
    public String getTopNews() {
        try {
            log.debug("Fetching news from BBC RSS");
            String xml = restTemplate.getForObject(BBC_RSS, String.class);

            // Parse with standard Java DOM (handles CDATA correctly)
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // Disable external entity processing for security
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            DocumentBuilder docBuilder = factory.newDocumentBuilder();

            byte[] xmlBytes = xml.getBytes(StandardCharsets.UTF_8);
            Document doc = docBuilder.parse(new ByteArrayInputStream(xmlBytes));
            doc.getDocumentElement().normalize();

            // Extract <item> elements
            NodeList items = doc.getElementsByTagName("item");
            int limit = Math.min(5, items.getLength());

            if (limit == 0) {
                return "No news items available.";
            }

            List<String> headlines = new ArrayList<>();
            for (int i = 0; i < limit; i++) {
                Element item = (Element) items.item(i);

                String title = getTagText(item, "title");
                String description = getTagText(item, "description");

                StringBuilder entry = new StringBuilder((i + 1) + ". " + title);
                if (description != null && !description.isBlank()) {
                    // Strip any residual HTML tags
                    String clean = description.replaceAll("<[^>]*>", "").trim();
                    if (clean.length() > 120) clean = clean.substring(0, 120) + "...";
                    entry.append("\n   ").append(clean);
                }
                headlines.add(entry.toString());
            }

            log.info("Fetched {} news headlines", limit);
            return "Top News Headlines:\n" + String.join("\n", headlines);

        } catch (Exception e) {
            log.error("Error fetching news: {}", e.getMessage(), e);
            return "Unable to fetch news: " + e.getMessage();
        }
    }

    /**
     * Extracts the text content of the first matching child element.
     * Returns null if the element doesn't exist.
     *
     * @param parent  the parent DOM element
     * @param tagName the child tag name to look up
     * @return text content or null
     */
    private String getTagText(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() > 0) {
            return nodes.item(0).getTextContent().trim();
        }
        return null;
    }
}
