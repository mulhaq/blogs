package com.mulhaq.mcp.config;

import com.mulhaq.mcp.tools.WeatherService;
import com.mulhaq.mcp.tools.NewsService;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.boot.web.client.RestClientCustomizer;

import java.time.Duration;
import java.util.function.Function;

/**
 * Spring configuration for Groq AI, HTTP client, and AI tool registration.
 *
 * Wires together:
 *  1. ObjectMapper — tolerant of Groq's extra JSON fields (queue_time etc.)
 *  2. RestClientCustomizer — injects the tolerant mapper into Spring AI's RestClient
 *  3. ChatClient — talks to Groq via Spring AI's OpenAI-compatible integration
 *  4. RestTemplate — used by WeatherService and NewsService for HTTP calls
 *  5. Tool functions — registers get_weather and get_news as callable AI functions
 */
@Configuration
public class GroqConfig {

    /**
     * ObjectMapper configured to ignore unknown JSON properties.
     *
     * Groq adds extra fields (e.g. "queue_time") to its usage response that
     * Spring AI's OpenAiApi$Usage record doesn't declare. Without this override,
     * Jackson throws UnrecognizedPropertyException and the request fails.
     *
     * @return tolerant ObjectMapper
     */
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * RestClientCustomizer that injects our tolerant ObjectMapper into
     * Spring AI's internally-used RestClient.Builder.
     *
     * Spring AI's OpenAI autoconfiguration injects any RestClientCustomizer beans
     * into the RestClient.Builder it uses. This ensures Groq's extra fields
     * (e.g. "queue_time") don't crash parsing during tool calls.
     *
     * @param mapper the tolerant ObjectMapper defined above
     * @return customizer that registers the mapper as a message converter
     */
    @Bean
    public RestClientCustomizer restClientCustomizer(ObjectMapper mapper) {
        return builder -> builder
                .messageConverters(converters -> {
                    converters.removeIf(c -> c instanceof MappingJackson2HttpMessageConverter);
                    converters.add(0, new MappingJackson2HttpMessageConverter(mapper));
                });
    }

    /**
     * ChatClient bean configured to use Groq (via OpenAI-compatible API).
     * API key and base URL come from application.properties.
     *
     * @param builder auto-configured by Spring AI OpenAI starter
     * @return ChatClient ready to send prompts and handle tool calls
     */
    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }

    /**
     * RestTemplate bean with 10-second timeouts, using the tolerant ObjectMapper.
     * Shared by WeatherService and NewsService.
     *
     * @param builder Spring Boot's RestTemplateBuilder
     * @param mapper  our tolerant ObjectMapper
     * @return configured RestTemplate
     */
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder, ObjectMapper mapper) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(10))
                .setReadTimeout(Duration.ofSeconds(10))
                .additionalMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
                .build();
    }

    // ── AI Tool Registrations ────────────────────────────────────────────────
    // Spring AI detects @Bean + @Description + Function<Request,Response>
    // and makes them available as callable tools for the LLM.

    /**
     * Registers the get_weather tool.
     * The LLM calls this when it needs current weather for a city.
     *
     * @param weatherService the service that fetches from wttr.in
     * @return Function that accepts a city name and returns weather data
     */
    @Bean
    @Description("Get current weather for a city. Input: city name (e.g. 'Ashburn, VA'). Returns temperature, condition, wind speed, humidity.")
    public Function<WeatherRequest, String> get_weather(WeatherService weatherService) {
        return request -> weatherService.getWeather(request.city());
    }

    /**
     * Registers the get_news tool.
     * The LLM calls this when it needs the latest news headlines.
     *
     * @param newsService the service that fetches from BBC RSS
     * @return Function that returns top 5 headlines
     */
    @Bean
    @Description("Get the top 5 current news headlines from BBC News. No input needed. Returns latest news titles and brief descriptions.")
    public Function<NewsRequest, String> get_news(NewsService newsService) {
        return request -> newsService.getTopNews();
    }

    // ── Tool Request Records ─────────────────────────────────────────────────

    /**
     * Input model for the get_weather tool.
     *
     * @param city The city to get weather for (e.g. "Ashburn, VA")
     */
    public record WeatherRequest(
            @com.fasterxml.jackson.annotation.JsonProperty(required = true, value = "city")
            String city) {}

    /**
     * Input model for the get_news tool (no parameters needed).
     */
    public record NewsRequest() {}
}
