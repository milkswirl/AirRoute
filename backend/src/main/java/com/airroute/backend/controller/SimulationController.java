package com.airroute.backend.controller;

import com.airroute.backend.service.SimulationService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@CrossOrigin(origins = {
        "http://localhost:5173",
        "http://localhost:5174"
})
@RestController
@RequestMapping("/api/simulation")
public class SimulationController {

    private final SimulationService simulationService;

    public SimulationController(
            SimulationService simulationService) {

        this.simulationService =
                simulationService;
    }

    @PostMapping("/start")
    public Map<String, Object> start() {

        simulationService.startSimulation();

        return getStatus();
    }

    @PostMapping("/pause")
    public Map<String, Object> pause() {

        simulationService.pauseSimulation();

        return getStatus();
    }

    @PostMapping("/reset")
    public Map<String, Object> reset() {

        simulationService.resetSimulation();

        return getStatus();
    }

    @PostMapping("/new-scenario")
    public Map<String, Object> newScenario() {

        simulationService.newScenario();

        return getStatus();
    }

    @PostMapping("/speed")
    public Map<String, Object> setSpeed(
            @RequestParam double multiplier) {

        simulationService.setSimulationSpeed(
                multiplier
        );

        return getStatus();
    }

    @GetMapping("/status")
    public Map<String, Object> getStatus() {

        Map<String, Object> status =
                new HashMap<>();

        status.put(
                "paused",
                simulationService.isPaused()
        );

        status.put(
                "scenarioType",
                simulationService.getScenarioType()
        );

        status.put(
                "speedMultiplier",
                simulationService.getSimulationSpeed()
        );

        return status;
    }
}