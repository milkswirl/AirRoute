package com.airroute.backend.controller;

import com.airroute.backend.model.Route;
import com.airroute.backend.model.WeatherHazard;
import com.airroute.backend.service.AirspaceService;
import com.airroute.backend.service.WeatherService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@CrossOrigin(origins = {
        "http://localhost:5173",
        "http://localhost:5174"
})
@RestController
@RequestMapping("/api/weather")
public class WeatherController {

    private final WeatherService weatherService;
    private final AirspaceService airspaceService;

    public WeatherController(
            WeatherService weatherService,
            AirspaceService airspaceService) {

        this.weatherService =
                weatherService;

        this.airspaceService =
                airspaceService;
    }

    @GetMapping
    public List<WeatherHazard> getWeather() {

        return weatherService
                .getHazards();
    }

    @GetMapping("/safe-route")
    public Route getWeatherSafeRoute(
            @RequestParam String from,
            @RequestParam String to) {

        return airspaceService
                .findWeatherSafeRoute(
                        from,
                        to
                );
    }
}