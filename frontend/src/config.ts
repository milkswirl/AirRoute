const isLocalDevelopment =
  window.location.hostname === "localhost"
  || window.location.hostname === "127.0.0.1";

export const API_BASE_URL =
  isLocalDevelopment
    ? "http://localhost:8080"
    : "https://airroute-backend-8t1h.onrender.com";

export const WS_BASE_URL =
  isLocalDevelopment
    ? "ws://localhost:8080"
    : "wss://airroute-backend-8t1h.onrender.com";
