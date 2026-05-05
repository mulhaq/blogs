package com.mulhaq.loganalyzer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

public class GroqClient {
    private static final String API_URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final String MODEL = "llama-3.3-70b-versatile";
    private final String apiKey;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public GroqClient(String apiKey) {
        this.apiKey = apiKey;
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newHttpClient();
    }

    /**
     * Send a log chunk to Groq API and get analysis.
     * Returns the assistant's response text.
     */
    public String analyzeLogChunk(String logChunk) throws Exception {
        // Build the system prompt for structured analysis
        String systemPrompt = "You are a log analysis expert. Analyze the provided log chunk and return a structured JSON response with: " +
                "root_cause (string), affected_components (array of strings), severity (string: critical/high/medium/low), " +
                "fix_suggestions (array of strings), and summary (string). Be concise but thorough.";

        // Build request body
        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", MODEL);
        requestBody.put("temperature", 0.3);
        requestBody.put("max_tokens", 1024);

        ArrayNode messages = requestBody.putArray("messages");
        
        ObjectNode systemMsg = messages.addObject();
        systemMsg.put("role", "system");
        systemMsg.put("content", systemPrompt);
        
        ObjectNode userMsg = messages.addObject();
        userMsg.put("role", "user");
        userMsg.put("content", "Analyze this log:\n\n" + logChunk);

        // Create HTTP request
        String bodyString = objectMapper.writeValueAsString(requestBody);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(bodyString, StandardCharsets.UTF_8))
                .build();

        // Execute request
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Groq API error: " + response.statusCode() + "\n" + response.body());
        }

        // Parse response and extract content
        JsonNode jsonResponse = objectMapper.readTree(response.body());
        JsonNode choices = jsonResponse.get("choices");
        if (choices == null || choices.size() == 0) {
            throw new RuntimeException("No choices in Groq response");
        }

        return choices.get(0).get("message").get("content").asText();
    }

    /**
     * Parse the Groq response (which should be JSON) into an AnalysisResult.
     */
    public AnalysisResult parseAnalysisResponse(String responseText) throws Exception {
        try {
            // Try to extract JSON from the response (in case there's extra text)
            String jsonStr = extractJsonFromResponse(responseText);
            JsonNode node = objectMapper.readTree(jsonStr);
            
            AnalysisResult result = new AnalysisResult();
            result.setRootCause(node.get("root_cause").asText("Unknown"));
            result.setSeverity(node.get("severity").asText("medium"));
            result.setSummary(node.get("summary").asText(""));
            
            ArrayNode components = (ArrayNode) node.get("affected_components");
            if (components != null) {
                java.util.List<String> compList = new java.util.ArrayList<>();
                components.forEach(c -> compList.add(c.asText()));
                result.setAffectedComponents(compList);
            }
            
            ArrayNode suggestions = (ArrayNode) node.get("fix_suggestions");
            if (suggestions != null) {
                java.util.List<String> suggList = new java.util.ArrayList<>();
                suggestions.forEach(s -> suggList.add(s.asText()));
                result.setFixSuggestions(suggList);
            }
            
            return result;
        } catch (Exception e) {
            // If parsing fails, create a fallback result
            return createFallbackResult(responseText);
        }
    }

    /**
     * Extract JSON object from response text (in case there's markdown or extra text).
     */
    private String extractJsonFromResponse(String text) {
        // Look for JSON object pattern
        int startIdx = text.indexOf('{');
        int endIdx = text.lastIndexOf('}');
        
        if (startIdx >= 0 && endIdx > startIdx) {
            return text.substring(startIdx, endIdx + 1);
        }
        
        return text;
    }

    /**
     * Create a fallback result if parsing fails.
     */
    private AnalysisResult createFallbackResult(String responseText) {
        AnalysisResult result = new AnalysisResult();
        result.setRootCause("Analysis received but format unclear");
        result.setSeverity("medium");
        result.setSummary(responseText.substring(0, Math.min(200, responseText.length())));
        result.setAffectedComponents(java.util.List.of("unknown"));
        result.setFixSuggestions(java.util.List.of("Review full response above"));
        return result;
    }
}
