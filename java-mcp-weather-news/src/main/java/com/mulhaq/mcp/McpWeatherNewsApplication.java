package com.mulhaq.mcp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main Spring Boot application class for the MCP Weather &amp; News demo.
 * 
 * This application demonstrates integration of Model Context Protocol (MCP)
 * with Spring AI and Groq LLM. It exposes two MCP tools:
 * - get_weather: Fetches real-time weather data for a specified city
 * - get_news: Fetches top news headlines from an RSS feed
 * 
 * The frontend provides a chat interface where users can ask questions,
 * and the AI uses the registered MCP tools to gather data and provide
 * informed responses.
 */
@SpringBootApplication
public class McpWeatherNewsApplication {

	/**
	 * Application entry point. Bootstraps the Spring Boot application.
	 * 
	 * @param args Command-line arguments passed to the application
	 */
	public static void main(String[] args) {
		SpringApplication.run(McpWeatherNewsApplication.class, args);
	}

}
