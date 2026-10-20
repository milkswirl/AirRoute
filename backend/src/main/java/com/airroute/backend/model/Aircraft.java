package com.airroute.backend.model;

public class Aircraft {

    private String callsign;

    private double latitude;
    private double longitude;

    private double altitude;
    private double speed;
    private double heading;

    private String origin;
    private String destination;

    private FlightPhase flightPhase;

    public Aircraft(
            String callsign,
            double latitude,
            double longitude,
            double altitude,
            double speed,
            double heading,
            String origin,
            String destination) {

        this.callsign = callsign;

        this.latitude = latitude;
        this.longitude = longitude;

        this.altitude = altitude;
        this.speed = speed;
        this.heading = heading;

        this.origin = origin;
        this.destination = destination;

        this.flightPhase =
                FlightPhase.WAITING;
    }

    public String getCallsign() {
        return callsign;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public double getAltitude() {
        return altitude;
    }

    public double getSpeed() {
        return speed;
    }

    public double getHeading() {
        return heading;
    }

    public String getOrigin() {
        return origin;
    }

    public String getDestination() {
        return destination;
    }

    public FlightPhase getFlightPhase() {
        return flightPhase;
    }

    public void setLatitude(
            double latitude) {

        this.latitude =
                latitude;
    }

    public void setLongitude(
            double longitude) {

        this.longitude =
                longitude;
    }

    public void setAltitude(
            double altitude) {

        this.altitude =
                altitude;
    }

    public void setSpeed(
            double speed) {

        this.speed =
                speed;
    }

    public void setHeading(
            double heading) {

        this.heading =
                heading;
    }

    public void setFlightPhase(
            FlightPhase flightPhase) {

        this.flightPhase =
                flightPhase;
    }
}