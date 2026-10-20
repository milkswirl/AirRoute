package com.airroute.backend.controller;

import com.airroute.backend.model.Reroute;
import com.airroute.backend.service.SimulationService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@CrossOrigin(origins = {
        "http://localhost:5173",
        "http://localhost:5174"
})
@RestController
@RequestMapping("/api/reroutes")
public class RerouteController {

    private final SimulationService simulationService;

    public RerouteController(
            SimulationService simulationService) {

        this.simulationService =
                simulationService;
    }

    @GetMapping
    public List<Reroute> getReroutes() {

        return simulationService
                .getReroutes();
    }
}
