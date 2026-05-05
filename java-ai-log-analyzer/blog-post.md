# AI-Powered Log Analyzer in Java: Making Sense of Server Chaos with Groq

> This Java tool uses Groq's LLM API to transform unreadable server logs into structured analysis—root causes, affected components, and fixes—in seconds.

---

## Introduction

Server logs are a paradox. You need them, but they're overwhelming. A cascading failure can produce thousands of lines in minutes, and by the time you've grep'd through them, the incident is already escalating.

We built **java-ai-log-analyzer** to solve this: a small, focused Java tool that feeds raw logs to Groq's LLM API and returns structured, actionable insights. No machine learning pipeline. No training data. Just a clean CLI that turns logs into English.

This article walks through what we built, why we chose the tools we did, and how you can use it—or fork it to build something better.

---

## The Problem: Logs Aren't Intelligence

Here's what happens at 3 AM when your service goes down:

1. You SSH into the server.
2. You tail the logs and see 500 lines of noise.
3. You grep for "ERROR" and get 50 hits.
4. You dig into each one manually.
5. By the time you spot the real culprit, you've lost 20 minutes.

Logs are _events_, not _insights_. They tell you what happened, but not why or what's broken. That analysis job still lands on you.

What if the LLM did that work instead?

The goal was simple:
- **Input**: Raw log file (any size, any format)
- **Output**: Root cause. Affected components. Severity. Fix suggestions. Summary.
- **Constraint**: Do it fast. Without external dependencies or complex setup.

---

## Architecture: Keep It Simple

Three pieces:

1. **LogChunker** — reads the file and breaks it into chunks (logs can be huge; LLM context windows aren't)
2. **GroqClient** — sends each chunk to Groq's API and parses the response
3. **AnalysisResult** — POJO to structure the output

**Why Groq?** Straightforward API, low latency, and `llama-3.3-70b-versatile` is a solid open model — no vendor lock-in.

**Why no external HTTP library?** Java 21's built-in `HttpClient` is excellent. One less dependency, one less thing to shade into the JAR.

**Why Maven Shade?** Single executable JAR you can drop on any machine and run. No classpath surprises.

---

## Project Structure

```
java-ai-log-analyzer/
├── pom.xml
├── README.md
├── sample-logs/
│   └── app.log
└── src/main/java/com/mulhaq/loganalyzer/
    ├── LogAnalyzer.java       — main entry point, CLI arg handling
    ├── GroqClient.java        — Groq API calls
    ├── LogChunker.java        — reads file, chunks into 3000-char segments
    └── AnalysisResult.java    — POJO: rootCause, affectedComponents, severity, fixSuggestions, summary
```

---

## The Build: Stack & Tooling

```xml
<groupId>com.mulhaq</groupId>
<artifactId>java-ai-log-analyzer</artifactId>
<version>1.0</version>

<properties>
    <maven.compiler.source>21</maven.compiler.source>
    <maven.compiler.target>21</maven.compiler.target>
</properties>

<dependencies>
    <dependency>
        <groupId>com.fasterxml.jackson.core</groupId>
        <artifactId>jackson-databind</artifactId>
        <version>2.17.0</version>
    </dependency>
</dependencies>
```

Build: `mvn clean package`. Output: `target/java-ai-log-analyzer-1.0-shaded.jar`.

---

## The Code: Walking Through Key Pieces

### 1. LogChunker — Breaking Up the File

```java
public class LogChunker {
    private static final int CHUNK_SIZE = 3000;

    public static List<String> chunkLogFile(String filePath) throws Exception {
        String content = Files.readString(Paths.get(filePath));
        List<String> chunks = new ArrayList<>();
        int length = content.length();
        for (int i = 0; i < length; i += CHUNK_SIZE) {
            int end = Math.min(i + CHUNK_SIZE, length);
            chunks.add(content.substring(i, end));
        }
        return chunks;
    }
}
```

Why 3000 chars? Small enough to fit in Groq's context window, large enough to give the model real signal.

### 2. GroqClient — Talking to the API

```java
public class GroqClient {
    private static final String API_URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final String MODEL = "llama-3.3-70b-versatile";
    private final String apiKey;
    private final HttpClient httpClient;
    private final ObjectMapper mapper;

    public AnalysisResult analyzeChunk(String logChunk) throws Exception {
        String prompt = "Analyze this server log chunk and provide: root cause, " +
            "affected components, severity (critical/high/medium/low), fix suggestions, " +
            "and a summary. Return as JSON with keys: rootCause, affectedComponents (array), " +
            "severity, fixSuggestions (array), summary.\n\nLog:\n" + logChunk;

        String requestBody = mapper.writeValueAsString(Map.of(
            "model", MODEL,
            "messages", List.of(Map.of("role", "user", "content", prompt)),
            "temperature", 0.3
        ));

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(API_URL))
            .header("Authorization", "Bearer " + apiKey)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build();

        HttpResponse<String> response = httpClient.send(request,
            HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Groq API error: " + response.statusCode());
        }

        var responseJson = mapper.readTree(response.body());
        String content = responseJson.get("choices").get(0)
            .get("message").get("content").asText();
        return mapper.readValue(content, AnalysisResult.class);
    }
}
```

> **Note on the model:** We originally used `llama3-8b-8192` — Groq decommissioned it mid-build. We switched to `llama-3.3-70b-versatile` and the analysis actually got better. Real-world reminder: APIs evolve; don't hardcode model names without a plan to update them.

### 3. AnalysisResult — The POJO

```java
public class AnalysisResult {
    @JsonProperty("rootCause")
    public String rootCause;

    @JsonProperty("affectedComponents")
    public List<String> affectedComponents;

    @JsonProperty("severity")
    public String severity;

    @JsonProperty("fixSuggestions")
    public List<String> fixSuggestions;

    @JsonProperty("summary")
    public String summary;
}
```

### 4. LogAnalyzer — Main Entry Point

```java
public class LogAnalyzer {
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("Usage: java -jar java-ai-log-analyzer-1.0-shaded.jar <log-file>");
            System.exit(1);
        }

        String apiKey = System.getenv("GROQ_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            System.err.println("Error: GROQ_API_KEY environment variable not set.");
            System.exit(1);
        }

        List<String> chunks = LogChunker.chunkLogFile(args[0]);
        GroqClient client = new GroqClient(apiKey);

        System.out.println("=== AI-Powered Log Analyzer ===\n");
        System.out.println("Log file: " + args[0]);
        System.out.println("Total chunks: " + chunks.size() + "\n");

        for (int i = 0; i < chunks.size(); i++) {
            System.out.println("--- Chunk " + (i + 1) + " of " + chunks.size() + " ---");
            AnalysisResult result = client.analyzeChunk(chunks.get(i));
            System.out.println("Root Cause: " + result.rootCause);
            System.out.println("Severity: " + result.severity);
            System.out.println("Affected Components:");
            result.affectedComponents.forEach(c -> System.out.println("  - " + c));
            System.out.println("Fix Suggestions:");
            result.fixSuggestions.forEach(s -> System.out.println("  - " + s));
            System.out.println("Summary: " + result.summary + "\n");
        }

        System.out.println("=== Analysis Complete ===");
    }
}
```

---

## Real Output: What You Actually Get

We ran it against a 3.70 KB sample log file:

```
=== AI-Powered Log Analyzer ===

Log file: sample-logs/app.log
File size: 3.70 KB

Reading and chunking log file...
Total chunks: 2

--- Chunk 1 of 2 ---
Root Cause: Database connection pool exhaustion due to prolonged request processing
Severity: critical
Affected Components:
  - DatabasePool
  - AuthService
  - CacheManager
  - DataSyncService
  - ApiGateway
  - RequestHandler
Fix Suggestions:
  - Increase the database connection pool size
  - Optimize database queries to reduce connection usage
  - Implement connection timeout and retry mechanisms
  - Monitor and alert on connection pool utilization
  - Regularly check for and resolve data inconsistencies
Summary: The application experienced a critical outage due to database connection
pool exhaustion. Requests were queued and timed out as new connections could not
be established.

--- Chunk 2 of 2 ---
Root Cause: Unknown, but a repair was initiated
Severity: low
Affected Components: Database, Connection Pool, Cache Cluster, Request Handler, Data Sync Service
Fix Suggestions:
  - Monitor system logs for similar issues
  - Verify database consistency regularly
  - Review connection pool and cache cluster configurations
Summary: The system experienced an issue requiring a database consistency repair.
Repair was successful and all components are now fully operational.

=== Analysis Complete ===
```

That's what 500 lines of logs distills down to in seconds.

---

## What's Next: Ways to Extend This

**Multi-file aggregation** — Pipe logs from multiple services into one run. The LLM can spot cross-service patterns — cascading failures, race conditions — that single-service analysis would miss.

**Streaming mode** — Tail a live log file and feed chunks in real time. Add a `WatchService` integration and emit alerts as anomalies surface.

**Custom severity rules** — Replace hardcoded categories with a pluggable rule engine. Let teams define what "critical" means for their stack.

**Observability integration** — Forward results to Datadog, Grafana Loki, or PagerDuty. Anomalies become incidents automatically.

**Context injection** — Add metadata — deployment version, load at the time, recent commits — as context. The LLM can then correlate log spikes with operational events.

---

## How to Run It Yourself

**Prerequisites:** Java 21, Maven 3.8+, free Groq API key from [console.groq.com](https://console.groq.com)

```bash
# 1. Set your API key
export GROQ_API_KEY="your-api-key-here"

# 2. Clone the repo
git clone https://github.com/mulhaq/blogs
cd blogs/java-ai-log-analyzer

# 3. Build
mvn clean package

# 4. Run
java -jar target/java-ai-log-analyzer-1.0-shaded.jar sample-logs/app.log

# 5. Save output to file
java -jar target/java-ai-log-analyzer-1.0-shaded.jar app.log > analysis.txt
```

**Common issues:**
- *"GROQ_API_KEY not found"* — make sure it's exported in your current shell session, not just your IDE
- *Connection timeout* — Groq free tier has rate limits; wait 60 seconds and retry
- *Out of memory on huge files* — pre-split with `split -l 1000 app.log` and analyze each batch

---

## Conclusion

Log analysis at scale is a solved problem — if you have a data team and petabytes of storage. For most engineers, logs are a debugging tool that quietly becomes a compliance archive.

This analyzer closes a real gap: cheap intelligence on unstructured data. The pattern is what matters: _identify a recurring friction point in your workflow, feed it to an LLM, measure the time saved_. Logs. Diffs. Incident summaries. Exception traces. Each one is a candidate for intelligent automation.

Try it on your own logs. See what patterns emerge. Then build the next extension. That's where the value lives.

---

**Full source code:** [github.com/mulhaq/blogs](https://github.com/mulhaq/blogs) — folder: `java-ai-log-analyzer`

**Tags:** `#java` `#ai` `#groq` `#devtools`
