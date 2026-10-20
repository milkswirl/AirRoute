import {
  useEffect,
  useMemo,
  useState,
} from "react";

import { Client } from "@stomp/stompjs";

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

type SimulationDashboardProps = {
  weatherCount: number;
  speedMultiplier: number;
};

export default function SimulationDashboard({
  weatherCount,
  speedMultiplier,
}: SimulationDashboardProps) {

  const [
    aircraft,
    setAircraft,
  ] =
    useState<Aircraft[]>([]);

  const [
    conflicts,
    setConflicts,
  ] =
    useState<Conflict[]>([]);

  const [
    predictedConflicts,
    setPredictedConflicts,
  ] =
    useState<
      PredictedConflict[]
    >([]);

  const [
    reroutes,
    setReroutes,
  ] =
    useState<Reroute[]>([]);

  useEffect(() => {

    const client =
      new Client({

        brokerURL:
          "ws://localhost:8080/ws",

        reconnectDelay:
          5000,

        onConnect:
          () => {

            client.subscribe(
              "/topic/aircraft",
              (
                message
              ) => {

                setAircraft(
                  JSON.parse(
                    message.body
                  )
                );
              }
            );

            client.subscribe(
              "/topic/conflicts",
              (
                message
              ) => {

                setConflicts(
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

                setPredictedConflicts(
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

                setReroutes(
                  JSON.parse(
                    message.body
                  )
                );
              }
            );
          },
      });

    client.activate();

    return () => {

      client.deactivate();
    };

  }, []);

  const stats =
    useMemo(
      () => {

        const waiting =
          aircraft.filter(
            (
              plane
            ) =>
              plane.flightPhase
                === "WAITING"
          ).length;

        const climbing =
          aircraft.filter(
            (
              plane
            ) =>
              plane.flightPhase
                === "CLIMBING"
          ).length;

        const enRoute =
          aircraft.filter(
            (
              plane
            ) =>
              plane.flightPhase
                === "EN_ROUTE"
          ).length;

        const descending =
          aircraft.filter(
            (
              plane
            ) =>
              plane.flightPhase
                === "DESCENDING"
          ).length;

        const arrived =
          aircraft.filter(
            (
              plane
            ) =>
              plane.flightPhase
                === "ARRIVED"
          ).length;

        const airborne =
          climbing
          + enRoute
          + descending;

        const airborneAircraft =
          aircraft.filter(
            (
              plane
            ) =>
              plane.flightPhase
                === "CLIMBING"
              ||
              plane.flightPhase
                === "EN_ROUTE"
              ||
              plane.flightPhase
                === "DESCENDING"
          );

        const averageAltitude =
          airborneAircraft.length
            === 0
            ? 0
            :
            airborneAircraft.reduce(
              (
                total,
                plane
              ) =>
                total
                + plane.altitude,
              0
            )
            /
            airborneAircraft.length;

        const averageSpeed =
          airborneAircraft.length
            === 0
            ? 0
            :
            airborneAircraft.reduce(
              (
                total,
                plane
              ) =>
                total
                + plane.speed,
              0
            )
            /
            airborneAircraft.length;

        return {
          total:
            aircraft.length,

          waiting,

          climbing,

          enRoute,

          descending,

          arrived,

          airborne,

          averageAltitude,

          averageSpeed,
        };
      },

      [
        aircraft,
      ]
    );

  const cardStyle:
    React.CSSProperties = {

    background:
      "#0f172a",

    border:
      "1px solid #334155",

    borderRadius:
      "10px",

    padding:
      "13px 15px",

    minWidth:
      "125px",

    flex:
      "1 1 125px",

    boxShadow:
      "0 4px 12px rgba(0,0,0,0.18)",
  };

  const labelStyle:
    React.CSSProperties = {

    color:
      "#94a3b8",

    fontSize:
      "11px",

    fontWeight:
      "700",

    letterSpacing:
      "0.8px",

    textTransform:
      "uppercase",

    marginBottom:
      "5px",
  };

  const valueStyle:
    React.CSSProperties = {

    color:
      "#ffffff",

    fontSize:
      "23px",

    fontWeight:
      "800",

    lineHeight:
      "1.1",
  };

  const secondaryStyle:
    React.CSSProperties = {

    color:
      "#94a3b8",

    fontSize:
      "11px",

    marginTop:
      "4px",
  };

  return (

    <div
      style={{
        marginBottom:
          "16px",
      }}
    >

      <div
        style={{
          display:
            "flex",

          alignItems:
            "center",

          justifyContent:
            "space-between",

          marginBottom:
            "9px",

          gap:
            "15px",

          flexWrap:
            "wrap",
        }}
      >

        <div>

          <div
            style={{
              color:
                "#ffffff",

              fontSize:
                "16px",

              fontWeight:
                "800",
            }}
          >
            Live Simulation Statistics
          </div>

          <div
            style={{
              color:
                "#64748b",

              fontSize:
                "12px",

              marginTop:
                "2px",
            }}
          >
            Real-time nationwide airspace activity
          </div>

        </div>

        <div
          style={{
            color:
              "#94a3b8",

            fontSize:
              "12px",

            fontWeight:
              "600",
          }}
        >
          Simulation speed:{" "}

          <span
            style={{
              color:
                "#38bdf8",
            }}
          >
            {speedMultiplier}x
          </span>
        </div>

      </div>

      <div
        style={{
          display:
            "flex",

          gap:
            "10px",

          flexWrap:
            "wrap",
        }}
      >

        <div style={cardStyle}>
          <div style={labelStyle}>
            Aircraft
          </div>

          <div style={valueStyle}>
            {stats.total}
          </div>

          <div style={secondaryStyle}>
            {stats.airborne} airborne
          </div>
        </div>

        <div style={cardStyle}>
          <div style={labelStyle}>
            En Route
          </div>

          <div style={valueStyle}>
            {stats.enRoute}
          </div>

          <div style={secondaryStyle}>
            {stats.climbing} climbing · {stats.descending} descending
          </div>
        </div>

        <div style={cardStyle}>
          <div style={labelStyle}>
            Ground
          </div>

          <div style={valueStyle}>
            {stats.waiting}
          </div>

          <div style={secondaryStyle}>
            {stats.arrived} arrived
          </div>
        </div>

        <div
          style={{
            ...cardStyle,

            border:
              conflicts.length > 0
                ? "1px solid #ef4444"
                : "1px solid #334155",
          }}
        >
          <div style={labelStyle}>
            Conflicts
          </div>

          <div
            style={{
              ...valueStyle,

              color:
                conflicts.length > 0
                  ? "#ef4444"
                  : "#ffffff",
            }}
          >
            {conflicts.length}
          </div>

          <div style={secondaryStyle}>
            active
          </div>
        </div>

        <div
          style={{
            ...cardStyle,

            border:
              predictedConflicts.length > 0
                ? "1px solid #f59e0b"
                : "1px solid #334155",
          }}
        >
          <div style={labelStyle}>
            Predicted
          </div>

          <div
            style={{
              ...valueStyle,

              color:
                predictedConflicts.length > 0
                  ? "#f59e0b"
                  : "#ffffff",
            }}
          >
            {predictedConflicts.length}
          </div>

          <div style={secondaryStyle}>
            potential conflicts
          </div>
        </div>

        <div style={cardStyle}>
          <div style={labelStyle}>
            Resolutions
          </div>

          <div style={valueStyle}>
            {reroutes.length}
          </div>

          <div style={secondaryStyle}>
            recent events
          </div>
        </div>

        <div style={cardStyle}>
          <div style={labelStyle}>
            Weather
          </div>

          <div style={valueStyle}>
            {weatherCount}
          </div>

          <div style={secondaryStyle}>
            active hazards
          </div>
        </div>

        <div style={cardStyle}>
          <div style={labelStyle}>
            Avg Altitude
          </div>

          <div style={valueStyle}>
            {
              Math.round(
                stats.averageAltitude
                / 100
              )
              * 100
            }
          </div>

          <div style={secondaryStyle}>
            feet
          </div>
        </div>

        <div style={cardStyle}>
          <div style={labelStyle}>
            Avg Speed
          </div>

          <div style={valueStyle}>
            {
              Math.round(
                stats.averageSpeed
              )
            }
          </div>

          <div style={secondaryStyle}>
            knots
          </div>
        </div>

      </div>

    </div>
  );
}