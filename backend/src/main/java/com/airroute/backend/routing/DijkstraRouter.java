package com.airroute.backend.routing;

import com.airroute.backend.model.Route;
import com.airroute.backend.model.Waypoint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

public class DijkstraRouter {

    private final AirspaceGraph graph;

    public DijkstraRouter(
            AirspaceGraph graph) {

        this.graph = graph;
    }

    public Route findShortestRoute(
            String startId,
            String endId) {

        return findShortestRouteAvoiding(
                startId,
                endId,
                new HashSet<>()
        );
    }

    public Route findShortestRouteAvoiding(
            String startId,
            String endId,
            String avoidedWaypointId) {

        Set<String> avoided =
                new HashSet<>();

        if (avoidedWaypointId != null) {
            avoided.add(
                    avoidedWaypointId
            );
        }

        return findShortestRouteAvoiding(
                startId,
                endId,
                avoided
        );
    }

    public Route findShortestRouteAvoiding(
            String startId,
            String endId,
            Set<String> avoidedWaypoints) {

        if (graph.getWaypoint(startId) == null
                || graph.getWaypoint(endId) == null) {

            throw new IllegalArgumentException(
                    "Start and end waypoints must exist."
            );
        }

        Map<String, Double> distances =
                new HashMap<>();

        Map<String, String> previous =
                new HashMap<>();

        PriorityQueue<NodeDistance> queue =
                new PriorityQueue<>();

        for (String waypointId
                : graph.getWaypoints().keySet()) {

            distances.put(
                    waypointId,
                    Double.POSITIVE_INFINITY
            );
        }

        distances.put(
                startId,
                0.0
        );

        queue.add(
                new NodeDistance(
                        startId,
                        0.0
                )
        );

        while (!queue.isEmpty()) {

            NodeDistance current =
                    queue.poll();

            if (current.distance
                    > distances.get(
                    current.waypointId)) {

                continue;
            }

            if (current.waypointId
                    .equals(endId)) {

                break;
            }

            for (AirspaceGraph.Edge edge
                    : graph.getNeighbors(
                    current.waypointId)) {

                if (avoidedWaypoints.contains(
                        edge.getDestinationId())) {

                    continue;
                }

                double newDistance =
                        current.distance
                                + edge.getDistance();

                if (newDistance
                        < distances.get(
                        edge.getDestinationId())) {

                    distances.put(
                            edge.getDestinationId(),
                            newDistance
                    );

                    previous.put(
                            edge.getDestinationId(),
                            current.waypointId
                    );

                    queue.add(
                            new NodeDistance(
                                    edge.getDestinationId(),
                                    newDistance
                            )
                    );
                }
            }
        }

        if (Double.isInfinite(
                distances.get(endId))) {

            return null;
        }

        List<Waypoint> routeWaypoints =
                new ArrayList<>();

        String currentId =
                endId;

        while (currentId != null) {

            routeWaypoints.add(
                    graph.getWaypoint(
                            currentId
                    )
            );

            currentId =
                    previous.get(
                            currentId
                    );
        }

        Collections.reverse(
                routeWaypoints
        );

        return new Route(
                routeWaypoints,
                distances.get(endId)
        );
    }

    private static class NodeDistance
            implements Comparable<NodeDistance> {

        private final String waypointId;
        private final double distance;

        public NodeDistance(
                String waypointId,
                double distance) {

            this.waypointId =
                    waypointId;

            this.distance =
                    distance;
        }

        @Override
        public int compareTo(
                NodeDistance other) {

            return Double.compare(
                    this.distance,
                    other.distance
            );
        }
    }
}