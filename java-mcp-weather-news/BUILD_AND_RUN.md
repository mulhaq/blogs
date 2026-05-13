# Build and Run Instructions

## Quick Start

### 1. Build the Project
```bash
cd /workspace/java-mcp-weather-news
mvn clean package
```

### 2. Run the Application
```bash
mvn spring-boot:run
```

Or run the JAR directly:
```bash
java -jar target/java-mcp-weather-news-1.0.0.jar
```

### 3. Access the Chat UI
Open your browser to `http://localhost:8080`

## System Requirements

- **Java 21+** (not Java 11 or 17)
- **Maven 3.8+**
- **Internet connection** (for weather and news APIs)

## Project Dependencies

All dependencies are managed by Maven and will be downloaded automatically during the build:

### Spring Boot & Spring AI
- Spring Boot 3.4.0 (latest stable)
- Spring AI 1.1.0 (GA) with MCP support
- Spring Boot Web Starter

### External APIs (No Keys Required!)
- **Weather**: wttr.in (free, no API key)
- **News**: BBC News RSS feed (free, public)

### Libraries
- Jackson (JSON/XML processing)
- Lombok (reduce boilerplate)
- Apache HttpClient 5.3.1

### Groq API Integration
- OpenAI-compatible endpoint: `https://api.groq.com/openai`
- Model: llama-3.3-70b-versatile
- API key: Pre-configured in `application.properties`

## Configuration

The app is pre-configured and ready to run. If you need to change settings, edit `src/main/resources/application.properties`:

```properties
# Groq API (already configured)
spring.ai.openai.api-key=YOUR_GROQ_API_KEY_HERE
spring.ai.openai.base-url=https://api.groq.com/openai
spring.ai.openai.chat.options.model=llama-3.3-70b-versatile

# Server port (change if 8080 is already in use)
server.port=8080

# Logging level
logging.level.root=INFO
logging.level.com.mulhaq.mcp=DEBUG
```

## Troubleshooting

### Java Version Error
```
error: invalid target release: 21
```
**Solution**: Install Java 21+
```bash
java -version  # Check your version
# If not 21+, download from https://adoptium.net
```

### Port Already in Use
```
Caused by: java.net.BindException: Address already in use
```
**Solution**: Change the port in `application.properties` or kill the process using port 8080
```bash
# macOS/Linux
lsof -i :8080 | grep LISTEN | awk '{print $2}' | xargs kill -9

# Windows
netstat -ano | findstr :8080
taskkill /PID <PID> /F
```

### Maven Build Fails
```
[ERROR] Could not find artifact org.springframework.ai:...
```
**Solution**: Ensure Maven can reach the Spring milestone repository. Run:
```bash
mvn clean
mvn install
```

### Weather/News API Failures
These are external services. If they're down:
- wttr.in status: `curl https://wttr.in/status`
- BBC RSS feed: `curl https://feeds.bbci.co.uk/news/rss.xml`

Check logs for details:
```
logging.level.com.mulhaq.mcp=DEBUG
```

## Development

### IDE Setup
1. **IntelliJ IDEA**
   - Open project folder
   - Maven should auto-detect
   - Right-click `pom.xml` → Run Maven → Install

2. **Visual Studio Code**
   - Install "Extension Pack for Java"
   - Maven will auto-detect and download dependencies

3. **Eclipse**
   - File → Import → Existing Maven Projects
   - Select project root

### Running Tests
```bash
mvn test
```

### Building Production JAR
```bash
mvn clean package -DskipTests
```

The JAR will be in `target/java-mcp-weather-news-1.0.0.jar`

## API Endpoints

### Chat Endpoint
```
POST /api/chat
Content-Type: application/json

{
  "message": "What's the weather in New York?"
}

Response:
{
  "response": "Weather in New York, United States:\nCondition: Partly cloudy\nTemperature: 15°C (59°F)\nWind: 12 km/h\nHumidity: 65%"
}
```

### Tool Endpoints (Internal - Called by LLM)
```
POST /api/tool/get_weather?city=Ashburn,VA
POST /api/tool/get_news
```

## Example Usage

### In the Chat UI
1. Type: "Should I go out today in Ashburn, VA?"
2. The AI will:
   - Call `get_weather` to fetch current conditions
   - Call `get_news` to fetch top headlines
   - Generate a reasoned response based on both data sources

### From Command Line
```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message":"What is the weather in London?"}'
```

## File Locations

- **Java Code**: `src/main/java/com/mulhaq/mcp/`
- **Configuration**: `src/main/resources/application.properties`
- **Frontend**: `src/main/resources/static/index.html`
- **Build Output**: `target/`
- **Maven Cache**: `~/.m2/repository/`

## Performance

- **Startup Time**: ~5-10 seconds
- **Chat Response**: ~2-5 seconds (depends on external APIs)
- **Memory Usage**: ~300-500 MB
- **API Timeouts**: 10 seconds (configurable in GroqConfig.java)

## Next Steps

1. ✅ Build the project
2. ✅ Run the application
3. ✅ Open http://localhost:8080 in your browser
4. ✅ Ask questions about weather or news
5. ✅ Watch as the AI fetches live data and responds

For more details, see `README.md` in the project root.
