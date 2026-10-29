import {
  useEffect,
  useRef,
  useState,
} from "react";

import * as maplibregl from "maplibre-gl";

import "maplibre-gl/dist/maplibre-gl.css";

import AircraftMarker from "./AircraftMarker";
import SimulationDashboard from "./SimulationDashboard";
import { API_BASE_URL } from "../config";
import { SharedSimulationClient } from "../services/sharedSimulationClient";

type WeatherHazard = {
  waypointId: string;
  type: string;
  severity: string;
  blocked: boolean;
  latitude: number;
  longitude: number;
  radiusNm: number;
};

type SimulationStatus = {
  paused: boolean;
  scenarioType: string;
  speedMultiplier: number;
};

function createWeatherCircle(
  latitude: number,
  longitude: number,
  radiusNm: number
) {

  const pointCount =
    64;

  const coordinates:
    [number, number][] =
    [];

  const earthRadiusNm =
    3440.065;

  const angularDistance =
    radiusNm
    / earthRadiusNm;

  const latitudeRadians =
    latitude
    * (
      Math.PI
      / 180
    );

  const longitudeRadians =
    longitude
    * (
      Math.PI
      / 180
    );

  for (
    let i = 0;
    i <= pointCount;
    i++
  ) {

    const bearing =
      (
        i
        / pointCount
      )
      * 2
      * Math.PI;

    const circleLatitude =
      Math.asin(
        Math.sin(
          latitudeRadians
        )
        * Math.cos(
          angularDistance
        )
        +
        Math.cos(
          latitudeRadians
        )
        * Math.sin(
          angularDistance
        )
        * Math.cos(
          bearing
        )
      );

    const circleLongitude =
      longitudeRadians
      +
      Math.atan2(
        Math.sin(
          bearing
        )
        * Math.sin(
          angularDistance
        )
        * Math.cos(
          latitudeRadians
        ),

        Math.cos(
          angularDistance
        )
        -
        Math.sin(
          latitudeRadians
        )
        * Math.sin(
          circleLatitude
        )
      );

    coordinates.push([
      circleLongitude
        * (
          180
          / Math.PI
        ),

      circleLatitude
        * (
          180
          / Math.PI
        ),
    ]);
  }

  return {

    type:
      "Feature" as const,

    properties:
      {},

    geometry: {

      type:
        "Polygon" as const,

      coordinates: [
        coordinates,
      ],
    },
  };
}

export default function AirRouteMap() {

  const mapContainer =
    useRef<HTMLDivElement | null>(
      null
    );

  const mapRef =
    useRef<maplibregl.Map | null>(
      null
    );

  const [
    mapInstance,
    setMapInstance,
  ] =
    useState<
      maplibregl.Map | null
    >(null);

  const [
    weather,
    setWeather,
  ] =
    useState<
      WeatherHazard[]
    >([]);

  const [
    status,
    setStatus,
  ] =
    useState<
      SimulationStatus
    >({

      paused:
        false,

      scenarioType:
        "NORMAL",

      speedMultiplier:
        1,
    });

  const [
    scenarioVersion,
    setScenarioVersion,
  ] =
    useState(
      0
    );

  const [
    showAllPaths,
    setShowAllPaths,
  ] =
    useState(
      false
    );

  const speedOptions =
    [
      1,
      1.25,
      1.5,
      1.75,
      2,
      4,
    ];

  // ------------------------------------------------
  // LOAD WEATHER
  // ------------------------------------------------

  const loadWeather =
    async () => {

      try {

        const response =
          await fetch(
            `${API_BASE_URL}/api/weather`
          );

        const data =
          await response.json();

        setWeather(
          data
        );

      } catch (error) {

        console.error(
          "Failed to load weather:",
          error
        );
      }
    };

  // ------------------------------------------------
  // LOAD STATUS
  // ------------------------------------------------

  const loadStatus =
    async () => {

      try {

        const response =
          await fetch(
            `${API_BASE_URL}/api/simulation/status`
          );

        const data =
          await response.json();

        setStatus(
          data
        );

      } catch (error) {

        console.error(
          "Failed to load simulation status:",
          error
        );
      }
    };

  useEffect(() => {

    loadWeather();

    loadStatus();

    const statusClient = new SharedSimulationClient({
      onConnect: () => {
        statusClient.subscribe("/topic/simulation-status", (message) => {
          setStatus(JSON.parse(message.body) as SimulationStatus);
        });
      },
    });
    statusClient.activate();

    return () => statusClient.deactivate();
  }, []);

  // ------------------------------------------------
  // SIMULATION COMMANDS
  // ------------------------------------------------

  const sendSimulationCommand =
    async (
      command:
        string
    ) => {

      try {

        const response =
          await fetch(
            `${API_BASE_URL}/api/simulation/${command}`,
            {

              method:
                "POST",
            }
          );

        const data:
          SimulationStatus =
          await response.json();

        setStatus(
          data
        );

        if (
          command
            === "new-scenario"
        ) {

          await loadWeather();

          setShowAllPaths(
            false
          );

          setScenarioVersion(
            (
              previous
            ) =>
              previous + 1
          );
        }

        if (
          command
            === "reset"
        ) {

          setShowAllPaths(
            false
          );

          setScenarioVersion(
            (
              previous
            ) =>
              previous + 1
          );
        }

      } catch (error) {

        console.error(
          "Simulation command failed:",
          error
        );
      }
    };

  // ------------------------------------------------
  // SPEED COMMAND
  // ------------------------------------------------

  const setSimulationSpeed =
    async (
      multiplier:
        number
    ) => {

      try {

        const response =
          await fetch(
            `${API_BASE_URL}/api/simulation/speed?multiplier=${multiplier}`,
            {

              method:
                "POST",
            }
          );

        const data:
          SimulationStatus =
          await response.json();

        setStatus(
          data
        );

      } catch (error) {

        console.error(
          "Failed to change simulation speed:",
          error
        );
      }
    };

  // ------------------------------------------------
  // CREATE MAP
  // ------------------------------------------------

  useEffect(() => {

    if (
      !mapContainer.current
      ||
      mapRef.current
    ) {

      return;
    }

    const map =
      new maplibregl.Map({

        container:
          mapContainer.current,

        style: {

          version:
            8,

          sources: {

            osm: {

              type:
                "raster",

              tiles: [
                "https://tile.openstreetmap.org/{z}/{x}/{y}.png",
              ],

              tileSize:
                256,

              attribution:
                "&copy; OpenStreetMap contributors",
            },
          },

          layers: [

            {

              id:
                "osm",

              type:
                "raster",

              source:
                "osm",
            },
          ],
        },

        center: [
          -98.5,
          38.5,
        ],

        zoom:
          3.5,
      });

    map.addControl(
      new maplibregl.NavigationControl(),
      "top-right"
    );

    mapRef.current =
      map;

    setMapInstance(
      map
    );

    return () => {

      map.remove();

      mapRef.current =
        null;

      setMapInstance(
        null
      );
    };

  }, []);

  // ------------------------------------------------
  // WEATHER DISPLAY
  // ------------------------------------------------

  useEffect(() => {

    if (
      !mapInstance
      ||
      weather.length === 0
    ) {

      return;
    }

    const sourceIds:
      string[] =
      [];

    const layerIds:
      string[] =
      [];

    const markers:
      maplibregl.Marker[] =
      [];

    const drawWeather =
      () => {

        weather.forEach(
          (
            hazard,
            index
          ) => {

            const sourceId =
              `weather-source-${index}`;

            const fillId =
              `weather-fill-${index}`;

            const outlineId =
              `weather-outline-${index}`;

            const circle =
              createWeatherCircle(
                hazard.latitude,
                hazard.longitude,
                hazard.radiusNm
              );

            if (
              !mapInstance
                .getSource(
                  sourceId
                )
            ) {

              mapInstance.addSource(
                sourceId,
                {

                  type:
                    "geojson",

                  data:
                    circle,
                }
              );
            }

            if (
              !mapInstance
                .getLayer(
                  fillId
                )
            ) {

              mapInstance.addLayer({

                id:
                  fillId,

                type:
                  "fill",

                source:
                  sourceId,

                paint: {

                  "fill-color":
                    "#ef4444",

                  "fill-opacity":
                    0.22,
                },
              });
            }

            if (
              !mapInstance
                .getLayer(
                  outlineId
                )
            ) {

              mapInstance.addLayer({

                id:
                  outlineId,

                type:
                  "line",

                source:
                  sourceId,

                paint: {

                  "line-color":
                    "#ef4444",

                  "line-width":
                    2,
                },
              });
            }

            sourceIds.push(
              sourceId
            );

            layerIds.push(
              fillId,
              outlineId
            );

            // ------------------------------------------------
            // ORIGINAL STORM MARKER
            // ------------------------------------------------

            const element =
              document.createElement(
                "div"
              );

            element.textContent =
              "⛈";

            element.style.width =
              "36px";

            element.style.height =
              "36px";

            element.style.display =
              "flex";

            element.style.alignItems =
              "center";

            element.style.justifyContent =
              "center";

            element.style.fontSize =
              "22px";

            element.style.background =
              "#0f172a";

            element.style.border =
              "2px solid #ef4444";

            element.style.borderRadius =
              "50%";

            element.style.boxShadow =
              "0 0 9px rgba(239,68,68,.85)";

            element.style.cursor =
              "pointer";

            // ------------------------------------------------
            // POPUP
            // NOW SHOWS RADIUS + DIAMETER
            // ------------------------------------------------

            const popup =
              new maplibregl.Popup({
                offset:
                  24,
              })
                .setHTML(`
                  <strong>
                    WEATHER HAZARD
                  </strong>

                  <br>

                  ${hazard.type}

                  <br>

                  Severity:
                  ${hazard.severity}

                  <br>

                  Radius:
                  ${hazard.radiusNm.toFixed(0)} NM

                  <br>

                  Diameter:
                  ${(hazard.radiusNm * 2).toFixed(0)} NM

                  <br>

                  Status:
                  ${
                    hazard.blocked
                      ? "BLOCKED"
                      : "OPEN"
                  }
                `);

            const marker =
              new maplibregl.Marker({

                element,

                anchor:
                  "center",
              })
                .setLngLat([
                  hazard.longitude,
                  hazard.latitude,
                ])
                .setPopup(
                  popup
                )
                .addTo(
                  mapInstance
                );

            markers.push(
              marker
            );
          }
        );
      };

    if (
      mapInstance.loaded()
    ) {

      drawWeather();

    } else {

      mapInstance.once(
        "load",
        drawWeather
      );
    }

    return () => {

      markers.forEach(
        (
          marker
        ) => {

          marker.remove();
        }
      );

      layerIds
        .slice()
        .reverse()
        .forEach(
          (
            id
          ) => {

            if (
              mapInstance
                .getLayer(
                  id
                )
            ) {

              mapInstance
                .removeLayer(
                  id
                );
            }
          }
        );

      sourceIds.forEach(
        (
          id
        ) => {

          if (
            mapInstance
              .getSource(
                id
              )
          ) {

            mapInstance
              .removeSource(
                id
              );
          }
        }
      );
    };

  }, [
    mapInstance,
    weather,
  ]);

  // ------------------------------------------------
  // UI
  // ------------------------------------------------

  return (

    <div
      className="map-section"
    >

      <div
        className="route-info"
      >

        <h2>
          United States Airspace Simulation
        </h2>

        <p>
          60 live simulated aircraft operating between major U.S. airports.
        </p>

        {/* -------------------------
            MAIN CONTROLS
        ------------------------- */}

        <div
          style={{

            display:
              "flex",

            gap:
              "10px",

            flexWrap:
              "wrap",

            justifyContent:
              "center",

            marginTop:
              "14px",

            marginBottom:
              "14px",
          }}
        >

          <button
            onClick={
              () =>
                sendSimulationCommand(
                  "start"
                )
            }

            disabled={
              !status.paused
            }
          >
            ▶ Start
          </button>

          <button
            onClick={
              () =>
                sendSimulationCommand(
                  "pause"
                )
            }

            disabled={
              status.paused
            }
          >
            ⏸ Pause
          </button>

          <button
            onClick={
              () =>
                sendSimulationCommand(
                  "reset"
                )
            }
          >
            ↺ Reset
          </button>

          <button
            onClick={
              () =>
                sendSimulationCommand(
                  "new-scenario"
                )
            }
          >
            ⟳ New Scenario
          </button>

        </div>

        {/* -------------------------
            SPEED
        ------------------------- */}

        <div
          style={{

            display:
              "flex",

            alignItems:
              "center",

            justifyContent:
              "center",

            flexWrap:
              "wrap",

            gap:
              "7px",

            marginBottom:
              "16px",
          }}
        >

          <span
            style={{

              marginRight:
                "5px",

              fontWeight:
                "600",
            }}
          >
            Speed:
          </span>

          {
            speedOptions.map(
              (
                speed
              ) => {

                const active =
                  Math.abs(
                    status.speedMultiplier
                      - speed
                  )
                  < 0.001;

                return (

                  <button
                    key={
                      speed
                    }

                    onClick={
                      () =>
                        setSimulationSpeed(
                          speed
                        )
                    }

                    style={{

                      padding:
                        "6px 10px",

                      borderRadius:
                        "6px",

                      border:
                        active
                          ? "2px solid #38bdf8"
                          : "1px solid #475569",

                      background:
                        active
                          ? "#0c4a6e"
                          : "#0f172a",

                      color:
                        "white",

                      fontWeight:
                        active
                          ? "700"
                          : "500",

                      cursor:
                        "pointer",
                    }}
                  >

                    {speed}x

                  </button>
                );
              }
            )
          }

        </div>

        {/* -------------------------
            STATUS
        ------------------------- */}

        <p>

          Simulation:{" "}

          <strong>

            {
              status.paused
                ? "PAUSED"
                : "RUNNING"
            }

          </strong>

          {" • "}

          Speed:{" "}

          <strong>
            {status.speedMultiplier}x
          </strong>

        </p>

        <p>

          Traffic:{" "}

          <strong>

            {
              status.scenarioType
                ===
                "ELEVATED_CONFLICT"

                ? "Elevated Conflict Probability"

                : "Normal Traffic"
            }

          </strong>

        </p>

        <p>

          Weather:{" "}

          <strong>

            {
              weather.length > 0

                ? `${weather.length} active hazards`

                : "Clear"
            }

          </strong>

        </p>

        <p
          style={{

            opacity:
              0.75,

            fontSize:
              "13px",
          }}
        >

          Click an aircraft to inspect it and view its flown path.
          Use Show All Paths on the map to display every aircraft trail.

        </p>

      </div>

      {/* -------------------------
          DASHBOARD
      ------------------------- */}

      <SimulationDashboard
        weatherCount={
          weather.length
        }

        speedMultiplier={
          status.speedMultiplier
        }
      />

      {/* -------------------------
          MAP
      ------------------------- */}

      <div
        ref={
          mapContainer
        }

        className="map-container"
      />

      <AircraftMarker
        key={
          scenarioVersion
        }

        map={
          mapInstance
        }

        showAllPaths={
          showAllPaths
        }

        onShowAllPathsChange={
          setShowAllPaths
        }
      />

    </div>
  );
}