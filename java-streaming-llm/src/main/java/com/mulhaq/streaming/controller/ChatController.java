package com.mulhaq.streaming.controller;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = "http://localhost:*")
public class ChatController {

    private static final Logger logger = LoggerFactory.getLogger(ChatController.class);
    private static final long SSE_TIMEOUT_MS = 30000; // 30 second timeout
    private static final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    private final ChatClient chatClient;

    public ChatController(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public static class ChatRequest {
        @JsonProperty("message")
        public String message;
    }

    public static class SseTokenData {
        @JsonProperty("token")
        public String token;

        public SseTokenData(String token) {
            this.token = token;
        }
    }

    public static class SseErrorData {
        @JsonProperty("error")
        public String error;

        public SseErrorData(String error) {
            this.error = error;
        }
    }

    @PostMapping("/stream")
    public SseEmitter stream(@RequestBody ChatRequest request) {
        // Create a new SseEmitter with 30 second timeout
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        // Process the stream asynchronously to avoid blocking
        executor.execute(() -> {
            try {
                logger.info("Starting stream for message: {}", request.message);

                // Stream the response from the LLM
                chatClient.prompt()
                        .user(request.message)
                        .stream()
                        .content()
                        .doOnNext(token -> {
                            try {
                                // Send each token as a separate SSE event
                                SseTokenData data = new SseTokenData(token);
                                SseEmitter.SseEventBuilder event = SseEmitter.event()
                                        .data(data)
                                        .id(System.nanoTime() + "");

                                emitter.send(event);
                                logger.debug("Sent token: {}", token);
                            } catch (IOException e) {
                                logger.error("Error sending token via SSE", e);
                                emitter.completeWithError(e);
                            }
                        })
                        .doOnError(error -> {
                            logger.error("Error during stream", error);
                            try {
                                SseErrorData errorData = new SseErrorData(
                                        error.getMessage() != null ? error.getMessage() : "Unknown error"
                                );
                                SseEmitter.SseEventBuilder event = SseEmitter.event()
                                        .name("error")
                                        .data(errorData);

                                emitter.send(event);
                                emitter.complete();
                            } catch (Exception e) {
                                logger.debug("Client already disconnected on error");
                            }
                        })
                        .doOnComplete(() -> {
                            try {
                                // Send completion marker
                                SseEmitter.SseEventBuilder event = SseEmitter.event()
                                        .data("[DONE]");
                                emitter.send(event);
                                emitter.complete();
                                logger.info("Stream completed successfully");
                            } catch (IOException e) {
                                logger.error("Error sending completion event", e);
                            }
                        })
                        .blockLast(); // Block until the stream is complete

            } catch (Exception e) {
                logger.error("Unexpected error in stream processing", e);
                try {
                    SseErrorData errorData = new SseErrorData("Unexpected error: " + e.getMessage());
                    SseEmitter.SseEventBuilder event = SseEmitter.event()
                            .name("error")
                            .data(errorData);
                    emitter.send(event);
                    emitter.completeWithError(e);
                } catch (IOException ex) {
                    logger.debug("Client already disconnected on exception");
                }
            }
        });

        return emitter;
    }
}
