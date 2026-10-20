package com.airroute.backend.routing;

import com.airroute.backend.model.Route;
import com.airroute.backend.model.Waypoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class DijkstraRouterTest {

    @Test
    void findsShortestRoute() {
        AirspaceGraph graph = new AirspaceGraph();

        Waypoint iad = new Waypoint("IAD", 38.9531, -77.4565);
        Waypoint wp1 = new Waypoint("WP1", 39.2000, -77.0000);
        Waypoint jfk = new Waypoint("JFK", 40.6413, -73.7781);

        graph.addWaypoint(iad);
        graph.addWaypoint(wp1);
        graph.addWaypoint(jfk);

        graph.addConnection("IAD", "WP1", 100);
        graph.addConnection("WP1", "JFK", 80);
        graph.addConnection("IAD", "JFK", 250);

        DijkstraRouter router = new DijkstraRouter(graph);

        Route route = router.findShortestRoute("IAD", "JFK");

        assertNotNull(route);

        assertEquals(3, route.getWaypoints().size());

        assertEquals("IAD", route.getWaypoints().get(0).getId());
        assertEquals("WP1", route.getWaypoints().get(1).getId());
        assertEquals("JFK", route.getWaypoints().get(2).getId());

        assertEquals(180.0, route.getTotalDistance());
    }
}