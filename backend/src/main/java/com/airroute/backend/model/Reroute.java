package com.airroute.backend.model;

import java.util.List;

public class Reroute {

    private final String callsign;
    private final String reason;
    private final List<Waypoint> newRoute;

    public Reroute(
            String callsign,
            String reason,
            List<Waypoint> newRoute) {

        this.callsign = callsign;
        this.reason = reason;
        this.newRoute = newRoute;
    }

    public String getCallsign() {
        return callsign;
    }

    public String getReason() {
        return reason;
    }

    public List<Waypoint> getNewRoute() {
        return newRoute;
    }
}