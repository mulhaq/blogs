# Teaching Your Spring Boot App to Think: LLM Tool Calling with Groq + Spring AI

*Learn how to build a Spring Boot application where an LLM autonomously calls Java methods to fetch real-time data and reason over it before answering user questions.*

---

## The Hook: Why Tool Calling Matters

LLM tool calling is the mechanism that lets language models step outside the chat and fetch real-world data — calling APIs, running computations, or querying databases — before responding. Instead of hallucinating an answer, your LLM gains access to live information. In this tutorial, we'll build a Spring Boot application that demonstrates this pattern: the user asks a question, the Groq LLM autonomously calls Java methods to gather weather and news data, and then synthesizes a thoughtful answer.

## What We're Building

A chat interface where you ask questions like *"Should I go out today in Ashburn, VA?"* and the AI responds by:

1. Calling a `get_weather` tool to fetch current conditions from wttr.in
2. Calling a `get_news` tool to pull top BBC headlines
3. Reasoning over both datasets to give you a practical answer

Here's what the homepage looks like:

![Homepage](https://raw.githubusercontent.com/mulhaq/blogs/main/java-mcp-weather-news/screenshots/01-homepage.png)

The application uses **Spring AI 1.0.0-M1** to orchestrate tool calling with the **Groq API** (running llama-3.3-70b-versatile). No OpenAI subscription required — Groq is fast and free to try.

---

## Prerequisites

- **Java 21**
- **Maven 3.9+**
- **Groq API key** (free at https://console.groq.com)
- Basic Spring Boot knowledge

---

## Project Structure

```
java-mcp-weather-news/
├── pom.xml
└── src/main/
    ├── java/com/mulhaq/mcp/
    │   ├── McpWeatherNewsApplication.java
    │   ├── config/
    │   │   └── GroqConfig.java          ← tool registration + beans
    │   ├── tools/
    │   │   ├── WeatherService.java      ← get_weather tool
    │   │   └── NewsService.java         ← get_news tool
    │   └── controller/
    │       └── ChatController.java      ← POST /api/chat
    └── resources/
        ├── application.properties
        └── static/
            └── index.html               ← chat UI
```

---

## How Tool Registration Works in Spring AI

Spring AI 1.0.0-M1 uses a declarative, annotation-driven pattern for tool registration:

1. **Define a `Function<Request, Response>`** — a standard Java functional interface
2. **Register it as a Spring `@Bean`** — Spring detects it automatically
3. **Annotate with `@Description`** — Spring AI reads this to tell the LLM what the tool does
4. **Reference tools by name in `ChatClient`** — call `.functions("get_weather", "get_news")`

When you call:

```java
chatClient.prompt()
    .system(systemPrompt)
    .user(message)
    .functions("get_weather", "get_news")
    .call()
    .content();
```

Spring AI serializes your function definitions into OpenAI tool schema, sends them to Groq, and handles the full tool-calling loop automatically. If Groq decides it needs data, it returns a tool call. Spring AI invokes the matching function, feeds the result back to Groq, and Groq produces the final answer.

---

## WeatherService.java

Fetches current weather from wttr.in — no API key required:

```java
package com.mulhaq.mcp.tools;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service for fetching real-time weather data from wttr.in.
 * No API key required. Registered as an AI-callable tool in GroqConfig.
 */
@Service
public class WeatherService {

    private static final Logger log = LoggerFactory.getLogger(WeatherService.class);
    private static final String WTTR_URL = "https://wttr.in/%s?format=j1";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public WeatherService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    public String getWeather(String city) {
        try {
            String url = String.format(WTTR_URL, city.replace(" ", "+"));
            String json = restTemplate.getForObject(url, String.class);

            JsonNode root = objectMapper.readTree(json);
            JsonNode current = root.path("current_condition").get(0);
            JsonNode area = root.path("nearest_area").get(0);

            String condition  = current.path("weatherDesc").get(0).path("value").asText("Unknown");
            int tempC         = current.path("temp_C").asInt(0);
            int tempF         = current.path("temp_F").asInt(0);
            int windKph       = current.path("windspeedKmph").asInt(0);
            int humidity      = current.path("humidity").asInt(0);
            String areaName   = area.path("areaName").get(0).path("value").asText(city);
            String country    = area.path("country").get(0).path("value").asText("");

            return String.format(
                "Weather in %s%s: %s, %d°C (%d°F), Wind: %d km/h, Humidity: %d%%",
                areaName,
                country.isEmpty() ? "" : ", " + country,
                condition, tempC, tempF, windKph, humidity
            );
        } catch (Exception e) {
            log.error("Error fetching weather for {}: {}", city, e.getMessage());
            return "Unable to fetch weather for " + city + ": " + e.getMessage();
        }
    }
}
```

The service uses `RestTemplate` to call wttr.in's JSON format endpoint, extracts condition, temperature, wind, and humidity, and returns a human-readable string for the LLM.

---

## NewsService.java

Fetches top 5 headlines from BBC News RSS — no API key required:

```java
package com.mulhaq.mcp.tools;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.*;
import javax.xml.parsers.*;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for fetching top news headlines from the BBC News RSS feed.
 * No API key required. Uses Java DOM parser (not Jackson XML) to
 * correctly handle CDATA sections in the feed.
 */
@Service
public class NewsService {

    private static final Logger log = LoggerFactory.getLogger(NewsService.class);
    private static final String BBC_RSS = "https://feeds.bbci.co.uk/news/rss.xml";

    private final RestTemplate restTemplate;

    public NewsService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public String getTopNews() {
        try {
            String xml = restTemplate.getForObject(BBC_RSS, String.class);

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            DocumentBuilder docBuilder = factory.newDocumentBuilder();

            Document doc = docBuilder.parse(
                new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))
            );
            doc.getDocumentElement().normalize();

            NodeList items = doc.getElementsByTagName("item");
            int limit = Math.min(5, items.getLength());

            List<String> headlines = new ArrayList<>();
            for (int i = 0; i < limit; i++) {
                Element item = (Element) items.item(i);
                String title = getTagText(item, "title");
                String desc  = getTagText(item, "description");

                StringBuilder entry = new StringBuilder((i + 1) + ". " + title);
                if (desc != null && !desc.isBlank()) {
                    String clean = desc.replaceAll("<[^>]*>", "").trim();
                    if (clean.length() > 120) clean = clean.substring(0, 120) + "...";
                    entry.append("\n   ").append(clean);
                }
                headlines.add(entry.toString());
            }

            log.info("Fetched {} news headlines", limit);
            return "Top News Headlines:\n" + String.join("\n", headlines);

        } catch (Exception e) {
            log.error("Error fetching news: {}", e.getMessage());
            return "Unable to fetch news: " + e.getMessage();
        }
    }

    private String getTagText(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        return nodes.getLength() > 0 ? nodes.item(0).getTextContent().trim() : null;
    }
}
```

**Why DOM parser and not Jackson XmlMapper?** BBC's RSS uses CDATA sections for descriptions. Jackson's XmlMapper can deserialize CDATA as raw strings instead of structured objects, causing `Cannot construct instance` errors. Java's DOM parser handles CDATA correctly out of the box.

---

## GroqConfig.java — Tool Registration

This is where Spring AI wires everything together:

```java
package com.mulhaq.mcp.config;

import com.mulhaq.mcp.tools.WeatherService;
import com.mulhaq.mcp.tools.NewsService;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestTemplate;
import java.time.Duration;
import java.util.function.Function;

@Configuration
public class GroqConfig {

    /**
     * Tolerant ObjectMapper — ignores unknown JSON fields.
     * Required because Groq adds "queue_time" to usage responses
     * that Spring AI's OpenAiApi$Usage record doesn't declare.
     */
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * Injects the tolerant ObjectMapper into Spring AI's internal RestClient.
     * Without this, Groq's extra fields crash deserialization.
     */
    @Bean
    public RestClientCustomizer restClientCustomizer(ObjectMapper mapper) {
        return builder -> builder
                .messageConverters(converters -> {
                    converters.removeIf(c -> c instanceof MappingJackson2HttpMessageConverter);
                    converters.add(0, new MappingJackson2HttpMessageConverter(mapper));
                });
    }

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder, ObjectMapper mapper) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(10))
                .setReadTimeout(Duration.ofSeconds(10))
                .additionalMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
                .build();
    }

    // ── Tool Registrations ───────────────────────────────────────────────────

    @Bean
    @Description("Get current weather for a city. Input: city name (e.g. 'Ashburn, VA'). Returns temperature, condition, wind speed, humidity.")
    public Function<WeatherRequest, String> get_weather(WeatherService weatherService) {
        return request -> weatherService.getWeather(request.city());
    }

    @Bean
    @Description("Get the top 5 current news headlines from BBC News. No input needed.")
    public Function<NewsRequest, String> get_news(NewsService newsService) {
        return request -> newsService.getTopNews();
    }

    public record WeatherRequest(
        @com.fasterxml.jackson.annotation.JsonProperty(required = true, value = "city")
        String city) {}

    public record NewsRequest() {}
}
```

The `@Description` annotation is what the LLM reads to understand when to call each tool. Write it clearly — it directly affects how well the model decides to use your tools.

---

## ChatController.java

The REST endpoint:

```java
package com.mulhaq.mcp.controller;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Map;

/**
 * POST /api/chat — accepts a user message, returns AI response.
 * The LLM autonomously decides which tools to call based on the question.
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);
    private final ChatClient chatClient;

    public ChatController(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @PostMapping("/chat")
    public Map<String, String> chat(@RequestBody Map<String, String> request) {
        String message = request.get("message");
        log.debug("Chat request: {}", message);

        try {
            String systemPrompt = """
                    You are a helpful assistant with access to real-time weather and news data.
                    When the user asks about weather, call the get_weather tool with the city name.
                    When the user asks about news, call the get_news tool.
                    When a combined decision is needed (e.g. "should I go out today?"),
                    call both tools and reason over both results to give a helpful answer.
                    Be concise and friendly.
                    """;

            String response = chatClient.prompt()
                    .system(systemPrompt)
                    .user(message)
                    .functions("get_weather", "get_news")
                    .call()
                    .content();

            return Map.of("response", response);

        } catch (Exception e) {
            log.error("Error: {}", e.getMessage(), e);
            return Map.of("response", "Sorry, something went wrong: " + e.getMessage());
        }
    }
}
```

`.functions("get_weather", "get_news")` references the `@Bean` names defined in `GroqConfig`. Spring AI handles everything else — serializing the tool schema, routing calls, and feeding results back.

---

## application.properties

```properties
# Groq API Configuration
# Get your free API key at https://console.groq.com
spring.ai.openai.api-key=YOUR_GROQ_API_KEY_HERE
spring.ai.openai.base-url=https://api.groq.com/openai
spring.ai.openai.chat.options.model=llama-3.3-70b-versatile

server.port=8080

logging.level.root=INFO
logging.level.com.mulhaq.mcp=DEBUG
```

**Important:** The base URL must be `https://api.groq.com/openai` — Spring AI automatically appends `/v1/chat/completions`. If you add `/v1` yourself, you'll get a 404.

---

## Frontend

The vanilla HTML/JS chat UI lives in `src/main/resources/static/index.html`. It sends `POST /api/chat` with `{"message": "..."}` and displays the response. No frameworks, no build step — Spring Boot serves it directly.

See the [full UI in the repo](https://github.com/mulhaq/blogs/tree/main/java-mcp-weather-news).

---

## Demo in Action

**Type your question:**

![Question typed](https://raw.githubusercontent.com/mulhaq/blogs/main/java-mcp-weather-news/screenshots/02-question-typed.png)

**The AI responds with reasoning over real data:**

![AI response](https://raw.githubusercontent.com/mulhaq/blogs/main/java-mcp-weather-news/screenshots/03-ai-response.png)

Behind the scenes in one HTTP request:

1. Message hits `POST /api/chat`
2. Spring AI sends it to Groq with tool definitions
3. Groq returns tool calls: `get_weather("Ashburn, VA")` + `get_news()`
4. Spring AI invokes both Java methods
5. Results are sent back to Groq
6. Groq synthesizes the final answer
7. Response arrives in your browser

---

## Gotchas & Lessons Learned

We hit two real problems. Both are documented here so you don't have to.

### Problem 1: Groq Returns Extra Fields in Usage

```
UnrecognizedPropertyException: Unrecognized field "queue_time"
(class org.springframework.ai.openai.api.OpenAiApi$Usage)
```

Groq adds a `queue_time` field to its usage response. Spring AI 1.0.0-M1's `OpenAiApi$Usage` record doesn't declare it, so Jackson rejects it by default.

**Fix:** Register a `RestClientCustomizer` bean that sets `FAIL_ON_UNKNOWN_PROPERTIES = false` on the message converter used by Spring AI's internal `RestClient`. See `GroqConfig.java` above.

### Problem 2: BBC RSS Feed Uses CDATA

```
Cannot construct instance of RssItem: no String-argument constructor
```

BBC RSS descriptions are wrapped in CDATA sections. Jackson's `XmlMapper` deserializes them as raw strings rather than structured objects when the RSS schema doesn't match perfectly.

**Fix:** Switch to Java's built-in DOM parser (`DocumentBuilderFactory` + `DocumentBuilder`). It handles CDATA correctly, needs no additional dependencies, and is faster for this use case.

---

## What's Next

- **Add more tools:** stock prices, database queries, calendar lookups
- **Stateful conversations:** store history and use it as context
- **Streaming responses:** switch to `SseEmitter` for token-by-token output
- **More cities/sources:** let users pick a city dynamically, add multiple news sources

---

## Conclusion

LLM tool calling in Spring Boot is straightforward with Spring AI. The pattern is declarative: register a `Function` bean, annotate with `@Description`, and reference it by name in `ChatClient.prompt().functions(...)`. The tool-calling loop is handled for you.

We built a working demo that fetches real-time weather and news and returns a reasoned answer. The two real lessons from building it: watch for deserializer quirks when using OpenAI-compatible endpoints with non-OpenAI vendors, and test your tool implementations independently before wiring them into the LLM loop.

The code is open source. Clone it, extend it, build on it.

---

**Full source code:** https://github.com/mulhaq/blogs/tree/main/java-mcp-weather-news

**Run it locally:**

```bash
git clone https://github.com/mulhaq/blogs.git
cd blogs/java-mcp-weather-news
# Edit application.properties and add your Groq API key
mvn spring-boot:run
```

Then visit `http://localhost:8080`.

Happy building.
