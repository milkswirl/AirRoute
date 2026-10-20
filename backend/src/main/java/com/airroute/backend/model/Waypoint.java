package com.airroute.backend.model;

public class Waypoint {
    private String id;
    private double latitude;
    private double longitude;

    public Waypoint(String id, double latitude, double longitude) {
        this.id = id;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public String getId() {
        return id;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }
}