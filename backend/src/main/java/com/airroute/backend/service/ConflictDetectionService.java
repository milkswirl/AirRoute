package com.airroute.backend.service;

import com.airroute.backend.model.Aircraft;
import com.airroute.backend.model.Conflict;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ConflictDetectionService {

    private static final double HORIZONTAL_LIMIT_NM = 5.0;
    private static final double VERTICAL_LIMIT_FEET = 1000.0;

    public List<Conflict> detectConflicts(
            List<Aircraft> aircraftList) {

        List<Conflict> conflicts =
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

                double horizontalDistance =
                        calculateDistanceNauticalMiles(
                                first.getLatitude(),
                                first.getLongitude(),
                                second.getLatitude(),
                                second.getLongitude()
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

                    Conflict conflict =
                            new Conflict(
                                    first.getCallsign(),
                                    second.getCallsign(),
                                    horizontalDistance,
                                    verticalDistance
                            );

                    conflicts.add(conflict);
                }
            }
        }

        return conflicts;
    }

    private double calculateDistanceNauticalMiles(
            double latitudeOne,
            double longitudeOne,
            double latitudeTwo,
            double longitudeTwo) {

        double earthRadiusNm = 3440.065;

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