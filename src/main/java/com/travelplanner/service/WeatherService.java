package com.travelplanner.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.travelplanner.api.ApiModels.DailyForecast;
import com.travelplanner.api.ApiModels.WeatherView;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;
import java.util.ArrayList;
import java.util.List;

@Service
public class WeatherService {
    private final RestClient client;
    public WeatherService(RestClient.Builder builder) { client = builder.build(); }

    public WeatherView weatherFor(String city) {
        if (city == null || city.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a destination to see its weather.");
        try {
            JsonNode places = client.get().uri(UriComponentsBuilder.newInstance().scheme("https")
                    .host("geocoding-api.open-meteo.com").path("/v1/search").queryParam("name", city.trim())
                    .queryParam("count", 1).queryParam("language", "en").queryParam("format", "json")
                    .build().encode().toUri()).retrieve().body(JsonNode.class);
            JsonNode place = places == null ? null : places.path("results").path(0);
            if (place == null || place.isMissingNode()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No weather location found for " + city + ".");
            JsonNode forecast = client.get().uri(UriComponentsBuilder.newInstance().scheme("https")
                    .host("api.open-meteo.com").path("/v1/forecast")
                    .queryParam("latitude", place.path("latitude").asDouble())
                    .queryParam("longitude", place.path("longitude").asDouble())
                    .queryParam("current", "temperature_2m,apparent_temperature,precipitation,weather_code,wind_speed_10m")
                    .queryParam("daily", "weather_code,temperature_2m_max,temperature_2m_min")
                    .queryParam("timezone", "auto").queryParam("forecast_days", 5)
                    .build().encode().toUri()).retrieve().body(JsonNode.class);
            if (forecast == null || forecast.path("current").isMissingNode()) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Weather provider returned an incomplete forecast.");
            JsonNode current = forecast.path("current"), daily = forecast.path("daily");
            List<DailyForecast> days = new ArrayList<>();
            for (int i = 0; i < daily.path("time").size(); i++) {
                days.add(new DailyForecast(daily.path("time").get(i).asText(), condition(daily.path("weather_code").get(i).asInt()),
                        daily.path("temperature_2m_min").get(i).asDouble(), daily.path("temperature_2m_max").get(i).asDouble()));
            }
            String name = place.path("name").asText(city.trim()), country = place.path("country").asText("");
            return new WeatherView(country.isBlank() ? name : name + ", " + country, current.path("time").asText(),
                    current.path("temperature_2m").asDouble(), current.path("apparent_temperature").asDouble(),
                    condition(current.path("weather_code").asInt()), current.path("wind_speed_10m").asDouble(),
                    current.path("precipitation").asDouble(), days);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (RestClientException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Weather is temporarily unavailable. Please try again shortly.");
        }
    }

    private String condition(int code) {
        if (code == 0) return "Clear sky";
        if (code <= 2) return "Partly cloudy";
        if (code == 3) return "Overcast";
        if (code <= 48) return "Foggy";
        if (code <= 57) return "Drizzle";
        if (code <= 67) return "Rain";
        if (code <= 77) return "Snow";
        if (code <= 82) return "Rain showers";
        if (code <= 86) return "Snow showers";
        return "Thunderstorms";
    }
}
