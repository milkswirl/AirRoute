package com.airroute.backend.model;

public class WeatherHazard {

    private final String waypointId;
    private final String type;
    private final String severity;
    private final boolean blocked;

    private final double latitude;
    private final double longitude;
    private final double radiusNm;

    public WeatherHazard(
            String waypointId,
            String type,
            String severity,
            boolean blocked,
            double latitude,
            double longitude,
            double radiusNm) {

        this.waypointId = waypointId;
        this.type = type;
        this.severity = severity;
        this.blocked = blocked;
        this.latitude = latitude;
        this.longitude = longitude;
        this.radiusNm = radiusNm;
    }

    public String getWaypointId() {
        return waypointId;
    }

    public String getType() {
        return type;
    }

    public String getSeverity() {
        return severity;
    }

    public boolean isBlocked() {
        return blocked;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public double getRadiusNm() {
        return radiusNm;
    }
}