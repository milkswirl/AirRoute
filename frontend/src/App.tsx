import "./App.css";
import AirRouteMap from "./components/AirRouteMap";

function App() {
  return (
    <div className="app">
      <header className="header">
        <div>
          <h1>AirRoute</h1>
          <p>Air Traffic Routing & Conflict Simulation</p>
        </div>

        <div className="status">
          <span className="status-dot"></span>
          Simulation Online
        </div>
      </header>

      <main>
        <AirRouteMap />
      </main>
    </div>
  );
}

export default App;