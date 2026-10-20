import { useEffect, useRef } from "react";
import * as maplibregl from "maplibre-gl";
import { Client } from "@stomp/stompjs";
import { WS_BASE_URL } from "../config";

type FlightPhase =
  | "WAITING"
  | "CLIMBING"
  | "EN_ROUTE"
  | "DESCENDING"
  | "ARRIVED";

type Aircraft = {
  callsign: string;
  latitude: number;
  longitude: number;
  altitude: number;
  speed: number;
  heading: number;
  origin: string;
  destination: string;
  flightPhase: FlightPhase;
};

type Conflict = {
  aircraftOne: string;
  aircraftTwo: string;
  horizontalDistance: number;
  verticalDistance: number;
};

type PredictedConflict = {
  aircraftOne: string;
  aircraftTwo: string;
  predictedHorizontalDistance: number;
  predictedVerticalDistance: number;
  secondsAhead: number;
};

type Reroute = {
  callsign: string;
  reason: string;
};

type AircraftMarkerProps = {
  map: maplibregl.Map | null;

  showAllPaths: boolean;

  onShowAllPathsChange: (
    value: boolean
  ) => void;
};

type AircraftVisual = {
  marker: maplibregl.Marker;

  aircraftElement: HTMLDivElement;

  planeElement: HTMLDivElement;

  trailCoordinates: [number, number][];

  trailPath: SVGPathElement;

  glowPath: SVGPathElement;

  lastAircraft: Aircraft | null;
};

const MAX_SELECTED_PATH_RENDER_POINTS =
  260;

const MAX_ALL_PATH_RENDER_POINTS =
  90;

const MIN_TRAIL_POINT_DISTANCE_NM =
  0.35;

export default function AircraftMarker({
  map,
  showAllPaths,
  onShowAllPathsChange,
}: AircraftMarkerProps) {

  const aircraftVisualsRef =
    useRef<
      Map<string, AircraftVisual>
    >(new Map());

  const selectedCallsignRef =
    useRef<string | null>(
      null
    );

  const showAllPathsRef =
    useRef(
      showAllPaths
    );

  const updateTrailVisibility =
    () => {

      aircraftVisualsRef.current
        .forEach(
          (
            visual,
            callsign
          ) => {

            const visible =
              showAllPathsRef.current
              ||
              selectedCallsignRef.current
                === callsign;

            visual.trailPath.style.display =
              visible
                ? "block"
                : "none";

            visual.glowPath.style.display =
              visible
                ? "block"
                : "none";
          }
        );
    };

  useEffect(() => {

    showAllPathsRef.current =
      showAllPaths;

    updateTrailVisibility();

  }, [showAllPaths]);

  useEffect(() => {

    if (!map) {
      return;
    }

    const mapContainer =
      map.getContainer();

    let currentConflicts:
      Conflict[] =
      [];

    let currentPredictedConflicts:
      PredictedConflict[] =
      [];

    let currentReroutes:
      Reroute[] =
      [];

    let conflictCallsigns =
      new Set<string>();

    let predictedConflictCallsigns =
      new Set<string>();

    let redrawFrame:
      number | null =
      null;

    // ------------------------------------------------
    // CLEAN DEFAULT FILTER STATE
    // ------------------------------------------------

    const phaseFilters:
      Record<
        FlightPhase,
        boolean
      > = {

      WAITING:
        false,

      CLIMBING:
        true,

      EN_ROUTE:
        true,

      DESCENDING:
        true,

      ARRIVED:
        false,
    };

    let onlyActiveConflicts =
      false;

    let onlyPredictedConflicts =
      false;

    // ------------------------------------------------
    // DISTANCE HELPER
    // ------------------------------------------------

    const calculateDistanceNm =
      (
        first:
          [number, number],

        second:
          [number, number]
      ) => {

        const earthRadiusNm =
          3440.065;

        const lat1 =
          first[1]
          * Math.PI
          / 180;

        const lat2 =
          second[1]
          * Math.PI
          / 180;

        const deltaLat =
          (
            second[1]
            - first[1]
          )
          * Math.PI
          / 180;

        const deltaLon =
          (
            second[0]
            - first[0]
          )
          * Math.PI
          / 180;

        const a =
          Math.sin(
            deltaLat / 2
          )
          *
          Math.sin(
            deltaLat / 2
          )
          +
          Math.cos(
            lat1
          )
          *
          Math.cos(
            lat2
          )
          *
          Math.sin(
            deltaLon / 2
          )
          *
          Math.sin(
            deltaLon / 2
          );

        const c =
          2
          *
          Math.atan2(
            Math.sqrt(
              a
            ),
            Math.sqrt(
              1 - a
            )
          );

        return (
          earthRadiusNm
          * c
        );
      };

    // ------------------------------------------------
    // TRAIL SVG
    // ------------------------------------------------

    const trailSvg =
      document.createElementNS(
        "http://www.w3.org/2000/svg",
        "svg"
      );

    trailSvg.style.position =
      "absolute";

    trailSvg.style.left =
      "0";

    trailSvg.style.top =
      "0";

    trailSvg.style.width =
      "100%";

    trailSvg.style.height =
      "100%";

    trailSvg.style.pointerEvents =
      "none";

    trailSvg.style.zIndex =
      "5";

    mapContainer.appendChild(
      trailSvg
    );

    // ------------------------------------------------
    // AIRCRAFT DETAILS PANEL
    // ------------------------------------------------

    const detailPanel =
      document.createElement(
        "div"
      );

    detailPanel.style.position =
      "absolute";

    detailPanel.style.left =
      "18px";

    detailPanel.style.bottom =
      "18px";

    detailPanel.style.width =
      "310px";

    detailPanel.style.zIndex =
      "70";

    detailPanel.style.background =
      "rgba(15,23,42,.97)";

    detailPanel.style.border =
      "1px solid #475569";

    detailPanel.style.borderRadius =
      "12px";

    detailPanel.style.boxShadow =
      "0 8px 25px rgba(0,0,0,.42)";

    detailPanel.style.color =
      "#ffffff";

    detailPanel.style.padding =
      "17px";

    detailPanel.style.display =
      "none";

    detailPanel.style.backdropFilter =
      "blur(6px)";

    mapContainer.appendChild(
      detailPanel
    );

    // ------------------------------------------------
    // ALERT PANEL
    // ------------------------------------------------

    const alertPanel =
      document.createElement(
        "div"
      );

    alertPanel.style.position =
      "absolute";

    alertPanel.style.top =
      "15px";

    alertPanel.style.left =
      "15px";

    alertPanel.style.width =
      "285px";

    alertPanel.style.maxHeight =
      "275px";

    alertPanel.style.overflowY =
      "auto";

    alertPanel.style.zIndex =
      "65";

    alertPanel.style.background =
      "rgba(15,23,42,.96)";

    alertPanel.style.border =
      "1px solid #334155";

    alertPanel.style.borderRadius =
      "11px";

    alertPanel.style.boxShadow =
      "0 6px 20px rgba(0,0,0,.35)";

    alertPanel.style.color =
      "#ffffff";

    alertPanel.style.padding =
      "12px";

    alertPanel.style.backdropFilter =
      "blur(6px)";

    mapContainer.appendChild(
      alertPanel
    );

    const renderAlerts =
      () => {

        const activeCount =
          currentConflicts.length;

        const predictedCount =
          currentPredictedConflicts
            .length;

        const hasResolution =
          currentReroutes.length > 0;

        let html = `
          <div
            style="
              display:flex;
              justify-content:space-between;
              align-items:center;
              margin-bottom:10px;
            "
          >

            <div
              style="
                font-size:11px;
                font-weight:800;
                letter-spacing:1px;
                color:#94a3b8;
              "
            >
              AIRSPACE ALERTS
            </div>

            <div
              style="
                font-size:10px;
                color:#64748b;
              "
            >
              LIVE
            </div>

          </div>
        `;

        if (
          activeCount === 0
          &&
          predictedCount === 0
          &&
          !hasResolution
        ) {

          html += `
            <div
              style="
                padding:8px 9px;
                background:rgba(34,197,94,.10);
                border:1px solid rgba(34,197,94,.35);
                border-radius:7px;
              "
            >

              <div
                style="
                  color:#4ade80;
                  font-weight:700;
                  font-size:13px;
                "
              >
                ✓ Airspace normal
              </div>

              <div
                style="
                  color:#94a3b8;
                  font-size:11px;
                  margin-top:3px;
                "
              >
                No active alerts
              </div>

            </div>
          `;

          alertPanel.innerHTML =
            html;

          return;
        }

        if (
          activeCount > 0
        ) {

          html += `
            <div
              style="
                margin-bottom:9px;
                padding:8px 9px;
                background:rgba(239,68,68,.12);
                border:1px solid rgba(239,68,68,.55);
                border-radius:7px;
              "
            >

              <div
                style="
                  color:#f87171;
                  font-size:11px;
                  font-weight:800;
                  margin-bottom:5px;
                "
              >
                ● ACTIVE CONFLICT${
                  activeCount > 1
                    ? "S"
                    : ""
                }
                (${activeCount})
              </div>
          `;

          currentConflicts
            .slice(
              0,
              4
            )
            .forEach(
              (
                conflict
              ) => {

                html += `
                  <div
                    style="
                      font-size:12px;
                      font-weight:600;
                      margin-top:4px;
                    "
                  >
                    ${conflict.aircraftOne}
                    /
                    ${conflict.aircraftTwo}
                  </div>
                `;
              }
            );

          if (
            activeCount > 4
          ) {

            html += `
              <div
                style="
                  color:#94a3b8;
                  font-size:10px;
                  margin-top:5px;
                "
              >
                +${activeCount - 4} more
              </div>
            `;
          }

          html +=
            `</div>`;
        }

        if (
          predictedCount > 0
        ) {

          html += `
            <div
              style="
                margin-bottom:9px;
                padding:8px 9px;
                background:rgba(245,158,11,.10);
                border:1px solid rgba(245,158,11,.5);
                border-radius:7px;
              "
            >

              <div
                style="
                  color:#fbbf24;
                  font-size:11px;
                  font-weight:800;
                  margin-bottom:5px;
                "
              >
                ● PREDICTED CONFLICT${
                  predictedCount > 1
                    ? "S"
                    : ""
                }
                (${predictedCount})
              </div>
          `;

          currentPredictedConflicts
            .slice(
              0,
              4
            )
            .forEach(
              (
                conflict
              ) => {

                html += `
                  <div
                    style="
                      font-size:12px;
                      font-weight:600;
                      margin-top:4px;
                    "
                  >
                    ${conflict.aircraftOne}
                    /
                    ${conflict.aircraftTwo}

                    <span
                      style="
                        color:#94a3b8;
                        font-size:10px;
                        margin-left:4px;
                      "
                    >
                      ${Math.round(
                        conflict.secondsAhead
                      )}s
                    </span>

                  </div>
                `;
              }
            );

          if (
            predictedCount > 4
          ) {

            html += `
              <div
                style="
                  color:#94a3b8;
                  font-size:10px;
                  margin-top:5px;
                "
              >
                +${predictedCount - 4} more
              </div>
            `;
          }

          html +=
            `</div>`;
        }

        if (
          hasResolution
        ) {

          const latest =
            currentReroutes[
              currentReroutes.length - 1
            ];

          html += `
            <div
              style="
                padding:8px 9px;
                background:rgba(56,189,248,.10);
                border:1px solid rgba(56,189,248,.45);
                border-radius:7px;
              "
            >

              <div
                style="
                  color:#38bdf8;
                  font-size:11px;
                  font-weight:800;
                  margin-bottom:5px;
                "
              >
                ↪ LATEST RESOLUTION
              </div>

              <div
                style="
                  font-size:12px;
                  font-weight:700;
                  color:#ffffff;
                "
              >
                ${latest.callsign}
              </div>

              <div
                style="
                  font-size:11px;
                  color:#cbd5e1;
                  margin-top:3px;
                  line-height:1.35;
                "
              >
                ${latest.reason}
              </div>

            </div>
          `;
        }

        alertPanel.innerHTML =
          html;
      };

    renderAlerts();

    // ------------------------------------------------
    // SEARCH
    // ------------------------------------------------

    const searchContainer =
      document.createElement(
        "div"
      );

    searchContainer.style.position =
      "absolute";

    searchContainer.style.top =
      "58px";

    searchContainer.style.right =
      "55px";

    searchContainer.style.width =
      "235px";

    searchContainer.style.zIndex =
      "80";

    mapContainer.appendChild(
      searchContainer
    );

    const searchInput =
      document.createElement(
        "input"
      );

    searchInput.type =
      "text";

    searchInput.placeholder =
      "Search aircraft...";

    searchInput.style.width =
      "100%";

    searchInput.style.boxSizing =
      "border-box";

    searchInput.style.padding =
      "9px 12px";

    searchInput.style.border =
      "1px solid #475569";

    searchInput.style.borderRadius =
      "8px";

    searchInput.style.background =
      "rgba(15,23,42,.96)";

    searchInput.style.color =
      "#ffffff";

    searchInput.style.outline =
      "none";

    searchInput.style.fontSize =
      "13px";

    searchInput.style.boxShadow =
      "0 3px 10px rgba(0,0,0,.25)";

    searchContainer.appendChild(
      searchInput
    );

    const searchResults =
      document.createElement(
        "div"
      );

    searchResults.style.marginTop =
      "5px";

    searchResults.style.background =
      "rgba(15,23,42,.98)";

    searchResults.style.border =
      "1px solid #334155";

    searchResults.style.borderRadius =
      "8px";

    searchResults.style.display =
      "none";

    searchResults.style.maxHeight =
      "230px";

    searchResults.style.overflowY =
      "auto";

    searchResults.style.boxShadow =
      "0 6px 18px rgba(0,0,0,.35)";

    searchContainer.appendChild(
      searchResults
    );

    // ------------------------------------------------
    // PATH TOGGLE
    // ------------------------------------------------

    const pathToggle =
      document.createElement(
        "button"
      );

    pathToggle.style.position =
      "absolute";

    pathToggle.style.top =
      "15px";

    pathToggle.style.right =
      "55px";

    pathToggle.style.zIndex =
      "60";

    pathToggle.style.padding =
      "8px 13px";

    pathToggle.style.borderRadius =
      "7px";

    pathToggle.style.border =
      "1px solid #334155";

    pathToggle.style.background =
      "rgba(15,23,42,.94)";

    pathToggle.style.color =
      "#ffffff";

    pathToggle.style.fontSize =
      "13px";

    pathToggle.style.fontWeight =
      "600";

    pathToggle.style.cursor =
      "pointer";

    pathToggle.style.boxShadow =
      "0 2px 8px rgba(0,0,0,.25)";

    const updateToggleText =
      () => {

        pathToggle.textContent =
          showAllPathsRef.current
            ? "Hide All Paths"
            : "Show All Paths";
      };

    updateToggleText();

    mapContainer.appendChild(
      pathToggle
    );

    // ------------------------------------------------
    // FILTER PANEL
    // ------------------------------------------------

    const filterPanel =
      document.createElement(
        "div"
      );

    filterPanel.style.position =
      "absolute";

    filterPanel.style.top =
      "104px";

    filterPanel.style.right =
      "55px";

    filterPanel.style.width =
      "235px";

    filterPanel.style.zIndex =
      "55";

    filterPanel.style.background =
      "rgba(15,23,42,.96)";

    filterPanel.style.border =
      "1px solid #334155";

    filterPanel.style.borderRadius =
      "9px";

    filterPanel.style.padding =
      "11px 12px";

    filterPanel.style.color =
      "#ffffff";

    filterPanel.style.boxShadow =
      "0 5px 15px rgba(0,0,0,.28)";

    filterPanel.style.fontSize =
      "12px";

    mapContainer.appendChild(
      filterPanel
    );

    const filterTitle =
      document.createElement(
        "div"
      );

    filterTitle.textContent =
      "AIRCRAFT FILTERS";

    filterTitle.style.fontSize =
      "11px";

    filterTitle.style.fontWeight =
      "800";

    filterTitle.style.letterSpacing =
      "1px";

    filterTitle.style.color =
      "#94a3b8";

    filterTitle.style.marginBottom =
      "9px";

    filterPanel.appendChild(
      filterTitle
    );

    const phaseNames:
      {
        phase: FlightPhase;
        label: string;
        defaultChecked: boolean;
      }[] = [

      {
        phase:
          "WAITING",
        label:
          "Waiting",
        defaultChecked:
          false,
      },

      {
        phase:
          "CLIMBING",
        label:
          "Climbing",
        defaultChecked:
          true,
      },

      {
        phase:
          "EN_ROUTE",
        label:
          "En Route",
        defaultChecked:
          true,
      },

      {
        phase:
          "DESCENDING",
        label:
          "Descending",
        defaultChecked:
          true,
      },

      {
        phase:
          "ARRIVED",
        label:
          "Arrived",
        defaultChecked:
          false,
      },
    ];

    // ------------------------------------------------
    // LEGEND
    // ------------------------------------------------

    const legend =
      document.createElement(
        "div"
      );

    legend.style.position =
      "absolute";

    legend.style.right =
      "18px";

    legend.style.bottom =
      "28px";

    legend.style.zIndex =
      "50";

    legend.style.width =
      "175px";

    legend.style.background =
      "rgba(15,23,42,.95)";

    legend.style.border =
      "1px solid #334155";

    legend.style.borderRadius =
      "9px";

    legend.style.padding =
      "11px 12px";

    legend.style.color =
      "#ffffff";

    legend.style.boxShadow =
      "0 5px 15px rgba(0,0,0,.25)";

    legend.innerHTML = `
      <div
        style="
          color:#94a3b8;
          font-size:11px;
          font-weight:800;
          letter-spacing:1px;
          margin-bottom:8px;
        "
      >
        MAP LEGEND
      </div>

      <div style="margin-bottom:5px;">
        <span
          style="
            color:#9ca3af;
            font-size:16px;
            margin-right:7px;
          "
        >
          ✈
        </span>
        Normal aircraft
      </div>

      <div style="margin-bottom:5px;">
        <span
          style="
            color:#38bdf8;
            font-size:18px;
            margin-right:7px;
          "
        >
          ✈
        </span>
        Selected
      </div>

      <div style="margin-bottom:5px;">
        <span
          style="
            color:#f59e0b;
            font-size:16px;
            margin-right:7px;
          "
        >
          ✈
        </span>
        Predicted conflict
      </div>

      <div style="margin-bottom:5px;">
        <span
          style="
            color:#ef4444;
            font-size:16px;
            margin-right:7px;
          "
        >
          ✈
        </span>
        Active conflict
      </div>

      <div style="margin-bottom:5px;">
        <span
          style="
            display:inline-block;
            width:22px;
            height:2px;
            background:#2563eb;
            margin-right:8px;
            vertical-align:middle;
          "
        ></span>
        Flown path
      </div>

      <div>
        <span
          style="
            display:inline-block;
            width:13px;
            height:13px;
            border-radius:50%;
            background:rgba(239,68,68,.3);
            border:2px solid #ef4444;
            margin-right:8px;
            vertical-align:middle;
          "
        ></span>
        Weather hazard
      </div>
    `;

    mapContainer.appendChild(
      legend
    );

    // ------------------------------------------------
    // TRAIL FUNCTIONS
    // ------------------------------------------------

    const reduceTrailPoints =
      (
        coordinates:
          [number, number][],
        maximumPoints:
          number
      ) => {

        if (
          coordinates.length
          <= maximumPoints
        ) {

          return coordinates;
        }

        const result:
          [number, number][] =
          [];

        const step =
          (
            coordinates.length
            - 1
          )
          /
          (
            maximumPoints
            - 1
          );

        for (
          let i = 0;
          i < maximumPoints;
          i++
        ) {

          const index =
            Math.min(
              coordinates.length - 1,
              Math.round(
                i * step
              )
            );

          result.push(
            coordinates[
              index
            ]
          );
        }

        return result;
      };

    const buildSmoothPath =
      (
        points:
          maplibregl.Point[]
      ) => {

        if (
          points.length < 2
        ) {

          return "";
        }

        if (
          points.length === 2
        ) {

          return (
            `M ${points[0].x} ${points[0].y}`
            +
            ` L ${points[1].x} ${points[1].y}`
          );
        }

        let path =
          `M ${points[0].x} ${points[0].y}`;

        for (
          let i = 1;
          i < points.length - 1;
          i++
        ) {

          const current =
            points[i];

          const next =
            points[i + 1];

          const midX =
            (
              current.x
              + next.x
            )
            / 2;

          const midY =
            (
              current.y
              + next.y
            )
            / 2;

          path +=
            ` Q ${current.x} ${current.y} ${midX} ${midY}`;
        }

        const secondLast =
          points[
            points.length - 2
          ];

        const last =
          points[
            points.length - 1
          ];

        path +=
          ` Q ${secondLast.x} ${secondLast.y} ${last.x} ${last.y}`;

        return path;
      };

    const drawTrail =
      (
        visual:
          AircraftVisual
      ) => {

        const callsign =
          visual.lastAircraft
            ?.callsign;

        const aircraftVisible =
          visual.aircraftElement
            .style.display
          !== "none";

        const selected =
          selectedCallsignRef.current
            === callsign;

        const shouldShow =
          aircraftVisible
          &&
          (
            showAllPathsRef.current
            ||
            selected
          );

        if (
          !shouldShow
          ||
          visual.trailCoordinates
            .length < 2
        ) {

          visual.trailPath
            .style.display =
            "none";

          visual.glowPath
            .style.display =
            "none";

          return;
        }

        visual.trailPath
          .style.display =
          "block";

        visual.glowPath
          .style.display =
          "block";

        const coordinatesToRender =
          reduceTrailPoints(
            visual.trailCoordinates,

            selected
              ? MAX_SELECTED_PATH_RENDER_POINTS
              : MAX_ALL_PATH_RENDER_POINTS
          );

        const points =
          coordinatesToRender
            .map(
              ([
                longitude,
                latitude,
              ]) =>

                map.project([
                  longitude,
                  latitude,
                ])
            );

        const pathData =
          buildSmoothPath(
            points
          );

        visual.glowPath
          .setAttribute(
            "d",
            pathData
          );

        visual.trailPath
          .setAttribute(
            "d",
            pathData
          );
      };

    const drawAllTrails =
      () => {

        aircraftVisualsRef.current
          .forEach(
            (
              visual
            ) => {

              drawTrail(
                visual
              );
            }
          );
      };

    const scheduleTrailRedraw =
      () => {

        if (
          redrawFrame !== null
        ) {

          return;
        }

        redrawFrame =
          requestAnimationFrame(
            () => {

              redrawFrame =
                null;

              drawAllTrails();
            }
          );
      };

    // ------------------------------------------------
    // VISIBILITY
    // ------------------------------------------------

    const updateAircraftVisibility =
      () => {

        aircraftVisualsRef.current
          .forEach(
            (
              visual,
              callsign
            ) => {

              const aircraft =
                visual.lastAircraft;

              if (!aircraft) {
                return;
              }

              if (
                selectedCallsignRef.current
                  === callsign
              ) {

                visual.aircraftElement
                  .style.display =
                  "flex";

                return;
              }

              const phaseAllowed =
                phaseFilters[
                  aircraft.flightPhase
                ];

              let statusAllowed =
                true;

              if (
                onlyActiveConflicts
                ||
                onlyPredictedConflicts
              ) {

                const activeMatch =
                  onlyActiveConflicts
                  &&
                  conflictCallsigns.has(
                    callsign
                  );

                const predictedMatch =
                  onlyPredictedConflicts
                  &&
                  predictedConflictCallsigns
                    .has(
                      callsign
                    );

                statusAllowed =
                  activeMatch
                  ||
                  predictedMatch;
              }

              visual.aircraftElement
                .style.display =
                phaseAllowed
                &&
                statusAllowed

                  ? "flex"
                  : "none";
            }
          );

        scheduleTrailRedraw();
      };

    // ------------------------------------------------
    // FILTER CHECKBOXES
    // ------------------------------------------------

    phaseNames.forEach(
      (
        item
      ) => {

        const row =
          document.createElement(
            "label"
          );

        row.style.display =
          "flex";

        row.style.alignItems =
          "center";

        row.style.gap =
          "7px";

        row.style.marginBottom =
          "6px";

        row.style.cursor =
          "pointer";

        row.style.color =
          "#cbd5e1";

        const checkbox =
          document.createElement(
            "input"
          );

        checkbox.type =
          "checkbox";

        checkbox.checked =
          item.defaultChecked;

        checkbox.onchange =
          () => {

            phaseFilters[
              item.phase
            ] =
              checkbox.checked;

            updateAircraftVisibility();
          };

        row.appendChild(
          checkbox
        );

        row.appendChild(
          document.createTextNode(
            item.label
          )
        );

        filterPanel.appendChild(
          row
        );
      }
    );

    const divider =
      document.createElement(
        "div"
      );

    divider.style.height =
      "1px";

    divider.style.background =
      "#334155";

    divider.style.margin =
      "9px 0";

    filterPanel.appendChild(
      divider
    );

    const conflictFilterRow =
      document.createElement(
        "label"
      );

    conflictFilterRow.style.display =
      "flex";

    conflictFilterRow.style.alignItems =
      "center";

    conflictFilterRow.style.gap =
      "7px";

    conflictFilterRow.style.marginBottom =
      "6px";

    conflictFilterRow.style.cursor =
      "pointer";

    conflictFilterRow.style.color =
      "#fca5a5";

    const conflictFilterCheckbox =
      document.createElement(
        "input"
      );

    conflictFilterCheckbox.type =
      "checkbox";

    conflictFilterCheckbox.checked =
      false;

    conflictFilterCheckbox.onchange =
      () => {

        onlyActiveConflicts =
          conflictFilterCheckbox
            .checked;

        updateAircraftVisibility();
      };

    conflictFilterRow.appendChild(
      conflictFilterCheckbox
    );

    conflictFilterRow.appendChild(
      document.createTextNode(
        "Active conflicts only"
      )
    );

    filterPanel.appendChild(
      conflictFilterRow
    );

    const predictedFilterRow =
      document.createElement(
        "label"
      );

    predictedFilterRow.style.display =
      "flex";

    predictedFilterRow.style.alignItems =
      "center";

    predictedFilterRow.style.gap =
      "7px";

    predictedFilterRow.style.marginBottom =
      "9px";

    predictedFilterRow.style.cursor =
      "pointer";

    predictedFilterRow.style.color =
      "#fcd34d";

    const predictedFilterCheckbox =
      document.createElement(
        "input"
      );

    predictedFilterCheckbox.type =
      "checkbox";

    predictedFilterCheckbox.checked =
      false;

    predictedFilterCheckbox.onchange =
      () => {

        onlyPredictedConflicts =
          predictedFilterCheckbox
            .checked;

        updateAircraftVisibility();
      };

    predictedFilterRow.appendChild(
      predictedFilterCheckbox
    );

    predictedFilterRow.appendChild(
      document.createTextNode(
        "Predicted conflicts only"
      )
    );

    filterPanel.appendChild(
      predictedFilterRow
    );

    const resetFiltersButton =
      document.createElement(
        "button"
      );

    resetFiltersButton.textContent =
      "Reset Filters";

    resetFiltersButton.style.width =
      "100%";

    resetFiltersButton.style.padding =
      "7px 9px";

    resetFiltersButton.style.border =
      "1px solid #475569";

    resetFiltersButton.style.borderRadius =
      "6px";

    resetFiltersButton.style.background =
      "#1e293b";

    resetFiltersButton.style.color =
      "#ffffff";

    resetFiltersButton.style.cursor =
      "pointer";

    resetFiltersButton.style.fontSize =
      "12px";

    resetFiltersButton.style.fontWeight =
      "600";

    resetFiltersButton.onclick =
      () => {

        const inputs =
          filterPanel.querySelectorAll(
            'input[type="checkbox"]'
          );

        phaseNames.forEach(
          (
            item,
            index
          ) => {

            phaseFilters[
              item.phase
            ] =
              item.defaultChecked;

            const checkbox =
              inputs[
                index
              ] as HTMLInputElement;

            if (checkbox) {

              checkbox.checked =
                item.defaultChecked;
            }
          }
        );

        conflictFilterCheckbox.checked =
          false;

        predictedFilterCheckbox.checked =
          false;

        onlyActiveConflicts =
          false;

        onlyPredictedConflicts =
          false;

        updateAircraftVisibility();
      };

    filterPanel.appendChild(
      resetFiltersButton
    );

    // ------------------------------------------------
    // DETAILS
    // ------------------------------------------------

    const updateDetailPanel =
      (
        aircraft:
          Aircraft
      ) => {

        const isConflict =
          conflictCallsigns.has(
            aircraft.callsign
          );

        const isPredicted =
          predictedConflictCallsigns
            .has(
              aircraft.callsign
            );

        let statusText =
          "NORMAL";

        let statusColor =
          "#22c55e";

        if (isConflict) {

          statusText =
            "ACTIVE CONFLICT";

          statusColor =
            "#ef4444";

        } else if (
          isPredicted
        ) {

          statusText =
            "PREDICTED CONFLICT";

          statusColor =
            "#f59e0b";
        }

        const phaseText =
          aircraft.flightPhase
            .replace(
              "_",
              " "
            );

        detailPanel.innerHTML = `
          <div
            style="
              display:flex;
              justify-content:space-between;
              align-items:center;
              margin-bottom:14px;
            "
          >

            <div
              style="
                font-size:12px;
                font-weight:700;
                letter-spacing:1.2px;
                color:#94a3b8;
              "
            >
              AIRCRAFT DETAILS
            </div>

            <button
              id="airroute-detail-close"
              style="
                background:none;
                border:none;
                color:#94a3b8;
                font-size:20px;
                cursor:pointer;
                padding:0 3px;
              "
            >
              ×
            </button>

          </div>

          <div
            style="
              font-size:24px;
              font-weight:800;
              margin-bottom:3px;
            "
          >
            ${aircraft.callsign}
          </div>

          <div
            style="
              color:#cbd5e1;
              font-size:15px;
              margin-bottom:16px;
            "
          >
            ${aircraft.origin}
            →
            ${aircraft.destination}
          </div>

          <div
            style="
              height:1px;
              background:#334155;
              margin-bottom:14px;
            "
          ></div>

          <div
            style="
              display:grid;
              grid-template-columns:105px 1fr;
              row-gap:10px;
              font-size:14px;
            "
          >

            <span style="color:#94a3b8;">
              Phase
            </span>

            <strong>
              ${phaseText}
            </strong>

            <span style="color:#94a3b8;">
              Altitude
            </span>

            <strong>
              ${Math.round(
                aircraft.altitude
              ).toLocaleString()} ft
            </strong>

            <span style="color:#94a3b8;">
              Speed
            </span>

            <strong>
              ${Math.round(
                aircraft.speed
              )} kt
            </strong>

            <span style="color:#94a3b8;">
              Heading
            </span>

            <strong>
              ${Math.round(
                aircraft.heading
              )}°
            </strong>

            <span style="color:#94a3b8;">
              Status
            </span>

            <strong
              style="
                color:${statusColor};
              "
            >
              ${statusText}
            </strong>

          </div>
        `;

        detailPanel.style.display =
          "block";

        const closeButton =
          detailPanel.querySelector(
            "#airroute-detail-close"
          ) as HTMLButtonElement | null;

        if (closeButton) {

          closeButton.onclick =
            () => {

              selectedCallsignRef.current =
                null;

              detailPanel.style.display =
                "none";

              updateAircraftStyles();

              updateAircraftVisibility();

              updateTrailVisibility();

              scheduleTrailRedraw();
            };
        }
      };

    // ------------------------------------------------
    // AIRCRAFT STYLES
    // ------------------------------------------------

    const updateAircraftStyles =
      () => {

        aircraftVisualsRef.current
          .forEach(
            (
              visual,
              callsign
            ) => {

              const selected =
                selectedCallsignRef.current
                  === callsign;

              if (selected) {

                visual.planeElement
                  .style.color =
                  "#38bdf8";

                visual.planeElement
                  .style.fontSize =
                  "34px";

                visual.aircraftElement
                  .style.filter =
                  "drop-shadow(0 0 8px rgba(56,189,248,1)) drop-shadow(0 0 15px rgba(56,189,248,.75))";

                visual.aircraftElement
                  .style.zIndex =
                  "30";

                return;
              }

              // CLEANER NORMAL SIZE
              visual.planeElement
                .style.fontSize =
                "22px";

              visual.aircraftElement
                .style.zIndex =
                "20";

              if (
                conflictCallsigns.has(
                  callsign
                )
              ) {

                visual.planeElement
                  .style.color =
                  "#ef4444";

                visual.aircraftElement
                  .style.filter =
                  "drop-shadow(0 0 6px rgba(239,68,68,.95))";

                return;
              }

              if (
                predictedConflictCallsigns
                  .has(
                    callsign
                  )
              ) {

                visual.planeElement
                  .style.color =
                  "#f59e0b";

                visual.aircraftElement
                  .style.filter =
                  "drop-shadow(0 0 6px rgba(245,158,11,.9))";

                return;
              }

              visual.planeElement
                .style.color =
                "#9ca3af";

              visual.aircraftElement
                .style.filter =
                "drop-shadow(0 1px 2px rgba(0,0,0,.55))";
            }
          );

        if (
          selectedCallsignRef.current
        ) {

          const selected =
            aircraftVisualsRef.current
              .get(
                selectedCallsignRef.current
              );

          if (
            selected
              ?.lastAircraft
          ) {

            updateDetailPanel(
              selected.lastAircraft
            );
          }
        }
      };

    // ------------------------------------------------
    // SELECT AIRCRAFT
    // ------------------------------------------------

    const selectAircraft =
      (
        callsign:
          string,

        centerMap:
          boolean
      ) => {

        const visual =
          aircraftVisualsRef.current
            .get(
              callsign
            );

        if (
          !visual
          ||
          !visual.lastAircraft
        ) {

          return;
        }

        selectedCallsignRef.current =
          callsign;

        const aircraft =
          visual.lastAircraft;

        updateDetailPanel(
          aircraft
        );

        updateAircraftStyles();

        updateAircraftVisibility();

        updateTrailVisibility();

        scheduleTrailRedraw();

        if (centerMap) {

          map.easeTo({

            center: [
              aircraft.longitude,
              aircraft.latitude,
            ],

            zoom:
              Math.max(
                map.getZoom(),
                6
              ),

            duration:
              900,
          });
        }
      };

    // ------------------------------------------------
    // SEARCH RESULTS
    // ------------------------------------------------

    const updateSearchResults =
      () => {

        const query =
          searchInput.value
            .trim()
            .toUpperCase();

        searchResults.innerHTML =
          "";

        if (
          query.length === 0
        ) {

          searchResults.style.display =
            "none";

          return;
        }

        const matches =
          Array.from(
            aircraftVisualsRef.current
              .entries()
          )
            .filter(
              ([
                callsign
              ]) =>

                callsign
                  .toUpperCase()
                  .includes(
                    query
                  )
            )
            .slice(
              0,
              8
            );

        if (
          matches.length === 0
        ) {

          const empty =
            document.createElement(
              "div"
            );

          empty.textContent =
            "No aircraft found";

          empty.style.padding =
            "10px 12px";

          empty.style.color =
            "#94a3b8";

          empty.style.fontSize =
            "13px";

          searchResults.appendChild(
            empty
          );

          searchResults.style.display =
            "block";

          return;
        }

        matches.forEach(
          ([
            callsign,
            visual
          ]) => {

            const aircraft =
              visual.lastAircraft;

            if (!aircraft) {
              return;
            }

            const row =
              document.createElement(
                "div"
              );

            row.style.padding =
              "9px 12px";

            row.style.cursor =
              "pointer";

            row.style.borderBottom =
              "1px solid #1e293b";

            row.innerHTML = `
              <div
                style="
                  color:#ffffff;
                  font-weight:700;
                  font-size:13px;
                "
              >
                ${callsign}
              </div>

              <div
                style="
                  color:#94a3b8;
                  font-size:11px;
                  margin-top:2px;
                "
              >
                ${aircraft.origin}
                →
                ${aircraft.destination}
                ·
                ${aircraft.flightPhase.replace(
                  "_",
                  " "
                )}
              </div>
            `;

            row.onmouseenter =
              () => {

                row.style.background =
                  "#1e293b";
              };

            row.onmouseleave =
              () => {

                row.style.background =
                  "transparent";
              };

            row.onmousedown =
              (
                event
              ) => {

                event.preventDefault();

                event.stopPropagation();

                selectAircraft(
                  callsign,
                  true
                );

                searchInput.value =
                  callsign;

                searchResults.style.display =
                  "none";
              };

            searchResults.appendChild(
              row
            );
          }
        );

        searchResults.style.display =
          "block";
      };

    searchInput.oninput =
      updateSearchResults;

    searchInput.onfocus =
      updateSearchResults;

    // ------------------------------------------------
    // CREATE AIRCRAFT
    // ------------------------------------------------

    const createAircraftVisual =
      (
        aircraft:
          Aircraft
      ) => {

        const aircraftElement =
          document.createElement(
            "div"
          );

        aircraftElement.style.width =
          "30px";

        aircraftElement.style.height =
          "30px";

        aircraftElement.style.display =
          "flex";

        aircraftElement.style.alignItems =
          "center";

        aircraftElement.style.justifyContent =
          "center";

        aircraftElement.style.cursor =
          "pointer";

        aircraftElement.style.zIndex =
          "20";

        const plane =
          document.createElement(
            "div"
          );

        plane.textContent =
          "✈";

        plane.style.fontSize =
          "22px";

        plane.style.color =
          "#9ca3af";

        plane.style.lineHeight =
          "1";

        plane.style.transition =
          "font-size .15s ease, color .15s ease, transform .1s linear";

        aircraftElement.appendChild(
          plane
        );

        const marker =
          new maplibregl.Marker({

            element:
              aircraftElement,

            anchor:
              "center",
          })
            .setLngLat([
              aircraft.longitude,
              aircraft.latitude,
            ])
            .addTo(
              map
            );

        const glowPath =
          document.createElementNS(
            "http://www.w3.org/2000/svg",
            "path"
          );

        glowPath.setAttribute(
          "fill",
          "none"
        );

        glowPath.setAttribute(
          "stroke",
          "rgba(37,99,235,.10)"
        );

        glowPath.setAttribute(
          "stroke-width",
          "2.5"
        );

        glowPath.setAttribute(
          "stroke-linecap",
          "round"
        );

        glowPath.setAttribute(
          "stroke-linejoin",
          "round"
        );

        glowPath.style.display =
          "none";

        const trailPath =
          document.createElementNS(
            "http://www.w3.org/2000/svg",
            "path"
          );

        trailPath.setAttribute(
          "fill",
          "none"
        );

        trailPath.setAttribute(
          "stroke",
          "#2563eb"
        );

        trailPath.setAttribute(
          "stroke-width",
          "1.2"
        );

        trailPath.setAttribute(
          "stroke-linecap",
          "round"
        );

        trailPath.setAttribute(
          "stroke-linejoin",
          "round"
        );

        trailPath.style.display =
          "none";

        trailSvg.appendChild(
          glowPath
        );

        trailSvg.appendChild(
          trailPath
        );

        const visual:
          AircraftVisual = {

          marker,

          aircraftElement,

          planeElement:
            plane,

          trailCoordinates: [
            [
              aircraft.longitude,
              aircraft.latitude,
            ],
          ],

          trailPath,

          glowPath,

          lastAircraft:
            aircraft,
        };

        aircraftVisualsRef.current
          .set(
            aircraft.callsign,
            visual
          );

        aircraftElement.onclick =
          (
            event
          ) => {

            event.stopPropagation();

            if (
              selectedCallsignRef.current
                === aircraft.callsign
            ) {

              selectedCallsignRef.current =
                null;

              detailPanel.style.display =
                "none";

              updateAircraftStyles();

              updateAircraftVisibility();

              updateTrailVisibility();

              scheduleTrailRedraw();

              return;
            }

            selectAircraft(
              aircraft.callsign,
              false
            );
          };

        return visual;
      };

    // ------------------------------------------------
    // UPDATE AIRCRAFT
    // ------------------------------------------------

    const updateAircraft =
      (
        aircraft:
          Aircraft
      ) => {

        let visual =
          aircraftVisualsRef.current
            .get(
              aircraft.callsign
            );

        if (!visual) {

          visual =
            createAircraftVisual(
              aircraft
            );
        }

        const previous =
          visual.lastAircraft;

        const newFlight =
          previous !== null
          &&
          (
            previous.origin
              !== aircraft.origin
            ||
            previous.destination
              !== aircraft.destination
          );

        if (newFlight) {

          visual.trailCoordinates =
            [
              [
                aircraft.longitude,
                aircraft.latitude,
              ],
            ];

        } else if (
          aircraft.flightPhase
            !== "WAITING"
        ) {

          const newPoint:
            [number, number] =
            [
              aircraft.longitude,
              aircraft.latitude,
            ];

          const previousPoint =
            visual.trailCoordinates[
              visual.trailCoordinates.length
              - 1
            ];

          const shouldAddPoint =
            !previousPoint
            ||
            calculateDistanceNm(
              previousPoint,
              newPoint
            )
            >=
            MIN_TRAIL_POINT_DISTANCE_NM;

          if (
            shouldAddPoint
          ) {

            // Keep the COMPLETE trail for the current flight.
            // We only downsample temporarily while drawing, so the
            // stored history never loses its departure-airport end.
            visual.trailCoordinates.push(
              newPoint
            );
          }
        }

        visual.marker.setLngLat([
          aircraft.longitude,
          aircraft.latitude,
        ]);

        visual.planeElement
          .style.transform =
          `rotate(${aircraft.heading - 90}deg)`;

        if (
          aircraft.flightPhase
            === "WAITING"
          ||
          aircraft.flightPhase
            === "ARRIVED"
        ) {

          visual.aircraftElement
            .style.opacity =
            "0.45";

        } else {

          visual.aircraftElement
            .style.opacity =
            "1";
        }

        visual.lastAircraft =
          aircraft;

        if (
          selectedCallsignRef.current
            === aircraft.callsign
        ) {

          updateDetailPanel(
            aircraft
          );
        }
      };

    // ------------------------------------------------
    // CONFLICTS
    // ------------------------------------------------

    const updateConflicts =
      (
        conflicts:
          Conflict[]
      ) => {

        currentConflicts =
          conflicts;

        conflictCallsigns =
          new Set<string>();

        conflicts.forEach(
          (
            conflict
          ) => {

            conflictCallsigns.add(
              conflict.aircraftOne
            );

            conflictCallsigns.add(
              conflict.aircraftTwo
            );
          }
        );

        updateAircraftStyles();

        updateAircraftVisibility();

        renderAlerts();
      };

    // ------------------------------------------------
    // PREDICTED CONFLICTS
    // ------------------------------------------------

    const updatePredictedConflicts =
      (
        conflicts:
          PredictedConflict[]
      ) => {

        currentPredictedConflicts =
          conflicts;

        predictedConflictCallsigns =
          new Set<string>();

        conflicts.forEach(
          (
            conflict
          ) => {

            predictedConflictCallsigns.add(
              conflict.aircraftOne
            );

            predictedConflictCallsigns.add(
              conflict.aircraftTwo
            );
          }
        );

        updateAircraftStyles();

        updateAircraftVisibility();

        renderAlerts();
      };

    // ------------------------------------------------
    // RESOLUTIONS
    // ------------------------------------------------

    const updateReroutes =
      (
        reroutes:
          Reroute[]
      ) => {

        currentReroutes =
          reroutes;

        renderAlerts();
      };

    // ------------------------------------------------
    // PATH TOGGLE
    // ------------------------------------------------

    pathToggle.onclick =
      () => {

        const nextValue =
          !showAllPathsRef.current;

        showAllPathsRef.current =
          nextValue;

        onShowAllPathsChange(
          nextValue
        );

        updateToggleText();

        updateTrailVisibility();

        scheduleTrailRedraw();
      };

    // ------------------------------------------------
    // WEBSOCKET
    // ------------------------------------------------

    const client =
      new Client({

        brokerURL:
          `${WS_BASE_URL}/ws`,

        reconnectDelay:
          5000,

        onConnect:
          () => {

            console.log(
              "Connected to AirRoute WebSocket"
            );

            client.subscribe(
              "/topic/aircraft",

              (
                message
              ) => {

                const aircraftList:
                  Aircraft[] =
                  JSON.parse(
                    message.body
                  );

                aircraftList.forEach(
                  (
                    aircraft
                  ) => {

                    updateAircraft(
                      aircraft
                    );
                  }
                );

                updateAircraftStyles();

                updateAircraftVisibility();

                scheduleTrailRedraw();
              }
            );

            client.subscribe(
              "/topic/conflicts",

              (
                message
              ) => {

                updateConflicts(
                  JSON.parse(
                    message.body
                  )
                );
              }
            );

            client.subscribe(
              "/topic/predicted-conflicts",

              (
                message
              ) => {

                updatePredictedConflicts(
                  JSON.parse(
                    message.body
                  )
                );
              }
            );

            client.subscribe(
              "/topic/reroutes",

              (
                message
              ) => {

                updateReroutes(
                  JSON.parse(
                    message.body
                  )
                );
              }
            );
          },
      });

    client.activate();

    // ------------------------------------------------
    // MAP EVENTS
    // ------------------------------------------------

    const handleMapMovement =
      () => {

        scheduleTrailRedraw();
      };

    map.on(
      "move",
      handleMapMovement
    );

    map.on(
      "zoom",
      handleMapMovement
    );

    map.on(
      "resize",
      handleMapMovement
    );

    // ------------------------------------------------
    // CLEANUP
    // ------------------------------------------------

    return () => {

      client.deactivate();

      if (
        redrawFrame !== null
      ) {

        cancelAnimationFrame(
          redrawFrame
        );
      }

      aircraftVisualsRef.current
        .forEach(
          (
            visual
          ) => {

            visual.marker.remove();

            visual.trailPath.remove();

            visual.glowPath.remove();
          }
        );

      aircraftVisualsRef.current
        .clear();

      map.off(
        "move",
        handleMapMovement
      );

      map.off(
        "zoom",
        handleMapMovement
      );

      map.off(
        "resize",
        handleMapMovement
      );

      alertPanel.remove();

      detailPanel.remove();

      searchContainer.remove();

      pathToggle.remove();

      filterPanel.remove();

      legend.remove();

      trailSvg.remove();
    };

  }, [
    map,
    onShowAllPathsChange,
  ]);

  return null;
}