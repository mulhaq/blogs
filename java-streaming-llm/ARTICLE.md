---
title: "Streaming LLM Responses in Spring Boot: Real-Time Chat Without Async Hell"
published: false
tags: java, spring-boot, streaming, llm, sse
cover_image: "A split-screen showing: (left) a terminal with Spring Boot logs streaming tokens in real-time, (right) a browser window with a chat interface updating live with LLM output. Clean, modern design. Typography: 'Streaming LLM Responses' in bold. Color scheme: cool blues and greens."
---

# Streaming LLM Responses in Spring Boot: Real-Time Chat Without Async Hell

**Suggested tags:** `java`, `spring-boot`, `streaming`, `llm`, `spring-ai`

**GitHub repo:** https://github.com/mulhaq/blogs/tree/main/java-streaming-llm

---

## The Problem

You've seen it a hundred times: ChatGPT typing out an answer word by word. Your users expect that now. But when you try to bolt LLM streaming onto your Spring Boot backend, you hit a wall.

Most tutorials either show you a toy endpoint with hardcoded responses, or they gloss over the real stuff: backpressure, client disconnects, error boundaries, virtual threads. They leave you with a half-working solution that breaks under load or fails silently when the user closes their browser.

I ran into this building a chat feature. We wanted Server-Sent Events (SSE) streaming from Groq's llama-3.3-70b, but Spring AI's documentation didn't cover the full picture. We hit four separate gotchas before we had something solid. This article is what I wish I'd found.

What we're building: a Spring Boot 3.3 app that streams LLM responses token-by-token via SSE, using Spring AI to talk to Groq, Java 21 virtual threads to keep things cheap, and a vanilla JS frontend that actually handles edge cases.

---

## What Is SSE?

Server-Sent Events is a browser API for one-directional, long-lived connections from server to client. Think of it as HTTP's simpler cousin to WebSocket: you don't need bidirectional communication, you don't need fancy upgrade handshakes, and you don't need a separate protocol.

For LLM streaming, SSE is perfect. The server pushes tokens one by one. The client receives them in order. If the client disconnects, the connection closes and we stop streaming. No complexity, no state machine.

**SSE vs WebSocket:**
- **SSE:** Unidirectional, built on HTTP, reconnects automatically, simpler browser API
- **WebSocket:** Bidirectional, separate protocol, requires upgrade, more complex

For "server pushes tokens to browser," SSE wins. It's HTTP all the way down.

The browser consumes SSE through the `EventSource` API (or via fetch + ReadableStream for more control, which we'll use). When the server sends `data: {some json}\n\n`, the browser parses it, deserializes the JSON, and fires an event. Your JS runs a handler. Done.

---

## Project Setup

### Maven Dependencies

Add these to your `pom.xml`:

```xml
<!-- Spring AI (handles OpenAI-compatible APIs like Groq) -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-openai-spring-boot-starter</artifactId>
    <version>1.0.0-M1</version>
</dependency>

<!-- Spring Web (SseEmitter) -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>

<!-- Project Reactor (streaming operators) -->
<dependency>
    <groupId>io.projectreactor</groupId>
    <artifactId>reactor-core</artifactId>
</dependency>
```

### application.properties

```properties
spring.application.name=streaming-llm
server.port=8080
spring.ai.openai.api-key=${GROQ_API_KEY}
spring.ai.openai.base-url=https://api.groq.com/openai
spring.ai.openai.chat.options.model=llama-3.3-70b-versatile
```

**Important:** Note the base URL is `https://api.groq.com/openai`, not `https://api.groq.com/openai/v1`. Spring AI appends `/v1/chat/completions` automatically. If you include `/v1` in the base URL, you'll get a 404 on the double `/v1/v1/chat/completions` endpoint. This is Gotcha #1, and it's a real time-waster.

---

## The Streaming Endpoint

### Why You Need a Configuration Class

Spring AI doesn't auto-wire a `ChatClient` bean. You need to explicitly build it from the `OpenAiChatModel` that Spring Boot creates. This is Gotcha #2.

```java
package com.example.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GroqConfig {
    @Bean
    public ChatClient chatClient(OpenAiChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }
}
```

That's it. This one class unlocks streaming. Without it, you'll get a "no bean found" exception when you inject `ChatClient` into your controller.

### The Core Streaming Endpoint

```java
package com.example.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = "http://localhost:*")
public class ChatController {

    private static final long SSE_TIMEOUT_MS = 30000;
    private static final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    private final ChatClient chatClient;

    public ChatController(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @PostMapping("/stream")
    public SseEmitter stream(@RequestBody ChatRequest request) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        executor.execute(() -> {
            try {
                chatClient.prompt()
                        .user(request.message)
                        .stream()
                        .content()
                        .doOnNext(token -> {
                            try {
                                SseEmitter.SseEventBuilder event = SseEmitter.event()
                                        .data(new SseTokenData(token))
                                        .id(System.nanoTime() + "");
                                emitter.send(event);
                            } catch (IOException e) {
                                emitter.completeWithError(e);
                            }
                        })
                        .doOnError(error -> {
                            try {
                                emitter.send(SseEmitter.event().name("error").data(new SseErrorData(error.getMessage())));
                                emitter.complete();
                            } catch (Exception e) {
                                logger.debug("Client disconnected on error");
                            }
                        })
                        .doOnComplete(() -> {
                            try {
                                emitter.send(SseEmitter.event().data("[DONE]"));
                                emitter.complete();
                            } catch (IOException e) {
                                logger.error("Error sending completion event", e);
                            }
                        })
                        .blockLast();
            } catch (Exception e) {
                try {
                    emitter.send(SseEmitter.event().name("error").data(new SseErrorData(e.getMessage())));
                    emitter.completeWithError(e);
                } catch (IOException ex) {
                    logger.debug("Client already disconnected");
                }
            }
        });

        return emitter;
    }
}
```

Let me walk through this step by step.

**Create the emitter:**
```java
SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
```
This is your connection to the client. The timeout is 30 seconds—if no data is sent in that window, the connection closes (and we want to know about it).

**Dispatch to a virtual thread:**
```java
executor.execute(() -> { ... });
```
We use `Executors.newVirtualThreadPerTaskExecutor()` to spawn a cheap thread per request. Virtual threads are lightweight—they're not OS threads, they're managed by the JVM. This matters because the next line will *block*.

**Build the streaming chain:**
```java
chatClient.prompt()
    .user(request.message)
    .stream()
    .content()
```
This tells Spring AI: "Stream the response as it comes in, token by token." The `.stream()` method returns a Flux (Reactor's async stream type), and `.content()` filters to just the text tokens.

**Handle each token:**
```java
.doOnNext(token -> {
    try {
        SseEmitter.SseEventBuilder event = SseEmitter.event()
                .data(new SseTokenData(token))
                .id(System.nanoTime() + "");
        emitter.send(event);
    } catch (IOException e) {
        emitter.completeWithError(e);
    }
})
```
For each token, we wrap it in an SSE event (with a unique ID for debugging) and send it to the client. If the send fails (e.g., client disconnected), we mark the emitter as errored.

**Handle errors:**
```java
.doOnError(error -> {
    try {
        emitter.send(SseEmitter.event().name("error").data(new SseErrorData(error.getMessage())));
        emitter.complete();
    } catch (Exception e) {
        logger.debug("Client disconnected on error");
    }
})
```
If the LLM stream fails (API timeout, auth error, etc.), we send an error event to the client and mark the emitter complete. If *that* send fails, we just log it—the client is likely gone anyway.

**Handle completion:**
```java
.doOnComplete(() -> {
    try {
        emitter.send(SseEmitter.event().data("[DONE]"));
        emitter.complete();
    } catch (IOException e) {
        logger.error("Error sending completion event", e);
    }
})
```
When the LLM finishes streaming, we send a `[DONE]` marker so the client knows there's no more data coming.

**Block for the stream to finish:**
```java
.blockLast();
```
This is the key: we call `blockLast()` to wait for the entire stream to complete before the executor task ends. Without it, the executor task would return immediately and the connection would close. Because we're in a virtual thread, blocking is cheap.

**Outer catch for unexpected exceptions:**
```java
catch (Exception e) {
    try {
        emitter.send(SseEmitter.event().name("error").data(new SseErrorData(e.getMessage())));
        emitter.completeWithError(e);
    } catch (IOException ex) {
        logger.debug("Client already disconnected");
    }
}
```
If something blows up *outside* the stream chain (e.g., during the ChatClient build), we try to send an error event. If the client is already gone, we just log it as a debug message.

### The Request and Response DTOs

```java
package com.example.dto;

public record ChatRequest(String message) {}

public record SseTokenData(String token) {}

public record SseErrorData(String error) {}
```

---

## The Frontend

### Vanilla JS SSE Parser

Here's how to consume the stream on the browser side:

```javascript
async function streamChat(message) {
    const responseDiv = document.getElementById('response');
    responseDiv.textContent = '';

    const response = await fetch('/api/chat/stream', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ message }),
    });

    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let buffer = '';

    while (true) {
        const { done, value } = await reader.read();
        if (done) break;

        buffer += decoder.decode(value, { stream: true });
        const lines = buffer.split('\n');
        buffer = lines.pop() || '';

        for (const line of lines) {
            if (line.startsWith('data:')) {
                // Handle both "data: " (with space) and "data:" (without space)
                const data = line.startsWith('data: ') ? line.slice(6) : line.slice(5);
                
                if (data === '[DONE]') {
                    console.log('Stream complete');
                    continue;
                }

                try {
                    const parsed = JSON.parse(data);
                    if (parsed.token) {
                        responseDiv.textContent += parsed.token;
                    }
                } catch (e) {
                    console.error('Failed to parse token:', data, e);
                }
            }
        }
    }
}
```

### The `data:` Space Gotcha

This is Gotcha #3. Spring's `SseEmitter` emits events like `data:{...}` without a space after the colon. The SSE spec allows both formats, but many JS tutorials only check for `data: ` (with a space).

In the code above, we handle both:
```javascript
const data = line.startsWith('data: ') ? line.slice(6) : line.slice(5);
```

If the line is `data: {...}`, we skip 6 characters. If it's `data:{...}`, we skip 5. This way, your parser won't break if someone switches SSE libraries.

### Stop Button and Abort Logic

To let users cancel mid-stream:

```javascript
let controller = null;

async function streamChat(message) {
    controller = new AbortController();
    const responseDiv = document.getElementById('response');
    responseDiv.textContent = '';

    const response = await fetch('/api/chat/stream', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ message }),
        signal: controller.signal,
    });

    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let buffer = '';

    while (true) {
        const { done, value } = await reader.read();
        if (done) break;

        buffer += decoder.decode(value, { stream: true });
        const lines = buffer.split('\n');
        buffer = lines.pop() || '';

        for (const line of lines) {
            if (line.startsWith('data:')) {
                const data = line.startsWith('data: ') ? line.slice(6) : line.slice(5);
                if (data === '[DONE]') continue;
                const parsed = JSON.parse(data);
                if (parsed.token) responseDiv.textContent += parsed.token;
            }
        }
    }
}

function stopChat() {
    if (controller) {
        controller.abort();
        console.log('Stream aborted');
    }
}
```

Pass the `signal` to fetch. When you call `abort()`, the fetch is cancelled and the reader closes. On the server side, the `emitter.send()` call will throw an `IOException`, which we catch and handle gracefully.

---

## The Four Gotchas We Hit (And How We Fixed Them)

### Gotcha #1: Double `/v1` in Groq Base URL

**The problem:**

```properties
# WRONG ❌
spring.ai.openai.base-url=https://api.groq.com/openai/v1
```

Spring AI appends `/v1/chat/completions` to your base URL. If you include `/v1`, you get:
```
https://api.groq.com/openai/v1/v1/chat/completions → 404
```

**The fix:**

```properties
# CORRECT ✅
spring.ai.openai.base-url=https://api.groq.com/openai
```

Now Spring appends `/v1/chat/completions` and you get:
```
https://api.groq.com/openai/v1/chat/completions → 200
```

This cost us 20 minutes of debugging. Check your base URL first.

### Gotcha #2: Missing ChatClient Bean

**The problem:**

```java
@RestController
public class ChatController {
    // ❌ This throws "no bean of type ChatClient found"
    @Autowired
    private ChatClient chatClient;
}
```

Spring Boot auto-creates `OpenAiChatModel` but *not* `ChatClient`. The Spring AI docs assume you know this, but they don't always make it loud.

**The fix:**

Create a `@Configuration` class:

```java
@Configuration
public class GroqConfig {
    @Bean
    public ChatClient chatClient(OpenAiChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }
}
```

Now `ChatClient` is a managed bean and you can inject it. Done.

### Gotcha #3: SSE Format Without Space

**The problem:**

Your JS parser expects `data: {json}` (with space), but Spring's `SseEmitter` sends `data:{json}` (no space):

```javascript
// ❌ This misses all tokens
if (line.startsWith('data: ')) {
    const data = line.slice(6);
    // ... process
}
```

You get silence. No errors, just no output.

**The fix:**

Handle both formats:

```javascript
// ✅ This catches both
const data = line.startsWith('data: ') ? line.slice(6) : line.slice(5);
```

This also future-proofs your code if you ever switch SSE libraries.

### Gotcha #4: `blockLast()` in a Platform Thread (Without Virtual Threads)

**The problem:**

If you naively call `blockLast()` in a request handler without offloading to a separate thread:

```java
@PostMapping("/stream")
public SseEmitter stream(@RequestBody ChatRequest request) {
    SseEmitter emitter = new SseEmitter();
    
    // ❌ This blocks the HTTP handler thread for 30+ seconds
    chatClient.prompt()
        .user(request.message)
        .stream()
        .content()
        .doOnNext(/* ... */)
        .blockLast(); // <-- Blocks HERE
    
    return emitter;
}
```

You're now holding a Tomcat thread for the entire duration of the LLM stream. If you get 100 concurrent requests, you've tied up 100 threads. Tomcat's thread pool exhausts. New requests queue. Your server becomes unresponsive.

**The fix:**

Use Java 21 virtual threads:

```java
private static final ExecutorService executor = 
    Executors.newVirtualThreadPerTaskExecutor();

@PostMapping("/stream")
public SseEmitter stream(@RequestBody ChatRequest request) {
    SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
    
    // ✅ Offload to a virtual thread
    executor.execute(() -> {
        chatClient.prompt()
            .user(request.message)
            .stream()
            .content()
            .doOnNext(/* ... */)
            .blockLast(); // <-- Blocks in a virtual thread, not the HTTP handler
    });
    
    return emitter;
}
```

Virtual threads are cheap. The JVM can spin up thousands of them. Blocking in a virtual thread doesn't hog OS resources the way platform threads do. This is why we use them.

If you're on Java 20 or earlier, you'd need to use async chains (`.subscribe()`) instead of `blockLast()`, which gets messy. Upgrade to Java 21+ if possible.

---

## Testing It Locally

### Prerequisites

1. **Java 21+** (check with `java -version`)
2. **Maven** (check with `mvn -v`)
3. **Groq API key** (free at https://console.groq.com)

### Run the App

```bash
GROQ_API_KEY=your_actual_api_key mvn spring-boot:run
```

You should see:
```
Started ChatApplication in 2.341 seconds (process running for 2.523s)
```

### Test the Endpoint

Open a terminal and curl the endpoint:

```bash
curl -X POST http://localhost:8080/api/chat/stream \
  -H "Content-Type: application/json" \
  -d '{"message":"Explain virtual threads in one sentence"}' \
  -N
```

The `-N` flag disables buffering so you see tokens as they arrive. You should see SSE events streaming in:

```
data:{"token":"Virtual"}

data:{"token":" "}

data:{"token":"threads"}

data:{"token":" "}

data:{"token":"are"}

...

data:["DONE"]
```

### Test with a Frontend

Create `src/main/resources/static/index.html`:

```html
<!DOCTYPE html>
<html>
<head>
    <title>Streaming LLM Chat</title>
    <style>
        body { font-family: sans-serif; margin: 20px; }
        #input { width: 80%; padding: 8px; }
        #send { padding: 8px 16px; }
        #response { margin-top: 20px; padding: 10px; border: 1px solid #ccc; min-height: 100px; white-space: pre-wrap; }
    </style>
</head>
<body>
    <h1>Streaming LLM Chat</h1>
    <input type="text" id="input" placeholder="Ask me something..." />
    <button id="send">Send</button>
    <button id="stop">Stop</button>
    <div id="response"></div>

    <script>
        let controller = null;

        document.getElementById('send').addEventListener('click', async () => {
            const message = document.getElementById('input').value;
            if (!message) return;

            controller = new AbortController();
            const responseDiv = document.getElementById('response');
            responseDiv.textContent = 'Loading...';

            const response = await fetch('/api/chat/stream', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ message }),
                signal: controller.signal,
            });

            const reader = response.body.getReader();
            const decoder = new TextDecoder();
            let buffer = '';
            responseDiv.textContent = '';

            while (true) {
                const { done, value } = await reader.read();
                if (done) break;

                buffer += decoder.decode(value, { stream: true });
                const lines = buffer.split('\n');
                buffer = lines.pop() || '';

                for (const line of lines) {
                    if (line.startsWith('data:')) {
                        const data = line.startsWith('data: ') ? line.slice(6) : line.slice(5);
                        if (data === '[DONE]') continue;
                        try {
                            const parsed = JSON.parse(data);
                            if (parsed.token) responseDiv.textContent += parsed.token;
                        } catch (e) {}
                    }
                }
            }
        });

        document.getElementById('stop').addEventListener('click', () => {
            if (controller) controller.abort();
        });
    </script>
</body>
</html>
```

Navigate to `http://localhost:8080`, type a question, and watch tokens stream in.

---

## What's Next

This is a solid foundation, but there's more to build:

### Adding System Prompts

```java
chatClient.prompt()
    .system("You are a helpful coding assistant.")
    .user(request.message)
    .stream()
    // ...
```

### Multi-Turn Conversation History

Store previous exchanges in a database or in-memory map, and pass them to the prompt:

```java
List<Message> history = loadConversationHistory(userId);
var prompt = chatClient.prompt();
for (Message msg : history) {
    if (msg.isFromUser()) {
        prompt.user(msg.content);
    } else {
        prompt.assistant(msg.content);
    }
}
prompt.user(request.message).stream()
    // ...
```

### Rate Limiting

Add Spring's `@RateLimiter` or use a library like Bucket4j:

```java
@PostMapping("/stream")
@Retry(maxAttempts = 3)
@RateLimiter(value = "chatStream", limitRefreshPeriod = "1m", limitForPeriod = 100)
public SseEmitter stream(@RequestBody ChatRequest request) {
    // ...
}
```

### Deploying to Production

- **Use environment variables** for the API key (already doing this with `${GROQ_API_KEY}`)
- **Add auth** (JWT, OAuth2) to protect your endpoint from abuse
- **Set up monitoring** for SSE connection metrics (connections opened, closed, errored)
- **Use a reverse proxy** (nginx, CloudFlare) to handle connection timeouts gracefully
- **Store conversation logs** for auditing and debugging

---

## Conclusion

Streaming LLM responses in Spring Boot is straightforward once you know the gotchas. Use Spring AI's `ChatClient` for the LLM calls, `SseEmitter` for the connection, virtual threads to avoid blocking platform threads, and a careful SSE parser on the frontend.

The code is battle-tested but not production-ready as-is. You'll want to add error budgeting, user auth, rate limits, and proper monitoring before shipping to real users. But the core pattern is solid.

If you get stuck, check the GitHub repo: https://github.com/mulhaq/blogs/tree/main/java-streaming-llm

Good luck, and enjoy watching those tokens stream in.

---

## Full Project Repository

**GitHub:** https://github.com/mulhaq/blogs/tree/main/java-streaming-llm

Clone it, set your Groq API key, and run `mvn spring-boot:run` to see it in action.
