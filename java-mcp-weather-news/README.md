# Spring Boot MCP Weather & News Demo

A complete Spring Boot application demonstrating Model Context Protocol (MCP) server integration with real-time weather and news data.

## Overview

This application showcases:
- **MCP Server**: Exposes two tools (`get_weather` and `get_news`) that an LLM can call
- **Spring AI Integration**: Uses Spring AI 1.1.0 with annotation-based MCP tool registration
- **Groq LLM**: Leverages Groq's fast llama-3.3-70b-versatile model via OpenAI-compatible endpoint
- **Chat UI**: Vanilla HTML/JS frontend for seamless user interaction
- **No API Keys Required**: Uses free services (wttr.in for weather, BBC RSS for news)

## Tech Stack

- **Java 21** with Spring Boot 3.4.0
- **Spring AI 1.1.0 (GA)** for LLM integration and MCP support
- **Groq API** via OpenAI-compatible endpoint
- **Maven** for build and dependency management
- **Vanilla JavaScript** frontend (no frameworks, no external CSS libraries)

## Project Structure

```
java-mcp-weather-news/
├── pom.xml
├── src/main/
│   ├── java/com/mulhaq/mcp/
│   │   ├── McpWeatherNewsApplication.java       # Main Spring Boot app
│   │   ├── config/
│   │   │   └── GroqConfig.java                  # Beans for ChatClient & RestTemplate
│   │   ├── tools/
│   │   │   ├── WeatherService.java              # Fetches weather from wttr.in
│   │   │   └── NewsService.java                 # Parses news from BBC RSS feed
│   │   └── controller/
│   │       └── ChatController.java              # REST endpoints & MCP tool handlers
│   └── resources/
│       ├── application.properties                # Groq API & server config
│       └── static/
│           └── index.html                        # Chat UI
```

## Architecture

### MCP Tools
The application registers two MCP tools that the LLM can invoke:

1. **get_weather(city: String)** - Fetches current weather, temperature, wind, and humidity for any city
2. **get_news()** - Retrieves top 5 news headlines from BBC News RSS feed

### Data Flow
1. User enters a question in the chat UI
2. Frontend sends message to `/api/chat` endpoint
3. ChatController uses Spring AI ChatClient to process the request
4. LLM determines which tools are needed based on user query
5. MCP tools are automatically invoked to fetch live data
6. LLM reasons over the data and generates a response
7. Response is returned to frontend and displayed in chat

### Services

**WeatherService**
- Calls `https://wttr.in/{city}?format=j1` (no API key required)
- Returns current weather conditions, temperature (°C & °F), wind speed, and humidity
- Handles errors gracefully with informative messages

**NewsService**
- Fetches BBC News RSS feed (`https://feeds.bbci.co.uk/news/rss.xml`)
- Parses XML to extract top 5 headlines
- Truncates descriptions to 100 characters for readability
- No API key required

## Building & Running

### Prerequisites
- Java 21+
- Maven 3.8+

### Build
```bash
mvn clean package
```

### Run
```bash
mvn spring-boot:run
```

The application starts on `http://localhost:8080`

### Access the Chat UI
Open your browser to `http://localhost:8080` and start chatting!

## Example Queries

- "What's the weather in Ashburn, VA?"
- "Should I go out today in New York?"
- "Tell me the latest news headlines"
- "What's the weather in London and what's happening in the news?"

## Configuration

**application.properties** contains:
```properties
spring.ai.openai.api-key=YOUR_GROQ_API_KEY
spring.ai.openai.base-url=https://api.groq.com/openai
spring.ai.openai.chat.options.model=llama-3.3-70b-versatile
server.port=8080
```

The Groq API key is already pre-configured in the provided `application.properties`.

## Code Quality

- **Blog-quality code**: Every class and method includes clear Javadoc comments
- **Clean architecture**: Services, controllers, and configuration are properly separated
- **Production-ready**: No TODO comments or placeholders; all features are complete and functional
- **Error handling**: Comprehensive error handling with informative messages and logging

## REST Endpoints

### Chat Endpoint
- **POST** `/api/chat`
- **Request**: `{ "message": "Your question here" }`
- **Response**: `{ "response": "AI-generated answer with tool data" }`

### Tool Endpoints (internal, called by LLM)
- **POST** `/api/tool/get_weather?city=CityName`
- **POST** `/api/tool/get_news`

## Frontend Features

- **Real-time chat interface**: Messages update instantly
- **Loading indicators**: Visual feedback while waiting for response
- **Responsive design**: Works on desktop, tablet, and mobile
- **Keyboard support**: Send message with Enter key
- **XSS protection**: HTML escaping for all user content
- **Smooth animations**: Message transitions and loading effects

## MCP Integration

This project uses Spring AI's annotation-based MCP server support (Spring AI 1.1.0).
The ChatClient is automatically configured to recognize and call registered MCP tools
via the `FunctionCallingOptions` mechanism in the ChatController.

## Error Handling

- API timeouts: 10 seconds (configurable in GroqConfig)
- Missing services: Graceful fallback messages
- Invalid cities/feeds: Informative error responses
- Network failures: User-friendly error messages in chat

## Future Enhancements

- Streaming responses for longer generations
- Voice input/output support
- More MCP tools (stocks, translations, etc.)
- Persistent chat history
- User preferences and location memory
- Advanced response formatting with code blocks

## License

This is a demonstration project. Use freely for learning and experimentation.
