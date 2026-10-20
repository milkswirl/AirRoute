package com.airroute.backend.service;

import com.airroute.backend.model.Aircraft;
import com.airroute.backend.model.PredictedConflict;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ConflictPredictionService {

    private static final double HORIZONTAL_LIMIT_NM = 5.0;
    private static final double VERTICAL_LIMIT_FEET = 1000.0;

    private static final int LOOKAHEAD_SECONDS = 60;

    public List<PredictedConflict> predictConflicts(
            List<Aircraft> aircraftList) {

        List<PredictedConflict> predictedConflicts =
                new ArrayList<>();

        for (int i = 0;
             i < aircraftList.size();
             i++) {

            for (int j = i + 1;
                 j < aircraftList.size();
                 j++) {

                Aircraft first =
                        aircraftList.get(i);

                Aircraft second =
                        aircraftList.get(j);

                double[] firstFuturePosition =
                        predictPosition(
                                first,
                                LOOKAHEAD_SECONDS
                        );

                double[] secondFuturePosition =
                        predictPosition(
                                second,
                                LOOKAHEAD_SECONDS
                        );

                double horizontalDistance =
                        calculateDistanceNauticalMiles(
                                firstFuturePosition[0],
                                firstFuturePosition[1],
                                secondFuturePosition[0],
                                secondFuturePosition[1]
                        );

                double verticalDistance =
                        Math.abs(
                                first.getAltitude()
                                        - second.getAltitude()
                        );

                if (horizontalDistance
                        <= HORIZONTAL_LIMIT_NM
                        && verticalDistance
                        <= VERTICAL_LIMIT_FEET) {

                    predictedConflicts.add(
                            new PredictedConflict(
                                    first.getCallsign(),
                                    second.getCallsign(),
                                    horizontalDistance,
                                    verticalDistance,
                                    LOOKAHEAD_SECONDS
                            )
                    );
                }
            }
        }

        return predictedConflicts;
    }

    private double[] predictPosition(
            Aircraft aircraft,
            int secondsAhead) {

        double hoursAhead =
                secondsAhead / 3600.0;

        double distanceNm =
                aircraft.getSpeed()
                        * hoursAhead;

        double earthRadiusNm =
                3440.065;

        double angularDistance =
                distanceNm
                        / earthRadiusNm;

        double headingRadians =
                Math.toRadians(
                        aircraft.getHeading()
                );

        double latitudeRadians =
                Math.toRadians(
                        aircraft.getLatitude()
                );

        double longitudeRadians =
                Math.toRadians(
                        aircraft.getLongitude()
                );

        double predictedLatitude =
                Math.asin(
                        Math.sin(latitudeRadians)
                                * Math.cos(angularDistance)
                                + Math.cos(latitudeRadians)
                                * Math.sin(angularDistance)
                                * Math.cos(headingRadians)
                );

        double predictedLongitude =
                longitudeRadians
                        + Math.atan2(
                        Math.sin(headingRadians)
                                * Math.sin(angularDistance)
                                * Math.cos(latitudeRadians),

                        Math.cos(angularDistance)
                                - Math.sin(latitudeRadians)
                                * Math.sin(predictedLatitude)
                );

        return new double[]{
                Math.toDegrees(predictedLatitude),
                Math.toDegrees(predictedLongitude)
        };
    }

    private double calculateDistanceNauticalMiles(
            double latitudeOne,
            double longitudeOne,
            double latitudeTwo,
            double longitudeTwo) {

        double earthRadiusNm =
                3440.065;

        double latitudeOneRadians =
                Math.toRadians(latitudeOne);

        double latitudeTwoRadians =
                Math.toRadians(latitudeTwo);

        double latitudeDifference =
                Math.toRadians(
                        latitudeTwo
                                - latitudeOne
                );

        double longitudeDifference =
                Math.toRadians(
                        longitudeTwo
                                - longitudeOne
                );

        double a =
                Math.sin(
                        latitudeDifference / 2
                )
                        * Math.sin(
                        latitudeDifference / 2
                )
                        + Math.cos(
                        latitudeOneRadians
                )
                        * Math.cos(
                        latitudeTwoRadians
                )
                        * Math.sin(
                        longitudeDifference / 2
                )
                        * Math.sin(
                        longitudeDifference / 2
                );

        double c =
                2
                        * Math.atan2(
                        Math.sqrt(a),
                        Math.sqrt(1 - a)
                );

        return earthRadiusNm * c;
    }
}