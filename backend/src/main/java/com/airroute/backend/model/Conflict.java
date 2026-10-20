package com.airroute.backend.model;

public class Conflict {

    private final String aircraftOne;
    private final String aircraftTwo;

    private final double horizontalDistance;
    private final double verticalDistance;

    public Conflict(
            String aircraftOne,
            String aircraftTwo,
            double horizontalDistance,
            double verticalDistance) {

        this.aircraftOne = aircraftOne;
        this.aircraftTwo = aircraftTwo;
        this.horizontalDistance = horizontalDistance;
        this.verticalDistance = verticalDistance;
    }

    public String getAircraftOne() {
        return aircraftOne;
    }

    public String getAircraftTwo() {
        return aircraftTwo;
    }

    public double getHorizontalDistance() {
        return horizontalDistance;
    }

    public double getVerticalDistance() {
        return verticalDistance;
    }
}