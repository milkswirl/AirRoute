package com.airroute.backend.model;

import java.util.ArrayList;
import java.util.List;

public class Route {
    private List<Waypoint> waypoints;
    private double totalDistance;

    public Route() {
        this.waypoints = new ArrayList<>();
        this.totalDistance = 0.0;
    }

    public Route(List<Waypoint> waypoints, double totalDistance) {
        this.waypoints = waypoints;
        this.totalDistance = totalDistance;
    }

    public void addWaypoint(Waypoint waypoint) {
        waypoints.add(waypoint);
    }

    public List<Waypoint> getWaypoints() {
        return waypoints;
    }

    public double getTotalDistance() {
        return totalDistance;
    }

    public void setTotalDistance(double totalDistance) {
        this.totalDistance = totalDistance;
    }
}