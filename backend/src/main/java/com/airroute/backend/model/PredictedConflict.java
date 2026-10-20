package com.airroute.backend.model;

public class PredictedConflict {

    private final String aircraftOne;
    private final String aircraftTwo;

    private final double predictedHorizontalDistance;
    private final double predictedVerticalDistance;

    private final int secondsAhead;

    public PredictedConflict(
            String aircraftOne,
            String aircraftTwo,
            double predictedHorizontalDistance,
            double predictedVerticalDistance,
            int secondsAhead) {

        this.aircraftOne = aircraftOne;
        this.aircraftTwo = aircraftTwo;
        this.predictedHorizontalDistance =
                predictedHorizontalDistance;
        this.predictedVerticalDistance =
                predictedVerticalDistance;
        this.secondsAhead = secondsAhead;
    }

    public String getAircraftOne() {
        return aircraftOne;
    }

    public String getAircraftTwo() {
        return aircraftTwo;
    }

    public double getPredictedHorizontalDistance() {
        return predictedHorizontalDistance;
    }

    public double getPredictedVerticalDistance() {
        return predictedVerticalDistance;
    }

    public int getSecondsAhead() {
        return secondsAhead;
    }
}