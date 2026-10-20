package com.airroute.backend.model;

public class Airport extends Waypoint {
    private String name;
    private String city;

    public Airport(String id, String name, String city,
                   double latitude, double longitude) {
        super(id, latitude, longitude);
        this.name = name;
        this.city = city;
    }

    public String getName() {
        return name;
    }

    public String getCity() {
        return city;
    }
}