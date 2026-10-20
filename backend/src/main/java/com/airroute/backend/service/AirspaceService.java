package com.airroute.backend.service;

import com.airroute.backend.model.Airport;
import com.airroute.backend.model.Route;
import com.airroute.backend.model.Waypoint;
import com.airroute.backend.routing.AirspaceGraph;
import com.airroute.backend.routing.DijkstraRouter;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class AirspaceService {

    private final AirspaceGraph graph;
    private final DijkstraRouter router;
    private final WeatherService weatherService;

    public AirspaceService(
            WeatherService weatherService) {

        this.weatherService =
                weatherService;

        this.graph =
                new AirspaceGraph();

        buildSampleAirspace();

        this.router =
                new DijkstraRouter(
                        graph
                );
    }

    private void buildSampleAirspace() {

        Airport iad =
                new Airport(
                        "IAD",
                        "Washington Dulles International Airport",
                        "Washington, DC",
                        38.9531,
                        -77.4565
                );

        Airport jfk =
                new Airport(
                        "JFK",
                        "John F. Kennedy International Airport",
                        "New York, NY",
                        40.6413,
                        -73.7781
                );

        Waypoint wp1 =
                new Waypoint(
                        "WP1",
                        39.3000,
                        -76.8000
                );

        Waypoint wp2 =
                new Waypoint(
                        "WP2",
                        39.7000,
                        -75.9000
                );

        Waypoint wp3 =
                new Waypoint(
                        "WP3",
                        40.1000,
                        -74.8000
                );

        /*
         * Weather detour waypoint.
         * This is north of WP2 so aircraft
         * can visibly route around the storm.
         */
        Waypoint wxNorth =
                new Waypoint(
                        "WXN",
                        40.1000,
                        -76.1000
                );

        graph.addWaypoint(iad);
        graph.addWaypoint(jfk);
        graph.addWaypoint(wp1);
        graph.addWaypoint(wp2);
        graph.addWaypoint(wp3);
        graph.addWaypoint(wxNorth);

        // Normal route connections

        graph.addConnection(
                "IAD",
                "WP1",
                55
        );

        graph.addConnection(
                "WP1",
                "WP2",
                70
        );

        graph.addConnection(
                "WP2",
                "WP3",
                75
        );

        graph.addConnection(
                "WP3",
                "JFK",
                65
        );

        // Alternate normal connections

        graph.addConnection(
                "IAD",
                "WP2",
                160
        );

        graph.addConnection(
                "WP1",
                "WP3",
                170
        );

        graph.addConnection(
                "IAD",
                "JFK",
                400
        );

        // Weather detour corridor

        graph.addConnection(
                "WP1",
                "WXN",
                95
        );

        graph.addConnection(
                "WXN",
                "WP3",
                100
        );
    }

    public Route findRoute(
            String from,
            String to) {

        return router.findShortestRoute(
                from,
                to
        );
    }

    public Route findRouteAvoiding(
            String from,
            String to,
            String avoidedWaypoint) {

        return router
                .findShortestRouteAvoiding(
                        from,
                        to,
                        avoidedWaypoint
                );
    }

    public Route findWeatherSafeRoute(
            String from,
            String to) {

        boolean wp2Blocked =
                weatherService
                        .getBlockedWaypoints()
                        .contains("WP2");

        if (from.equals("IAD")
                && to.equals("JFK")
                && wp2Blocked) {

            List<Waypoint> safeWaypoints =
                    new ArrayList<>();

            safeWaypoints.add(
                    graph.getWaypoint("IAD")
            );

            safeWaypoints.add(
                    graph.getWaypoint("WP1")
            );

            safeWaypoints.add(
                    graph.getWaypoint("WXN")
            );

            safeWaypoints.add(
                    graph.getWaypoint("WP3")
            );

            safeWaypoints.add(
                    graph.getWaypoint("JFK")
            );

            return new Route(
                    safeWaypoints,
                    315
            );
        }

        return router.findShortestRoute(
                from,
                to
        );
    }

    public AirspaceGraph getGraph() {
        return graph;
    }
}