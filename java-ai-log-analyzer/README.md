# Java AI Log Analyzer

An AI-powered log analyzer that uses the Groq API to intelligently analyze log files and provide structured insights about errors, root causes, affected components, and fix suggestions.

## Features

- **Log File Chunking**: Splits large log files into manageable chunks (max 3000 chars each)
- **AI Analysis**: Uses Groq's llama3-8b-8192 model to analyze each chunk
- **Structured Output**: Returns analysis in JSON format with:
  - Root cause identification
  - Affected components
  - Severity level (critical/high/medium/low)
  - Fix suggestions
  - Summary
- **Built with Java 21**: Uses modern Java features and built-in HttpClient

## Requirements

- **Java 21** (JDK 21 or later)
- **Maven** 3.6+
- **Groq API Key** (set as environment variable `GROQ_API_KEY`)

## Installation

Clone the repository and navigate to the project directory:

```bash
git clone https://github.com/mulhaq/java-ai-log-analyzer.git
cd java-ai-log-analyzer
```

## Building

Build the project with Maven:

```bash
mvn clean package
```

This will create a fat JAR at `target/java-ai-log-analyzer-1.0-shaded.jar` with all dependencies included.

## Running

### Set the Groq API Key

```bash
export GROQ_API_KEY="your-groq-api-key-here"
```

### Run the analyzer

```bash
java -jar target/java-ai-log-analyzer-1.0-shaded.jar sample-logs/app.log
```

Or with the main JAR:

```bash
java -DGROQ_API_KEY=$GROQ_API_KEY -jar target/java-ai-log-analyzer-1.0.jar sample-logs/app.log
```

## Example Output

```
=== AI-Powered Log Analyzer ===

Log file: sample-logs/app.log
File size: 4.50 KB

Reading and chunking log file...
Total chunks: 2

--- Chunk 1 of 2 ---
Root Cause: Connection pool exhaustion due to high request volume exceeding available database connections
Severity: critical
Affected Components:
  - DatabasePool
  - RequestHandler
  - AuthService
  - ApiGateway
Fix Suggestions:
  - Increase database connection pool size (currently 10)
  - Implement connection pooling with timeout optimization
  - Add request rate limiting to prevent pool exhaustion
  - Monitor and tune connection idle timeout settings
Summary: Database connection pool exhaustion causing cascading failures across services

--- Chunk 2 of 2 ---
...

=== Analysis Complete ===
```

## Project Structure

```
java-ai-log-analyzer/
├── pom.xml                          # Maven configuration
├── README.md                        # This file
├── sample-logs/
│   └── app.log                      # Sample log file with realistic errors
└── src/main/java/com/mulhaq/loganalyzer/
    ├── LogAnalyzer.java             # Main entry point
    ├── LogChunker.java              # File reading and chunking logic
    ├── GroqClient.java              # Groq API integration
    └── AnalysisResult.java          # Result model class
```

## Dependencies

- **Jackson** (2.17.0): JSON parsing and serialization
- **JUnit** (4.13.2): Testing framework

No external HTTP libraries are used; the project uses Java's built-in `java.net.http.HttpClient`.

## API Details

### Groq API

- **Base URL**: https://api.groq.com/openai/v1/chat/completions
- **Model**: llama3-8b-8192
- **Temperature**: 0.3 (low temperature for consistent analysis)
- **Max Tokens**: 1024 per chunk

The analyzer sends each log chunk to the Groq API with a system prompt instructing it to return analysis in structured JSON format.

## Error Handling

- If the API key is not set, the program exits with an error message
- If the log file cannot be read, an error is printed and the program exits
- If Groq API calls fail, individual chunk errors are reported but processing continues
- Response parsing errors are handled with fallback results

## License

MIT

## Author

Mulhaq
