package com.airroute.backend.controller;

import com.airroute.backend.model.Route;
import com.airroute.backend.service.AirspaceService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@CrossOrigin(origins = {
        "http://localhost:5173",
        "http://localhost:5174"
})
@RestController
@RequestMapping("/api/routes")
public class RouteController {

    private final AirspaceService airspaceService;

    public RouteController(AirspaceService airspaceService) {
        this.airspaceService = airspaceService;
    }

    @GetMapping
    public Route getRoute(
            @RequestParam String from,
            @RequestParam String to) {

        return airspaceService.findRoute(from, to);
    }
}