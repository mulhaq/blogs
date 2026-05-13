package com.mulhaq.mcp.controller;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Map;

/**
 * REST controller for the chat endpoint.
 *
 * POST /api/chat accepts a user message and returns an AI-generated response.
 * The ChatClient is configured with the get_weather and get_news function tools,
 * so the Groq LLM can autonomously decide to call either or both tools before
 * composing its final answer.
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final ChatClient chatClient;

    /**
     * Constructs ChatController with the pre-built ChatClient.
     *
     * @param chatClient Spring AI ChatClient (wired in GroqConfig)
     */
    public ChatController(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    /**
     * Handles a user chat message with AI tool calling.
     *
     * The LLM receives the user's message along with descriptions of the
     * available tools (get_weather, get_news). It decides which tools to call,
     * receives their results, and then produces a final synthesized answer.
     *
     * @param request JSON body containing "message" key
     * @return JSON body containing "response" key with the AI answer
     */
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

            // .functions() tells Spring AI which tool @Beans to make available to the LLM
            String response = chatClient.prompt()
                    .system(systemPrompt)
                    .user(message)
                    .functions("get_weather", "get_news")
                    .call()
                    .content();

            log.info("Response generated for: {}", message);
            return Map.of("response", response);

        } catch (Exception e) {
            log.error("Error processing request: {}", e.getMessage(), e);
            return Map.of("response", "Sorry, something went wrong: " + e.getMessage());
        }
    }
}
