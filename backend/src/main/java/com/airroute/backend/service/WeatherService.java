package com.airroute.backend.service;

import com.airroute.backend.model.WeatherHazard;
import com.airroute.backend.model.Waypoint;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

@Service
public class WeatherService {

    private final Random random;

    private final List<WeatherHazard> activeHazards;

    private final List<WeatherSeed> weatherSeeds;

    public WeatherService() {

        this.random =
                new Random();

        this.activeHazards =
                new ArrayList<>();

        this.weatherSeeds =
                createWeatherSeeds();

        generateNewWeather();
    }

    // ------------------------------------------------
    // GENERATE RANDOM WEATHER ACROSS USA
    // ------------------------------------------------

    public synchronized void generateNewWeather() {

        activeHazards.clear();

        List<WeatherSeed> shuffledSeeds =
                new ArrayList<>(
                        weatherSeeds
                );

        Collections.shuffle(
                shuffledSeeds,
                random
        );

        /*
         * Generate between 2 and 5
         * main weather hazards.
         */
        int hazardCount =
                2 + random.nextInt(4);

        for (
                int i = 0;
                i < hazardCount
                        && i < shuffledSeeds.size();
                i++
        ) {

            WeatherSeed seed =
                    shuffledSeeds.get(i);

            activeHazards.add(
                    createHazard(
                            "WX-" + (i + 1),
                            seed.latitude,
                            seed.longitude,
                            0.9,
                            12.0,
                            30.0
                    )
            );

            /*
             * Sometimes generate a second
             * nearby storm to form a cluster.
             */
            if (
                    random.nextDouble()
                            < 0.35
            ) {

                activeHazards.add(
                        createHazard(
                                "WX-"
                                        + (i + 1)
                                        + "-B",

                                seed.latitude,
                                seed.longitude,

                                0.35,

                                8.0,
                                18.0
                        )
                );
            }
        }

        System.out.println(
                "AIRROUTE WEATHER: Generated "
                        + activeHazards.size()
                        + " weather hazards across the USA."
        );
    }

    // ------------------------------------------------
    // CREATE ONE HAZARD
    // ------------------------------------------------

    private WeatherHazard createHazard(
            String id,
            double baseLatitude,
            double baseLongitude,
            double maxOffsetDegrees,
            double minRadiusNm,
            double maxRadiusNm) {

        double latitude =
                baseLatitude
                        + randomOffset(
                        maxOffsetDegrees
                );

        double longitude =
                baseLongitude
                        + randomOffset(
                        maxOffsetDegrees
                );

        double radiusNm =
                minRadiusNm
                        + random.nextDouble()
                        * (
                        maxRadiusNm
                                - minRadiusNm
                );

        return new WeatherHazard(
                id,
                "THUNDERSTORM",
                "SEVERE",
                true,
                latitude,
                longitude,
                radiusNm
        );
    }

    private double randomOffset(
            double maxAbsOffset) {

        return (
                random.nextDouble()
                        * 2.0
                        - 1.0
        ) * maxAbsOffset;
    }

    // ------------------------------------------------
    // ORIGINAL METHODS YOUR PROJECT EXPECTS
    // ------------------------------------------------

    public synchronized List<WeatherHazard> getHazards() {

        return new ArrayList<>(
                activeHazards
        );
    }

    public synchronized Set<String> getBlockedWaypoints() {

        Set<String> blocked =
                new HashSet<>();

        for (WeatherHazard hazard
                : activeHazards) {

            if (
                    hazard.isBlocked()
            ) {

                blocked.add(
                        hazard.getWaypointId()
                );
            }
        }

        return blocked;
    }

    // ------------------------------------------------
    // ADDITIONAL WEATHER METHODS
    // ------------------------------------------------

    public synchronized List<WeatherHazard> getActiveHazards() {

        return new ArrayList<>(
                activeHazards
        );
    }

    public synchronized List<WeatherHazard> getWeatherHazards() {

        return getActiveHazards();
    }

    public synchronized List<WeatherHazard> getCurrentHazards() {

        return getActiveHazards();
    }

    // ------------------------------------------------
    // WAYPOINT WEATHER CHECKING
    // ------------------------------------------------

    public synchronized boolean isWaypointBlocked(
            Waypoint waypoint) {

        return findBlockingHazard(
                waypoint
        ) != null;
    }

    public synchronized WeatherHazard findBlockingHazard(
            Waypoint waypoint) {

        for (WeatherHazard hazard
                : activeHazards) {

            if (
                    !hazard.isBlocked()
            ) {
                continue;
            }

            double distanceNm =
                    calculateDistanceNm(
                            waypoint.getLatitude(),
                            waypoint.getLongitude(),
                            hazard.getLatitude(),
                            hazard.getLongitude()
                    );

            if (
                    distanceNm
                            <= hazard.getRadiusNm()
            ) {

                return hazard;
            }
        }

        return null;
    }

    public synchronized boolean hasBlockingHazardAt(
            double latitude,
            double longitude) {

        for (WeatherHazard hazard
                : activeHazards) {

            if (
                    !hazard.isBlocked()
            ) {
                continue;
            }

            double distanceNm =
                    calculateDistanceNm(
                            latitude,
                            longitude,
                            hazard.getLatitude(),
                            hazard.getLongitude()
                    );

            if (
                    distanceNm
                            <= hazard.getRadiusNm()
            ) {

                return true;
            }
        }

        return false;
    }

    // ------------------------------------------------
    // DISTANCE CALCULATION
    // ------------------------------------------------

    private double calculateDistanceNm(
            double latitudeOne,
            double longitudeOne,
            double latitudeTwo,
            double longitudeTwo) {

        double earthRadiusNm =
                3440.065;

        double lat1 =
                Math.toRadians(
                        latitudeOne
                );

        double lat2 =
                Math.toRadians(
                        latitudeTwo
                );

        double deltaLat =
                Math.toRadians(
                        latitudeTwo
                                - latitudeOne
                );

        double deltaLon =
                Math.toRadians(
                        longitudeTwo
                                - longitudeOne
                );

        double a =
                Math.sin(
                        deltaLat / 2
                )
                        * Math.sin(
                        deltaLat / 2
                )
                        + Math.cos(
                        lat1
                )
                        * Math.cos(
                        lat2
                )
                        * Math.sin(
                        deltaLon / 2
                )
                        * Math.sin(
                        deltaLon / 2
                );

        double c =
                2
                        * Math.atan2(
                        Math.sqrt(a),
                        Math.sqrt(1 - a)
                );

        return earthRadiusNm
                * c;
    }

    // ------------------------------------------------
    // WEATHER REGIONS ACROSS USA
    // ------------------------------------------------

    private List<WeatherSeed> createWeatherSeeds() {

        List<WeatherSeed> seeds =
                new ArrayList<>();

        // West Coast

        seeds.add(
                new WeatherSeed(
                        47.6062,
                        -122.3321
                )
        );

        seeds.add(
                new WeatherSeed(
                        45.5152,
                        -122.6784
                )
        );

        seeds.add(
                new WeatherSeed(
                        37.7749,
                        -122.4194
                )
        );

        seeds.add(
                new WeatherSeed(
                        34.0522,
                        -118.2437
                )
        );

        seeds.add(
                new WeatherSeed(
                        32.7157,
                        -117.1611
                )
        );

        // Southwest / Mountain

        seeds.add(
                new WeatherSeed(
                        36.1699,
                        -115.1398
                )
        );

        seeds.add(
                new WeatherSeed(
                        33.4484,
                        -112.0740
                )
        );

        seeds.add(
                new WeatherSeed(
                        40.7608,
                        -111.8910
                )
        );

        seeds.add(
                new WeatherSeed(
                        39.7392,
                        -104.9903
                )
        );

        seeds.add(
                new WeatherSeed(
                        35.0844,
                        -106.6504
                )
        );

        // Texas / Central

        seeds.add(
                new WeatherSeed(
                        32.7767,
                        -96.7970
                )
        );

        seeds.add(
                new WeatherSeed(
                        30.2672,
                        -97.7431
                )
        );

        seeds.add(
                new WeatherSeed(
                        29.7604,
                        -95.3698
                )
        );

        seeds.add(
                new WeatherSeed(
                        35.4676,
                        -97.5164
                )
        );

        seeds.add(
                new WeatherSeed(
                        39.0997,
                        -94.5786
                )
        );

        // Midwest

        seeds.add(
                new WeatherSeed(
                        44.9778,
                        -93.2650
                )
        );

        seeds.add(
                new WeatherSeed(
                        41.8781,
                        -87.6298
                )
        );

        seeds.add(
                new WeatherSeed(
                        38.6270,
                        -90.1994
                )
        );

        seeds.add(
                new WeatherSeed(
                        42.3314,
                        -83.0458
                )
        );

        seeds.add(
                new WeatherSeed(
                        41.4993,
                        -81.6944
                )
        );

        // Southeast

        seeds.add(
                new WeatherSeed(
                        33.7490,
                        -84.3880
                )
        );

        seeds.add(
                new WeatherSeed(
                        35.2271,
                        -80.8431
                )
        );

        seeds.add(
                new WeatherSeed(
                        36.1627,
                        -86.7816
                )
        );

        seeds.add(
                new WeatherSeed(
                        25.7617,
                        -80.1918
                )
        );

        seeds.add(
                new WeatherSeed(
                        28.5383,
                        -81.3792
                )
        );

        // Northeast

        seeds.add(
                new WeatherSeed(
                        38.9531,
                        -77.4565
                )
        );

        seeds.add(
                new WeatherSeed(
                        38.9072,
                        -77.0369
                )
        );

        seeds.add(
                new WeatherSeed(
                        39.2904,
                        -76.6122
                )
        );

        seeds.add(
                new WeatherSeed(
                        39.9526,
                        -75.1652
                )
        );

        seeds.add(
                new WeatherSeed(
                        40.6895,
                        -74.1745
                )
        );

        seeds.add(
                new WeatherSeed(
                        40.6413,
                        -73.7781
                )
        );

        seeds.add(
                new WeatherSeed(
                        42.3601,
                        -71.0589
                )
        );

        return seeds;
    }

    // ------------------------------------------------
    // INTERNAL WEATHER SEED
    // ------------------------------------------------

    private static class WeatherSeed {

        private final double latitude;
        private final double longitude;

        public WeatherSeed(
                double latitude,
                double longitude) {

            this.latitude =
                    latitude;

            this.longitude =
                    longitude;
        }
    }
}