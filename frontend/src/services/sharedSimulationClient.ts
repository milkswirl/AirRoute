import { Client, type IMessage } from "@stomp/stompjs";
import { WS_BASE_URL } from "../config";

type Handler = (message: IMessage) => void;
const topics = [
  "/topic/aircraft",
  "/topic/conflicts",
  "/topic/predicted-conflicts",
  "/topic/reroutes",
  "/topic/simulation-status",
];
const listeners = new Map<string, Set<Handler>>();
let consumers = 0;
let transition = Promise.resolve();

// One network subscription per topic. Components receive local copies.
const connection = new Client({
  brokerURL: `${WS_BASE_URL}/ws`,
  reconnectDelay: 5000,
  onConnect: () => {
    for (const topic of topics) {
      connection.subscribe(topic, (message) => {
        for (const handler of [...(listeners.get(topic) ?? [])]) {
          try {
            handler(message);
          } catch (error) {
            console.error(`AirRoute listener failed for ${topic}`, error);
          }
        }
      });
    }
  },
});

function reconcileConnection() {
  // Serialize shutdown/restart, including React StrictMode effect remounts.
  transition = transition.then(async () => {
    if (consumers > 0) {
      if (!connection.active) connection.activate();
    } else {
      await connection.deactivate();
    }
  }).catch((error: unknown) => {
    console.error("AirRoute WebSocket lifecycle failed", error);
  });
}

export class SharedSimulationClient {
  private active = false;
  private handlers: Array<{ topic: string; handler: Handler }> = [];
  private readonly onConnect: () => void;

  constructor(options: { onConnect: () => void }) {
    this.onConnect = options.onConnect;
  }

  subscribe(topic: string, handler: Handler) {
    if (!topics.includes(topic)) throw new Error(`Unknown topic: ${topic}`);
    let subscribers = listeners.get(topic);
    if (!subscribers) {
      subscribers = new Set();
      listeners.set(topic, subscribers);
    }
    subscribers.add(handler);
    this.handlers.push({ topic, handler });
  }

  activate() {
    if (this.active) return;
    this.active = true;
    consumers++;
    this.onConnect();
    reconcileConnection();
  }

  deactivate() {
    if (!this.active) return;
    this.active = false;
    for (const { topic, handler } of this.handlers) {
      listeners.get(topic)?.delete(handler);
    }
    this.handlers = [];
    consumers--;
    reconcileConnection();
  }
}
