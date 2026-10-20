package com.airroute.backend.routing;

import com.airroute.backend.model.Waypoint;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AirspaceGraph {

    private final Map<String, Waypoint> waypoints;
    private final Map<String, List<Edge>> adjacencyList;

    public AirspaceGraph() {
        this.waypoints = new HashMap<>();
        this.adjacencyList = new HashMap<>();
    }

    public void addWaypoint(Waypoint waypoint) {
        waypoints.put(waypoint.getId(), waypoint);
        adjacencyList.putIfAbsent(waypoint.getId(), new ArrayList<>());
    }

    public void addConnection(String fromId, String toId, double distance) {
        if (!waypoints.containsKey(fromId) || !waypoints.containsKey(toId)) {
            throw new IllegalArgumentException("Both waypoints must exist in the graph.");
        }

        adjacencyList.get(fromId).add(new Edge(toId, distance));
        adjacencyList.get(toId).add(new Edge(fromId, distance));
    }

    public Waypoint getWaypoint(String id) {
        return waypoints.get(id);
    }

    public List<Edge> getNeighbors(String waypointId) {
        return adjacencyList.getOrDefault(waypointId, new ArrayList<>());
    }

    public Map<String, Waypoint> getWaypoints() {
        return waypoints;
    }

    public static class Edge {
        private final String destinationId;
        private final double distance;

        public Edge(String destinationId, double distance) {
            this.destinationId = destinationId;
            this.distance = distance;
        }

        public String getDestinationId() {
            return destinationId;
        }

        public double getDistance() {
            return distance;
        }
    }
}