package com.mulhaq.mcp.tools;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service for fetching real-time weather data from wttr.in.
 *
 * No API key required. The wttr.in service returns JSON weather data
 * including temperature, condition, wind speed, and humidity for any city.
 * This service is registered as an AI-callable tool in ToolConfig.
 */
@Service
public class WeatherService {

    private static final Logger log = LoggerFactory.getLogger(WeatherService.class);
    private static final String WTTR_URL = "https://wttr.in/%s?format=j1";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Constructs WeatherService with HTTP client and JSON parser.
     *
     * @param restTemplate HTTP client for API calls
     * @param objectMapper JSON deserializer
     */
    public WeatherService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Fetches current weather for the given city from wttr.in.
     * Returns a human-readable summary: condition, temp (C/F), wind, humidity.
     *
     * @param city City name (e.g. "Ashburn, VA" or "London")
     * @return Formatted weather summary string
     */
    public String getWeather(String city) {
        try {
            log.debug("Fetching weather for: {}", city);
            String url = String.format(WTTR_URL, city.replace(" ", "+"));
            String json = restTemplate.getForObject(url, String.class);

            JsonNode root = objectMapper.readTree(json);
            JsonNode current = root.path("current_condition").get(0);
            JsonNode area = root.path("nearest_area").get(0);

            String condition  = current.path("weatherDesc").get(0).path("value").asText("Unknown");
            int tempC         = current.path("temp_C").asInt(0);
            int tempF         = current.path("temp_F").asInt(0);
            int windKph       = current.path("windspeedKmph").asInt(0);
            int humidity      = current.path("humidity").asInt(0);
            String areaName   = area.path("areaName").get(0).path("value").asText(city);
            String country    = area.path("country").get(0).path("value").asText("");

            String result = String.format(
                "Weather in %s%s: %s, %d°C (%d°F), Wind: %d km/h, Humidity: %d%%",
                areaName,
                country.isEmpty() ? "" : ", " + country,
                condition, tempC, tempF, windKph, humidity
            );

            log.info("Weather fetched for {}: {}", city, condition);
            return result;

        } catch (Exception e) {
            log.error("Error fetching weather for {}: {}", city, e.getMessage());
            return "Unable to fetch weather for " + city + ": " + e.getMessage();
        }
    }
}
