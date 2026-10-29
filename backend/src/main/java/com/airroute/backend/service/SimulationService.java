package com.airroute.backend.service;

import com.airroute.backend.model.Aircraft;
import com.airroute.backend.model.Conflict;
import com.airroute.backend.model.FlightPhase;
import com.airroute.backend.model.PredictedConflict;
import com.airroute.backend.model.Reroute;
import com.airroute.backend.model.Route;
import com.airroute.backend.model.Waypoint;
import com.airroute.backend.model.WeatherHazard;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Service
public class SimulationService {

    private static final int AIRCRAFT_COUNT = 60;

    private static final double WEATHER_BUFFER_NM = 8.0;

    private static final double MIN_CRUISE_ALTITUDE = 26000.0;
    private static final double MAX_CRUISE_ALTITUDE = 41000.0;
    private static final double MIN_CRUISE_SPEED = 340.0;
    private static final double MAX_CRUISE_SPEED = 560.0;

    private static final int CONFLICT_RESOLUTION_COOLDOWN_TICKS = 100;
    private static final int SPEED_RESOLUTION_DURATION_TICKS = 150;

    private final WeatherService weatherService;

    private final SimpMessagingTemplate messagingTemplate;

    private final ConflictDetectionService conflictDetectionService;
    private final ConflictPredictionService conflictPredictionService;

    private final Random random;

    private final List<AircraftSimulation> simulations;

    private final List<AirportInfo> airports;

    private List<Conflict> conflicts;
    private List<PredictedConflict> predictedConflicts;
    private List<Reroute> reroutes;

    private boolean paused;

    private boolean elevatedConflictScenario;

    /*
     * Simulation speed.
     *
     * Allowed:
     * 1.0
     * 1.25
     * 1.5
     * 1.75
     * 2.0
     * 4.0
     */
    private double simulationSpeed;

    /*
     * Allows fractional speeds such as
     * 1.25x and 1.75x while keeping the
     * scheduler running every 100 ms.
     */
    private double speedAccumulator;

    private int broadcastTickCounter = 0;

    public SimulationService(
            WeatherService weatherService,
            SimpMessagingTemplate messagingTemplate,
            ConflictDetectionService conflictDetectionService,
            ConflictPredictionService conflictPredictionService) {

        this.weatherService =
                weatherService;

        this.messagingTemplate =
                messagingTemplate;

        this.conflictDetectionService =
                conflictDetectionService;

        this.conflictPredictionService =
                conflictPredictionService;

        this.random =
                new Random();

        this.simulations =
                new ArrayList<>();

        this.airports =
                createAirportCatalog();

        this.conflicts =
                new ArrayList<>();

        this.predictedConflicts =
                new ArrayList<>();

        this.reroutes =
                new ArrayList<>();

        this.paused =
                false;

        this.simulationSpeed =
                1.0;

        this.speedAccumulator =
                0.0;

        createRandomScenario();
    }

    // ------------------------------------------------
    // AIRPORTS
    // ------------------------------------------------

    private List<AirportInfo> createAirportCatalog() {

        List<AirportInfo> list =
                new ArrayList<>();

        list.add(new AirportInfo("IAD", 38.9531, -77.4565));
        list.add(new AirportInfo("DCA", 38.8512, -77.0402));
        list.add(new AirportInfo("BWI", 39.1774, -76.6684));

        list.add(new AirportInfo("JFK", 40.6413, -73.7781));
        list.add(new AirportInfo("LGA", 40.7769, -73.8740));
        list.add(new AirportInfo("EWR", 40.6895, -74.1745));

        list.add(new AirportInfo("BOS", 42.3656, -71.0096));
        list.add(new AirportInfo("PHL", 39.8744, -75.2424));

        list.add(new AirportInfo("ATL", 33.6407, -84.4277));
        list.add(new AirportInfo("CLT", 35.2144, -80.9473));
        list.add(new AirportInfo("MIA", 25.7959, -80.2870));
        list.add(new AirportInfo("MCO", 28.4312, -81.3081));

        list.add(new AirportInfo("ORD", 41.9742, -87.9073));
        list.add(new AirportInfo("DTW", 42.2162, -83.3554));
        list.add(new AirportInfo("MSP", 44.8848, -93.2223));
        list.add(new AirportInfo("STL", 38.7487, -90.3700));

        list.add(new AirportInfo("DFW", 32.8998, -97.0403));
        list.add(new AirportInfo("IAH", 29.9902, -95.3368));
        list.add(new AirportInfo("AUS", 30.1975, -97.6664));

        list.add(new AirportInfo("DEN", 39.8561, -104.6737));

        list.add(new AirportInfo("PHX", 33.4342, -112.0116));
        list.add(new AirportInfo("LAS", 36.0840, -115.1537));

        list.add(new AirportInfo("LAX", 33.9416, -118.4085));
        list.add(new AirportInfo("SAN", 32.7338, -117.1933));

        list.add(new AirportInfo("SFO", 37.6213, -122.3790));
        list.add(new AirportInfo("SEA", 47.4502, -122.3088));
        list.add(new AirportInfo("PDX", 45.5898, -122.5951));

        return list;
    }

    // ------------------------------------------------
    // SPEED
    // ------------------------------------------------

    public synchronized void setSimulationSpeed(
            double multiplier) {

        double[] allowedSpeeds = {
                1.0,
                1.25,
                1.5,
                1.75,
                2.0,
                4.0
        };

        boolean valid =
                false;

        for (double allowed
                : allowedSpeeds) {

            if (
                    Math.abs(
                            allowed - multiplier
                    ) < 0.001
            ) {

                valid =
                        true;

                break;
            }
        }

        if (!valid) {

            throw new IllegalArgumentException(
                    "Unsupported simulation speed: "
                            + multiplier
            );
        }

        simulationSpeed =
                multiplier;

        speedAccumulator =
                0.0;

        System.out.println(
                "AIRROUTE SPEED: "
                        + simulationSpeed
                        + "x"
        );
    }

    public synchronized double getSimulationSpeed() {

        return simulationSpeed;
    }

    // ------------------------------------------------
    // NEW SCENARIO
    // ------------------------------------------------

    public synchronized void newScenario() {

        simulations.clear();

        conflicts =
                new ArrayList<>();

        predictedConflicts =
                new ArrayList<>();

        reroutes =
                new ArrayList<>();

        weatherService.generateNewWeather();

        createRandomScenario();

        paused =
                false;

        speedAccumulator =
                0.0;

        broadcastCurrentState();

        System.out.println(
                "AIRROUTE: New nationwide scenario generated."
        );
    }

    // ------------------------------------------------
    // RANDOM TRAFFIC
    // ------------------------------------------------

    private void createRandomScenario() {

        simulations.clear();

        elevatedConflictScenario =
                random.nextDouble() < 0.30;

        if (elevatedConflictScenario) {

            System.out.println(
                    "AIRROUTE SCENARIO: Elevated conflict probability."
            );

        } else {

            System.out.println(
                    "AIRROUTE SCENARIO: Normal nationwide traffic."
            );
        }

        AirportInfo iad =
                findAirport("IAD");

        AirportInfo jfk =
                findAirport("JFK");

        AirportInfo lga =
                findAirport("LGA");

        AirportInfo ewr =
                findAirport("EWR");

        addFlight(
                "UAL284",
                iad,
                jfk,
                0
        );

        addFlight(
                "DAL512",
                iad,
                lga,
                1
        );

        addFlight(
                "AAL903",
                iad,
                ewr,
                -1
        );

        if (elevatedConflictScenario) {

            addFlight(
                    "JBU418",
                    iad,
                    jfk,
                    0
            );
        }

        while (
                simulations.size()
                        < AIRCRAFT_COUNT
        ) {

            AirportInfo origin =
                    randomAirport();

            AirportInfo destination =
                    randomAirport();

            if (
                    origin.code.equals(
                            destination.code
                    )
            ) {

                continue;
            }

            String callsign =
                    generateCallsign(
                            simulations.size()
                    );

            int routeVariant =
                    random.nextInt(7) - 3;

            addFlight(
                    callsign,
                    origin,
                    destination,
                    routeVariant
            );
        }

        System.out.println(
                "AIRROUTE: "
                        + simulations.size()
                        + " aircraft created."
        );
    }

    // ------------------------------------------------
    // ADD FLIGHT
    // ------------------------------------------------

    private void addFlight(
            String callsign,
            AirportInfo origin,
            AirportInfo destination,
            int routeVariant) {

        Route route =
                buildRoute(
                        origin,
                        destination,
                        routeVariant
                );

        double cruiseAltitude =
                randomAltitude(
                        28000,
                        39000
                );

        double cruiseSpeed =
                randomSpeed(
                        420,
                        520
                );

        Aircraft aircraft =
                new Aircraft(
                        callsign,
                        origin.latitude,
                        origin.longitude,
                        0,
                        0,
                        0,
                        origin.code,
                        destination.code
                );

        aircraft.setFlightPhase(
                FlightPhase.WAITING
        );

        int departureDelayTicks =
                random.nextInt(190)
                        + 10;

        AircraftSimulation simulation =
                new AircraftSimulation(
                        aircraft,
                        route,
                        destination,
                        cruiseAltitude,
                        cruiseSpeed,
                        departureDelayTicks,
                        routeVariant
                );

        simulations.add(
                simulation
        );
    }

    // ------------------------------------------------
    // SMOOTH ROUTE GENERATION
    // ------------------------------------------------

    private Route buildRoute(
            AirportInfo origin,
            AirportInfo destination,
            int routeVariant) {

        List<Waypoint> waypoints =
                new ArrayList<>();

        waypoints.add(
                new Waypoint(
                        origin.code,
                        origin.latitude,
                        origin.longitude
                )
        );

        double latitudeDifference =
                destination.latitude
                        - origin.latitude;

        double longitudeDifference =
                destination.longitude
                        - origin.longitude;

        double length =
                Math.sqrt(
                        latitudeDifference
                                * latitudeDifference
                                + longitudeDifference
                                * longitudeDifference
                );

        if (length == 0) {
            length = 1;
        }

        double perpendicularLatitude =
                -longitudeDifference
                        / length;

        double perpendicularLongitude =
                latitudeDifference
                        / length;

        double routeDistanceFactor =
                Math.min(
                        length * 0.025,
                        0.55
                );

        double offset =
                routeVariant
                        * routeDistanceFactor;

        int intermediatePoints =
                7;

        for (
                int i = 1;
                i <= intermediatePoints;
                i++
        ) {

            double fraction =
                    i
                            / (double)
                            (intermediatePoints + 1);

            double curve =
                    Math.sin(
                            Math.PI
                                    * fraction
                    );

            double secondaryCurve =
                    Math.sin(
                            Math.PI
                                    * 2
                                    * fraction
                    ) * 0.12;

            double latitude =
                    origin.latitude
                            + latitudeDifference
                            * fraction
                            + perpendicularLatitude
                            * offset
                            * (
                            curve
                                    + secondaryCurve
                    );

            double longitude =
                    origin.longitude
                            + longitudeDifference
                            * fraction
                            + perpendicularLongitude
                            * offset
                            * (
                            curve
                                    + secondaryCurve
                    );

            waypoints.add(
                    new Waypoint(
                            "R"
                                    + routeVariant
                                    + "-"
                                    + i,
                            latitude,
                            longitude
                    )
            );
        }

        waypoints.add(
                new Waypoint(
                        destination.code,
                        destination.latitude,
                        destination.longitude
                )
        );

        return new Route(
                waypoints,
                calculateRouteDistance(
                        waypoints
                )
        );
    }

    // ------------------------------------------------
    // MAIN CLOCK
    // ------------------------------------------------

    @Scheduled(fixedRate = 100)
    public synchronized void updateSimulation() {

        if (paused) {
            return;
        }

        /*
         * Example:
         *
         * 1x    = 1 simulation step per tick
         * 2x    = 2 simulation steps per tick
         * 4x    = 4 simulation steps per tick
         *
         * 1.25x / 1.5x / 1.75x use the
         * accumulator to occasionally perform
         * an extra step.
         */
        speedAccumulator +=
                simulationSpeed;

        int steps =
                (int)
                        Math.floor(
                                speedAccumulator
                        );

        speedAccumulator -=
                steps;

        for (
                int i = 0;
                i < steps;
                i++
        ) {

            runSimulationStep();
        }

        // Keep simulation updates at 250 ms,
        // but send browser updates only once per second.
        broadcastTickCounter++;

        if (broadcastTickCounter >= 25) {
            broadcastTickCounter = 0;
            broadcastCurrentState();
        }
    }

    // ------------------------------------------------
    // ONE SIMULATION STEP
    // ------------------------------------------------

    private void runSimulationStep() {

        for (AircraftSimulation simulation
                : simulations) {

            updateAircraft(
                    simulation
            );
        }

        performWeatherAvoidance();

        List<Aircraft> activeAircraft =
                getActiveAircraft();

        conflicts =
                conflictDetectionService
                        .detectConflicts(
                                activeAircraft
                        );

        predictedConflicts =
                conflictPredictionService
                        .predictConflicts(
                                activeAircraft
                        );

        performAutomaticConflictRerouting();
    }

    // ------------------------------------------------
    // WEATHER AVOIDANCE
    // ------------------------------------------------

    private void performWeatherAvoidance() {

        List<WeatherHazard> hazards =
                weatherService.getHazards();

        if (hazards.isEmpty()) {
            return;
        }

        for (AircraftSimulation simulation
                : simulations) {

            FlightPhase phase =
                    simulation.aircraft
                            .getFlightPhase();

            if (
                    phase
                            != FlightPhase.CLIMBING
                            && phase
                            != FlightPhase.EN_ROUTE
                            && phase
                            != FlightPhase.DESCENDING
            ) {

                continue;
            }

            WeatherHazard blockingHazard =
                    findBlockingWeatherAhead(
                            simulation,
                            hazards
                    );

            if (
                    blockingHazard == null
            ) {

                continue;
            }

            applyWeatherDetour(
                    simulation,
                    blockingHazard
            );
        }
    }

    private WeatherHazard findBlockingWeatherAhead(
            AircraftSimulation simulation,
            List<WeatherHazard> hazards) {

        List<Waypoint> route =
                simulation.route
                        .getWaypoints();

        if (
                simulation.segmentIndex
                        >= route.size() - 1
        ) {

            return null;
        }

        int finalSegment =
                Math.min(
                        route.size() - 2,
                        simulation.segmentIndex + 3
                );

        for (
                int i =
                simulation.segmentIndex;
                i <= finalSegment;
                i++
        ) {

            Waypoint start =
                    route.get(i);

            Waypoint end =
                    route.get(i + 1);

            if (
                    i
                            == simulation.segmentIndex
            ) {

                start =
                        new Waypoint(
                                "CURRENT",
                                simulation.aircraft
                                        .getLatitude(),
                                simulation.aircraft
                                        .getLongitude()
                        );
            }

            for (WeatherHazard hazard
                    : hazards) {

                if (
                        !hazard.isBlocked()
                ) {

                    continue;
                }

                if (
                        segmentIntersectsHazard(
                                start,
                                end,
                                hazard
                        )
                ) {

                    return hazard;
                }
            }
        }

        return null;
    }

    /*
     * Exact-enough geographic segment clearance check.
     *
     * Instead of testing only a handful of sampled points,
     * project the segment into a local nautical-mile plane
     * centered on the weather hazard and compute the closest
     * point on the whole segment.
     *
     * A segment is considered blocked whenever it comes within:
     *
     *     hazard radius + WEATHER_BUFFER_NM
     */
    private boolean segmentIntersectsHazard(
            Waypoint start,
            Waypoint end,
            WeatherHazard hazard) {

        double referenceLatitudeRadians =
                Math.toRadians(
                        hazard.getLatitude()
                );

        double longitudeScale =
                60.0
                        * Math.cos(
                        referenceLatitudeRadians
                );

        double startX =
                (
                        start.getLongitude()
                                - hazard.getLongitude()
                )
                        * longitudeScale;

        double startY =
                (
                        start.getLatitude()
                                - hazard.getLatitude()
                )
                        * 60.0;

        double endX =
                (
                        end.getLongitude()
                                - hazard.getLongitude()
                )
                        * longitudeScale;

        double endY =
                (
                        end.getLatitude()
                                - hazard.getLatitude()
                )
                        * 60.0;

        double segmentX =
                endX - startX;

        double segmentY =
                endY - startY;

        double segmentLengthSquared =
                segmentX * segmentX
                        + segmentY * segmentY;

        double closestFraction;

        if (
                segmentLengthSquared
                        <= 0.0000001
        ) {

            closestFraction =
                    0.0;

        } else {

            closestFraction =
                    -(
                            startX * segmentX
                                    + startY * segmentY
                    )
                            / segmentLengthSquared;

            closestFraction =
                    Math.max(
                            0.0,
                            Math.min(
                                    1.0,
                                    closestFraction
                            )
                    );
        }

        double closestX =
                startX
                        + segmentX
                        * closestFraction;

        double closestY =
                startY
                        + segmentY
                        * closestFraction;

        double minimumDistanceNm =
                Math.sqrt(
                        closestX * closestX
                                + closestY * closestY
                );

        double requiredClearanceNm =
                hazard.getRadiusNm()
                        + WEATHER_BUFFER_NM;

        return minimumDistanceNm
                <= requiredClearanceNm;
    }

    /*
     * Verify every segment of a candidate route against every
     * blocked weather hazard.
     */
    private boolean routeClearsBlockedWeather(
            List<Waypoint> route,
            List<WeatherHazard> hazards) {

        for (
                int i = 0;
                i < route.size() - 1;
                i++
        ) {

            Waypoint start =
                    route.get(i);

            Waypoint end =
                    route.get(i + 1);

            for (WeatherHazard hazard
                    : hazards) {

                if (!hazard.isBlocked()) {
                    continue;
                }

                if (
                        segmentIntersectsHazard(
                                start,
                                end,
                                hazard
                        )
                ) {

                    return false;
                }
            }
        }

        return true;
    }

    /*
     * Create a real geographic destination point a given
     * distance and bearing away from a latitude/longitude.
     */
    private AirportInfo destinationPoint(
            String code,
            double latitude,
            double longitude,
            double bearingDegrees,
            double distanceNm) {

        double earthRadiusNm =
                3440.065;

        double angularDistance =
                distanceNm
                        / earthRadiusNm;

        double bearing =
                Math.toRadians(
                        bearingDegrees
                );

        double latitudeOne =
                Math.toRadians(
                        latitude
                );

        double longitudeOne =
                Math.toRadians(
                        longitude
                );

        double latitudeTwo =
                Math.asin(
                        Math.sin(latitudeOne)
                                * Math.cos(angularDistance)
                                + Math.cos(latitudeOne)
                                * Math.sin(angularDistance)
                                * Math.cos(bearing)
                );

        double longitudeTwo =
                longitudeOne
                        + Math.atan2(
                        Math.sin(bearing)
                                * Math.sin(angularDistance)
                                * Math.cos(latitudeOne),
                        Math.cos(angularDistance)
                                - Math.sin(latitudeOne)
                                * Math.sin(latitudeTwo)
                );

        return new AirportInfo(
                code,
                Math.toDegrees(latitudeTwo),
                Math.toDegrees(longitudeTwo)
        );
    }

    private List<Waypoint> combineRoutes(
            Route firstHalf,
            Route secondHalf) {

        List<Waypoint> combined =
                new ArrayList<>();

        combined.addAll(
                firstHalf.getWaypoints()
        );

        for (
                int i = 1;
                i < secondHalf
                        .getWaypoints()
                        .size();
                i++
        ) {

            combined.add(
                    secondHalf
                            .getWaypoints()
                            .get(i)
            );
        }

        return combined;
    }

    private void applyWeatherDetour(
            AircraftSimulation simulation,
            WeatherHazard hazard) {

        double aircraftLatitude =
                simulation.aircraft
                        .getLatitude();

        double aircraftLongitude =
                simulation.aircraft
                        .getLongitude();

        AirportInfo current =
                new AirportInfo(
                        "CURRENT",
                        aircraftLatitude,
                        aircraftLongitude
                );

        Waypoint currentWaypoint =
                new Waypoint(
                        "CURRENT",
                        aircraftLatitude,
                        aircraftLongitude
                );

        Waypoint destinationWaypoint =
                new Waypoint(
                        simulation.destination.code,
                        simulation.destination.latitude,
                        simulation.destination.longitude
                );

        double directHeading =
                calculateHeading(
                        currentWaypoint,
                        destinationWaypoint
                );

        /*
         * Try both sides of the storm. If the first detour
         * is still too close, move the detour farther away
         * and verify the ENTIRE candidate route again.
         */
        List<WeatherHazard> allHazards =
                weatherService.getHazards();

        List<Waypoint> bestRoute =
                null;

        double bestDistance =
                Double.MAX_VALUE;

        for (
                int attempt = 0;
                attempt < 14;
                attempt++
        ) {

            double detourDistanceNm =
                    hazard.getRadiusNm()
                            + WEATHER_BUFFER_NM
                            + 10.0
                            + attempt * 10.0;

            double[] sideBearings = {
                    directHeading + 90.0,
                    directHeading - 90.0
            };

            for (double sideBearing
                    : sideBearings) {

                AirportInfo detour =
                        destinationPoint(
                                "WX-DETOUR",
                                hazard.getLatitude(),
                                hazard.getLongitude(),
                                sideBearing,
                                detourDistanceNm
                        );

                Route firstHalf =
                        buildRoute(
                                current,
                                detour,
                                0
                        );

                Route secondHalf =
                        buildRoute(
                                detour,
                                simulation.destination,
                                simulation.routeVariant
                        );

                List<Waypoint> candidate =
                        combineRoutes(
                                firstHalf,
                                secondHalf
                        );

                if (
                        !routeClearsBlockedWeather(
                                candidate,
                                allHazards
                        )
                ) {

                    continue;
                }

                double candidateDistance =
                        calculateRouteDistance(
                                candidate
                        );

                if (
                        candidateDistance
                                < bestDistance
                ) {

                    bestDistance =
                            candidateDistance;

                    bestRoute =
                            candidate;
                }
            }

            /*
             * Once either side produces a safe route at this
             * clearance, use the shorter safe option instead
             * of unnecessarily pushing farther out.
             */
            if (bestRoute != null) {
                break;
            }
        }

        if (bestRoute == null) {

            System.out.println(
                    "AIRROUTE WEATHER WARNING: Unable to build a fully clear detour for "
                            + simulation.aircraft.getCallsign()
            );

            return;
        }

        simulation.route =
                new Route(
                        bestRoute,
                        calculateRouteDistance(
                                bestRoute
                        )
                );

        simulation.segmentIndex =
                0;

        simulation.segmentProgress =
                0.0;

        addRerouteEvent(
                new Reroute(
                        simulation.aircraft
                                .getCallsign(),
                        "Severe weather avoidance ("
                                + Math.round(
                                hazard.getRadiusNm()
                        )
                                + " NM radius + "
                                + Math.round(
                                WEATHER_BUFFER_NM
                        )
                                + " NM buffer)",
                        bestRoute
                )
        );

        System.out.println(
                "AIRROUTE WEATHER AVOIDANCE: "
                        + simulation.aircraft.getCallsign()
                        + " rerouted around "
                        + Math.round(
                        hazard.getRadiusNm()
                )
                        + " NM storm with "
                        + Math.round(
                        WEATHER_BUFFER_NM
                )
                        + " NM safety buffer."
        );
    }

    // ------------------------------------------------
    // FLIGHT PHASES
    // ------------------------------------------------

    private void updateAircraft(
            AircraftSimulation simulation) {

        updateConflictResolutionState(
                simulation
        );

        switch (
                simulation.aircraft
                        .getFlightPhase()
        ) {

            case WAITING ->
                    updateWaiting(
                            simulation
                    );

            case CLIMBING ->
                    updateClimbing(
                            simulation
                    );

            case EN_ROUTE ->
                    updateEnRoute(
                            simulation
                    );

            case DESCENDING ->
                    updateDescending(
                            simulation
                    );

            case ARRIVED ->
                    updateArrived(
                            simulation
                    );
        }
    }

    private void updateWaiting(
            AircraftSimulation simulation) {

        simulation.aircraft.setSpeed(
                0
        );

        simulation.aircraft.setAltitude(
                0
        );

        simulation.departureDelayTicks--;

        if (
                simulation.departureDelayTicks
                        <= 0
        ) {

            simulation.aircraft
                    .setFlightPhase(
                            FlightPhase.CLIMBING
                    );

            simulation.aircraft
                    .setSpeed(
                            simulation.cruiseSpeed
                                    * 0.75
                    );
        }
    }

    private void updateClimbing(
            AircraftSimulation simulation) {

        double altitude =
                simulation.aircraft
                        .getAltitude();

        altitude +=
                350;

        if (
                altitude
                        >= simulation.cruiseAltitude
        ) {

            altitude =
                    simulation.cruiseAltitude;

            simulation.aircraft
                    .setFlightPhase(
                            FlightPhase.EN_ROUTE
                    );

            simulation.aircraft
                    .setSpeed(
                            simulation.cruiseSpeed
                    );
        }

        simulation.aircraft.setAltitude(
                altitude
        );

        moveAircraft(
                simulation,
                0.70
        );
    }

    private void updateEnRoute(
            AircraftSimulation simulation) {

        simulation.aircraft.setSpeed(
                simulation.cruiseSpeed
        );

        if (
                simulation.segmentIndex
                        >= simulation.route
                        .getWaypoints()
                        .size()
                        - 2
        ) {

            simulation.aircraft
                    .setFlightPhase(
                            FlightPhase.DESCENDING
                    );

            return;
        }

        moveAircraft(
                simulation,
                1.0
        );
    }

    private void updateDescending(
            AircraftSimulation simulation) {

        double altitude =
                simulation.aircraft
                        .getAltitude();

        altitude -=
                450;

        if (altitude < 0) {

            altitude = 0;
        }

        simulation.aircraft.setAltitude(
                altitude
        );

        simulation.aircraft.setSpeed(
                Math.max(
                        180,
                        simulation.cruiseSpeed
                                * 0.65
                )
        );

        moveAircraft(
                simulation,
                0.75
        );
    }

    private void updateArrived(
            AircraftSimulation simulation) {

        simulation.aircraft.setSpeed(
                0
        );

        simulation.aircraft.setAltitude(
                0
        );

        simulation.arrivalWaitTicks--;

        if (
                simulation.arrivalWaitTicks
                        <= 0
        ) {

            restartFlight(
                    simulation
            );
        }
    }

    // ------------------------------------------------
    // MOVEMENT
    // ------------------------------------------------

    private void moveAircraft(
            AircraftSimulation simulation,
            double phaseSpeedFactor) {

        List<Waypoint> waypoints =
                simulation.route
                        .getWaypoints();

        if (
                simulation.segmentIndex
                        >= waypoints.size() - 1
        ) {

            finishFlight(
                    simulation
            );

            return;
        }

        Waypoint start =
                waypoints.get(
                        simulation.segmentIndex
                );

        Waypoint end =
                waypoints.get(
                        simulation.segmentIndex + 1
                );

        double progressIncrement =
                0.0045
                        * (
                        simulation.cruiseSpeed
                                / 450.0
                )
                        * phaseSpeedFactor;

        simulation.segmentProgress +=
                progressIncrement;

        if (
                simulation.segmentProgress
                        > 1.0
        ) {

            simulation.segmentProgress =
                    1.0;
        }

        double latitude =
                start.getLatitude()
                        + (
                        end.getLatitude()
                                - start.getLatitude()
                )
                        * simulation.segmentProgress;

        double longitude =
                start.getLongitude()
                        + (
                        end.getLongitude()
                                - start.getLongitude()
                )
                        * simulation.segmentProgress;

        simulation.aircraft.setLatitude(
                latitude
        );

        simulation.aircraft.setLongitude(
                longitude
        );

        simulation.aircraft.setHeading(
                calculateHeading(
                        start,
                        end
                )
        );

        if (
                simulation.segmentProgress
                        >= 1.0
        ) {

            simulation.segmentProgress =
                    0.0;

            simulation.segmentIndex++;

            if (
                    simulation.segmentIndex
                            >= waypoints.size() - 1
            ) {

                finishFlight(
                        simulation
                );
            }
        }
    }

    // ------------------------------------------------
    // FINISH / RESTART
    // ------------------------------------------------

    private void finishFlight(
            AircraftSimulation simulation) {

        simulation.aircraft
                .setFlightPhase(
                        FlightPhase.ARRIVED
                );

        simulation.aircraft
                .setLatitude(
                        simulation.destination.latitude
                );

        simulation.aircraft
                .setLongitude(
                        simulation.destination.longitude
                );

        simulation.aircraft
                .setAltitude(
                        0
                );

        simulation.aircraft
                .setSpeed(
                        0
                );

        simulation.arrivalWaitTicks =
                random.nextInt(100)
                        + 50;
    }

    private void restartFlight(
            AircraftSimulation simulation) {

        AirportInfo newOrigin =
                simulation.destination;

        AirportInfo newDestination =
                randomAirport();

        while (
                newDestination.code.equals(
                        newOrigin.code
                )
        ) {

            newDestination =
                    randomAirport();
        }

        int routeVariant =
                random.nextInt(7)
                        - 3;

        simulation.destination =
                newDestination;

        simulation.route =
                buildRoute(
                        newOrigin,
                        newDestination,
                        routeVariant
                );

        simulation.segmentIndex =
                0;

        simulation.segmentProgress =
                0.0;

        simulation.routeVariant =
                routeVariant;

        simulation.cruiseAltitude =
                randomAltitude(
                        28000,
                        39000
                );

        simulation.cruiseSpeed =
                randomSpeed(
                        420,
                        520
                );

        simulation.preferredCruiseSpeed =
                simulation.cruiseSpeed;

        simulation.conflictResolutionCooldownTicks =
                0;

        simulation.speedResolutionTicks =
                0;

        simulation.departureDelayTicks =
                random.nextInt(150)
                        + 20;


        simulation.aircraft =
                new Aircraft(
                        simulation.aircraft
                                .getCallsign(),
                        newOrigin.latitude,
                        newOrigin.longitude,
                        0,
                        0,
                        0,
                        newOrigin.code,
                        newDestination.code
                );

        simulation.aircraft
                .setFlightPhase(
                        FlightPhase.WAITING
                );
    }

    // ------------------------------------------------
    // SMART CONFLICT RESOLUTION
    // ------------------------------------------------

    private void performAutomaticConflictRerouting() {

        for (PredictedConflict conflict
                : predictedConflicts) {

            AircraftSimulation first =
                    findSimulation(
                            conflict.getAircraftOne()
                    );

            AircraftSimulation second =
                    findSimulation(
                            conflict.getAircraftTwo()
                    );

            AircraftSimulation selected =
                    chooseResolutionAircraft(
                            first,
                            second
                    );

            AircraftSimulation other =
                    selected == first
                            ? second
                            : first;

            if (
                    selected == null
                            || other == null
            ) {
                continue;
            }

            resolvePredictedConflict(
                    selected,
                    other,
                    conflict.getSecondsAhead()
            );
        }
    }

    private AircraftSimulation chooseResolutionAircraft(
            AircraftSimulation first,
            AircraftSimulation second) {

        if (isAvailableForConflictResolution(second)) {
            return second;
        }

        if (isAvailableForConflictResolution(first)) {
            return first;
        }

        return null;
    }

    private boolean isAvailableForConflictResolution(
            AircraftSimulation simulation) {

        return simulation != null
                && simulation.aircraft.getFlightPhase()
                == FlightPhase.EN_ROUTE
                && simulation.conflictResolutionCooldownTicks <= 0;
    }

    private void resolvePredictedConflict(
            AircraftSimulation selected,
            AircraftSimulation other,
            int secondsAhead) {

        ConflictResolutionOption bestOption =
                findBestConflictResolution(
                        selected,
                        other,
                        Math.max(secondsAhead, 30)
                );

        if (bestOption == null) {
            applyConflictReroute(
                    selected
            );

            selected.conflictResolutionCooldownTicks =
                    CONFLICT_RESOLUTION_COOLDOWN_TICKS;

            return;
        }

        switch (bestOption.type) {

            case SPEED ->
                    applySpeedResolution(
                            selected,
                            bestOption.value
                    );

            case ALTITUDE ->
                    applyAltitudeResolution(
                            selected,
                            bestOption.value
                    );

            case LATERAL ->
                    applyConflictReroute(
                            selected
                    );
        }

        selected.conflictResolutionCooldownTicks =
                CONFLICT_RESOLUTION_COOLDOWN_TICKS;
    }

    private ConflictResolutionOption findBestConflictResolution(
            AircraftSimulation selected,
            AircraftSimulation other,
            int secondsAhead) {

        List<ConflictResolutionOption> options =
                new ArrayList<>();

        double preferredSpeed =
                selected.preferredCruiseSpeed;

        double[] speedCandidates = {
                preferredSpeed - 35.0,
                preferredSpeed + 35.0,
                preferredSpeed - 60.0,
                preferredSpeed + 60.0
        };

        for (double candidateSpeed
                : speedCandidates) {

            if (
                    candidateSpeed < MIN_CRUISE_SPEED
                            || candidateSpeed > MAX_CRUISE_SPEED
            ) {
                continue;
            }

            if (
                    isSpeedResolutionSafe(
                            selected,
                            candidateSpeed,
                            secondsAhead
                    )
            ) {

                double score =
                        1.0
                                + Math.abs(
                                candidateSpeed
                                        - preferredSpeed
                        ) / 100.0;

                options.add(
                        new ConflictResolutionOption(
                                ResolutionType.SPEED,
                                candidateSpeed,
                                score
                        )
                );
            }
        }

        double currentAltitude =
                selected.aircraft.getAltitude();

        double[] altitudeCandidates = {
                currentAltitude + 2000.0,
                currentAltitude - 2000.0,
                currentAltitude + 4000.0,
                currentAltitude - 4000.0
        };

        for (double candidateAltitude
                : altitudeCandidates) {

            if (
                    candidateAltitude < MIN_CRUISE_ALTITUDE
                            || candidateAltitude > MAX_CRUISE_ALTITUDE
            ) {
                continue;
            }

            if (
                    isAltitudeResolutionSafe(
                            selected,
                            candidateAltitude,
                            secondsAhead
                    )
            ) {

                double score =
                        2.0
                                + Math.abs(
                                candidateAltitude
                                        - currentAltitude
                        ) / 4000.0;

                options.add(
                        new ConflictResolutionOption(
                                ResolutionType.ALTITUDE,
                                candidateAltitude,
                                score
                        )
                );
            }
        }

        if (options.isEmpty()) {
            return null;
        }

        ConflictResolutionOption best =
                options.get(0);

        for (ConflictResolutionOption option
                : options) {

            if (option.score < best.score) {
                best = option;
            }
        }

        return best;
    }

    private boolean isSpeedResolutionSafe(
            AircraftSimulation selected,
            double candidateSpeed,
            int secondsAhead) {

        PredictedPosition selectedPosition =
                predictPosition(
                        selected.aircraft,
                        candidateSpeed,
                        secondsAhead
                );

        for (AircraftSimulation simulation
                : simulations) {

            if (simulation == selected) {
                continue;
            }

            FlightPhase phase =
                    simulation.aircraft.getFlightPhase();

            if (
                    phase != FlightPhase.CLIMBING
                            && phase != FlightPhase.EN_ROUTE
                            && phase != FlightPhase.DESCENDING
            ) {
                continue;
            }

            PredictedPosition otherPosition =
                    predictPosition(
                            simulation.aircraft,
                            simulation.aircraft.getSpeed(),
                            secondsAhead
                    );

            double horizontalDistance =
                    calculateDistanceNm(
                            selectedPosition.latitude,
                            selectedPosition.longitude,
                            otherPosition.latitude,
                            otherPosition.longitude
                    );

            double verticalDistance =
                    Math.abs(
                            selected.aircraft.getAltitude()
                                    - simulation.aircraft.getAltitude()
                    );

            if (
                    horizontalDistance <= 5.0
                            && verticalDistance <= 1000.0
            ) {
                return false;
            }
        }

        return true;
    }

    private boolean isAltitudeResolutionSafe(
            AircraftSimulation selected,
            double candidateAltitude,
            int secondsAhead) {

        PredictedPosition selectedPosition =
                predictPosition(
                        selected.aircraft,
                        selected.aircraft.getSpeed(),
                        secondsAhead
                );

        for (AircraftSimulation simulation
                : simulations) {

            if (simulation == selected) {
                continue;
            }

            FlightPhase phase =
                    simulation.aircraft.getFlightPhase();

            if (
                    phase != FlightPhase.CLIMBING
                            && phase != FlightPhase.EN_ROUTE
                            && phase != FlightPhase.DESCENDING
            ) {
                continue;
            }

            PredictedPosition otherPosition =
                    predictPosition(
                            simulation.aircraft,
                            simulation.aircraft.getSpeed(),
                            secondsAhead
                    );

            double horizontalDistance =
                    calculateDistanceNm(
                            selectedPosition.latitude,
                            selectedPosition.longitude,
                            otherPosition.latitude,
                            otherPosition.longitude
                    );

            double verticalDistance =
                    Math.abs(
                            candidateAltitude
                                    - simulation.aircraft.getAltitude()
                    );

            if (
                    horizontalDistance <= 5.0
                            && verticalDistance <= 1000.0
            ) {
                return false;
            }
        }

        return true;
    }

    private PredictedPosition predictPosition(
            Aircraft aircraft,
            double speedKnots,
            int secondsAhead) {

        double distanceNm =
                speedKnots
                        * secondsAhead
                        / 3600.0;

        double angularDistance =
                distanceNm / 3440.065;

        double bearing =
                Math.toRadians(
                        aircraft.getHeading()
                );

        double latitudeOne =
                Math.toRadians(
                        aircraft.getLatitude()
                );

        double longitudeOne =
                Math.toRadians(
                        aircraft.getLongitude()
                );

        double latitudeTwo =
                Math.asin(
                        Math.sin(latitudeOne)
                                * Math.cos(angularDistance)
                                + Math.cos(latitudeOne)
                                * Math.sin(angularDistance)
                                * Math.cos(bearing)
                );

        double longitudeTwo =
                longitudeOne
                        + Math.atan2(
                        Math.sin(bearing)
                                * Math.sin(angularDistance)
                                * Math.cos(latitudeOne),
                        Math.cos(angularDistance)
                                - Math.sin(latitudeOne)
                                * Math.sin(latitudeTwo)
                );

        return new PredictedPosition(
                Math.toDegrees(latitudeTwo),
                Math.toDegrees(longitudeTwo)
        );
    }

    private void applySpeedResolution(
            AircraftSimulation simulation,
            double newSpeed) {

        double oldSpeed =
                simulation.cruiseSpeed;

        simulation.cruiseSpeed =
                newSpeed;

        simulation.aircraft.setSpeed(
                newSpeed
        );

        simulation.speedResolutionTicks =
                SPEED_RESOLUTION_DURATION_TICKS;

        String direction =
                newSpeed < oldSpeed
                        ? "reduced"
                        : "increased";

        int amount =
                (int) Math.round(
                        Math.abs(newSpeed - oldSpeed)
                );

        addRerouteEvent(
                new Reroute(
                        simulation.aircraft.getCallsign(),
                        "Conflict resolution: "
                                + direction
                                + " speed by "
                                + amount
                                + " kt",
                        simulation.route.getWaypoints()
                )
        );

        System.out.println(
                "AIRROUTE CONFLICT RESOLUTION: "
                        + simulation.aircraft.getCallsign()
                        + " "
                        + direction
                        + " speed by "
                        + amount
                        + " kt"
        );
    }

    private void applyAltitudeResolution(
            AircraftSimulation simulation,
            double newAltitude) {

        double oldAltitude =
                simulation.aircraft.getAltitude();

        simulation.cruiseAltitude =
                newAltitude;

        simulation.aircraft.setAltitude(
                newAltitude
        );

        String direction =
                newAltitude > oldAltitude
                        ? "climbed"
                        : "descended";

        int amount =
                (int) Math.round(
                        Math.abs(newAltitude - oldAltitude)
                );

        addRerouteEvent(
                new Reroute(
                        simulation.aircraft.getCallsign(),
                        "Conflict resolution: "
                                + direction
                                + " "
                                + amount
                                + " ft",
                        simulation.route.getWaypoints()
                )
        );

        System.out.println(
                "AIRROUTE CONFLICT RESOLUTION: "
                        + simulation.aircraft.getCallsign()
                        + " "
                        + direction
                        + " "
                        + amount
                        + " ft"
        );
    }

    private void updateConflictResolutionState(
            AircraftSimulation simulation) {

        if (simulation.conflictResolutionCooldownTicks > 0) {
            simulation.conflictResolutionCooldownTicks--;
        }

        if (simulation.speedResolutionTicks > 0) {

            simulation.speedResolutionTicks--;

            if (simulation.speedResolutionTicks == 0) {

                simulation.cruiseSpeed =
                        simulation.preferredCruiseSpeed;

                if (
                        simulation.aircraft.getFlightPhase()
                                == FlightPhase.EN_ROUTE
                ) {

                    simulation.aircraft.setSpeed(
                            simulation.preferredCruiseSpeed
                    );
                }
            }
        }
    }

    private void applyConflictReroute(
            AircraftSimulation simulation) {

        AirportInfo current =
                new AirportInfo(
                        "CURRENT",
                        simulation.aircraft
                                .getLatitude(),
                        simulation.aircraft
                                .getLongitude()
                );

        int newVariant =
                simulation.routeVariant >= 0
                        ? -2
                        : 2;

        Route alternate =
                buildRoute(
                        current,
                        simulation.destination,
                        newVariant
                );

        simulation.route =
                alternate;

        simulation.segmentIndex =
                0;

        simulation.segmentProgress =
                0.0;

        simulation.routeVariant =
                newVariant;


        addRerouteEvent(
                new Reroute(
                        simulation.aircraft
                                .getCallsign(),
                        "Conflict resolution: lateral reroute",
                        alternate.getWaypoints()
                )
        );

        System.out.println(
                "AIRROUTE CONFLICT RESOLUTION: "
                        + simulation.aircraft.getCallsign()
                        + " lateral reroute"
        );
    }

    // ------------------------------------------------
    // RESET / START / PAUSE
    // ------------------------------------------------

    public synchronized void resetSimulation() {

        createRandomScenario();

        conflicts =
                new ArrayList<>();

        predictedConflicts =
                new ArrayList<>();

        reroutes =
                new ArrayList<>();

        paused =
                false;

        speedAccumulator =
                0.0;

        broadcastCurrentState();

        System.out.println(
                "AIRROUTE: Traffic reset."
        );
    }

    public synchronized void startSimulation() {

        paused =
                false;
    }

    public synchronized void pauseSimulation() {

        paused =
                true;
    }

    public synchronized boolean isPaused() {

        return paused;
    }

    // ------------------------------------------------
    // WEBSOCKET
    // ------------------------------------------------

    private void broadcastCurrentState() {

        messagingTemplate.convertAndSend(
                "/topic/aircraft",
                getAircraft()
        );

        messagingTemplate.convertAndSend(
                "/topic/conflicts",
                conflicts
        );

        messagingTemplate.convertAndSend(
                "/topic/predicted-conflicts",
                predictedConflicts
        );

        messagingTemplate.convertAndSend(
                "/topic/reroutes",
                reroutes
        );
    }

    // ------------------------------------------------
    // HELPERS
    // ------------------------------------------------

    private AircraftSimulation findSimulation(
            String callsign) {

        for (AircraftSimulation simulation
                : simulations) {

            if (
                    simulation.aircraft
                            .getCallsign()
                            .equals(callsign)
            ) {

                return simulation;
            }
        }

        return null;
    }

    private AirportInfo findAirport(
            String code) {

        for (AirportInfo airport
                : airports) {

            if (
                    airport.code.equals(
                            code
                    )
            ) {

                return airport;
            }
        }

        throw new IllegalArgumentException(
                "Unknown airport: "
                        + code
        );
    }

    private AirportInfo randomAirport() {

        return airports.get(
                random.nextInt(
                        airports.size()
                )
        );
    }

    private String generateCallsign(
            int number) {

        String[] airlines = {
                "UAL",
                "DAL",
                "AAL",
                "SWA",
                "JBU",
                "FFT",
                "ASA"
        };

        String airline =
                airlines[
                        number
                                % airlines.length
                        ];

        int flightNumber =
                100
                        + random.nextInt(
                        8900
                );

        return airline
                + flightNumber;
    }

    private double randomAltitude(
            int minimum,
            int maximum) {

        int steps =
                (maximum - minimum)
                        / 500;

        return minimum
                + random.nextInt(
                        steps + 1
                ) * 500;
    }

    private double randomSpeed(
            int minimum,
            int maximum) {

        return minimum
                + random.nextInt(
                        maximum
                                - minimum
                                + 1
                );
    }

    // ------------------------------------------------
    // DISTANCE / HEADING
    // ------------------------------------------------

    private double calculateRouteDistance(
            List<Waypoint> route) {

        double total =
                0;

        for (
                int i = 0;
                i < route.size() - 1;
                i++
        ) {

            Waypoint first =
                    route.get(i);

            Waypoint second =
                    route.get(i + 1);

            total +=
                    calculateDistanceNm(
                            first.getLatitude(),
                            first.getLongitude(),
                            second.getLatitude(),
                            second.getLongitude()
                    );
        }

        return total;
    }

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

    private double calculateHeading(
            Waypoint start,
            Waypoint end) {

        double latitudeOne =
                Math.toRadians(
                        start.getLatitude()
                );

        double latitudeTwo =
                Math.toRadians(
                        end.getLatitude()
                );

        double longitudeDifference =
                Math.toRadians(
                        end.getLongitude()
                                - start.getLongitude()
                );

        double y =
                Math.sin(
                        longitudeDifference
                )
                        * Math.cos(
                        latitudeTwo
                );

        double x =
                Math.cos(
                        latitudeOne
                )
                        * Math.sin(
                        latitudeTwo
                )
                        - Math.sin(
                        latitudeOne
                )
                        * Math.cos(
                        latitudeTwo
                )
                        * Math.cos(
                        longitudeDifference
                );

        double heading =
                Math.toDegrees(
                        Math.atan2(
                                y,
                                x
                        )
                );

        return (
                heading + 360
        ) % 360;
    }

    // ------------------------------------------------
    // AIRCRAFT DATA
    // ------------------------------------------------

    private List<Aircraft> getActiveAircraft() {

        List<Aircraft> active =
                new ArrayList<>();

        for (AircraftSimulation simulation
                : simulations) {

            FlightPhase phase =
                    simulation.aircraft
                            .getFlightPhase();

            if (
                    phase
                            == FlightPhase.CLIMBING
                            || phase
                            == FlightPhase.EN_ROUTE
                            || phase
                            == FlightPhase.DESCENDING
            ) {

                active.add(
                        simulation.aircraft
                );
            }
        }

        return active;
    }

    public synchronized List<Aircraft> getAircraft() {

        List<Aircraft> aircraft =
                new ArrayList<>();

        for (AircraftSimulation simulation
                : simulations) {

            aircraft.add(
                    simulation.aircraft
            );
        }

        return aircraft;
    }

    public List<Conflict> getConflicts() {

        return conflicts;
    }

    public List<PredictedConflict> getPredictedConflicts() {

        return predictedConflicts;
    }

    public List<Reroute> getReroutes() {

        return reroutes;
    }

    public String getScenarioType() {

        return elevatedConflictScenario
                ? "ELEVATED_CONFLICT"
                : "NORMAL";
    }

    private void addRerouteEvent(
            Reroute reroute) {

        reroutes.add(
                reroute
        );

        if (
                reroutes.size()
                        > 20
        ) {

            reroutes.remove(
                    0
            );
        }
    }

    // ------------------------------------------------
    // INTERNAL AIRPORT
    // ------------------------------------------------

    private static class AirportInfo {

        private final String code;

        private final double latitude;

        private final double longitude;

        public AirportInfo(
                String code,
                double latitude,
                double longitude) {

            this.code =
                    code;

            this.latitude =
                    latitude;

            this.longitude =
                    longitude;
        }
    }

    // ------------------------------------------------
    // CONFLICT RESOLUTION HELPERS
    // ------------------------------------------------

    private enum ResolutionType {
        SPEED,
        ALTITUDE,
        LATERAL
    }

    private static class ConflictResolutionOption {

        private final ResolutionType type;
        private final double value;
        private final double score;

        public ConflictResolutionOption(
                ResolutionType type,
                double value,
                double score) {

            this.type = type;
            this.value = value;
            this.score = score;
        }
    }

    private static class PredictedPosition {

        private final double latitude;
        private final double longitude;

        public PredictedPosition(
                double latitude,
                double longitude) {

            this.latitude = latitude;
            this.longitude = longitude;
        }
    }

    // ------------------------------------------------
    // INTERNAL FLIGHT STATE
    // ------------------------------------------------

    private static class AircraftSimulation {

        private Aircraft aircraft;

        private Route route;

        private AirportInfo destination;

        private int segmentIndex;

        private double segmentProgress;

        private double cruiseAltitude;

        private double cruiseSpeed;

        private double preferredCruiseSpeed;

        private int conflictResolutionCooldownTicks;

        private int speedResolutionTicks;

        private int departureDelayTicks;

        private int arrivalWaitTicks;

        private int routeVariant;


        public AircraftSimulation(
                Aircraft aircraft,
                Route route,
                AirportInfo destination,
                double cruiseAltitude,
                double cruiseSpeed,
                int departureDelayTicks,
                int routeVariant) {

            this.aircraft =
                    aircraft;

            this.route =
                    route;

            this.destination =
                    destination;

            this.segmentIndex =
                    0;

            this.segmentProgress =
                    0.0;

            this.cruiseAltitude =
                    cruiseAltitude;

            this.cruiseSpeed =
                    cruiseSpeed;

            this.preferredCruiseSpeed =
                    cruiseSpeed;

            this.conflictResolutionCooldownTicks =
                    0;

            this.speedResolutionTicks =
                    0;

            this.departureDelayTicks =
                    departureDelayTicks;

            this.arrivalWaitTicks =
                    0;

            this.routeVariant =
                    routeVariant;


        }
    }
}