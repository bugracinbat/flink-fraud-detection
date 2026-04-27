import React, { useMemo, useState } from "react";
import { createRoot } from "react-dom/client";
import {
  Activity,
  Aperture,
  CalendarClock,
  Check,
  ChevronLeft,
  ChevronRight,
  Clock3,
  CloudMoon,
  Crosshair,
  Eye,
  FastForward,
  Focus,
  Gauge,
  Layers3,
  LocateFixed,
  MapPin,
  Moon,
  Pause,
  Play,
  Plus,
  Radio,
  RotateCcw,
  Satellite,
  Search,
  Sparkles,
  Star,
  Telescope,
  TimerReset,
  X,
} from "lucide-react";
import "./styles.css";

const targets = [
  {
    id: "mercury",
    name: "Mercury",
    short: "Me",
    color: "#d8c69b",
    orbit: 76,
    period: 88,
    mag: "-0.2",
    distance: "0.93 AU",
    phase: "71%",
    visibility: "Fair",
    ra: "03h 18m",
    dec: "+17 deg 42m",
    constellation: "Aries",
    altitude: "14 deg",
    azimuth: "284 deg",
    bestTime: "19:18",
    seeing: "6.2/10",
    transparency: "82%",
    moonSeparation: "54 deg",
    exposure: "1/250s",
    cadence: "15 min",
    score: 68,
    timeline: [28, 35, 22, 12, 8, 5],
    events: [
      { time: "18:54", title: "Low western window opens", meta: "Altitude 11.2 deg", tone: "warn" },
      { time: "19:18", title: "Best Mercury contrast", meta: "Solar depression 11 deg", tone: "best" },
      { time: "20:02", title: "Horizon haze risk", meta: "Airmass 3.1", tone: "warn" },
    ],
  },
  {
    id: "venus",
    name: "Venus",
    short: "Ve",
    color: "#e8d9a8",
    orbit: 108,
    period: 225,
    mag: "-4.1",
    distance: "1.24 AU",
    phase: "54%",
    visibility: "Excellent",
    ra: "04h 44m",
    dec: "+21 deg 06m",
    constellation: "Taurus",
    altitude: "26 deg",
    azimuth: "271 deg",
    bestTime: "20:04",
    seeing: "7.1/10",
    transparency: "89%",
    moonSeparation: "61 deg",
    exposure: "1/1000s",
    cadence: "10 min",
    score: 92,
    timeline: [54, 72, 63, 34, 18, 9],
    events: [
      { time: "19:36", title: "Venus enters clear western lane", meta: "Cloud model 4%", tone: "good" },
      { time: "20:04", title: "Peak brilliance window", meta: "Magnitude -4.1", tone: "best" },
      { time: "21:22", title: "Atmospheric shimmer increases", meta: "Seeing drops 0.8", tone: "warn" },
    ],
  },
  {
    id: "earth",
    name: "Earth",
    short: "Ea",
    color: "#6fb7ff",
    orbit: 145,
    period: 365,
    mag: "-",
    distance: "0.00 AU",
    phase: "100%",
    visibility: "Reference",
    ra: "Local",
    dec: "Zenith",
    constellation: "Horizon grid",
    altitude: "90 deg",
    azimuth: "000 deg",
    bestTime: "Now",
    seeing: "7.8/10",
    transparency: "91%",
    moonSeparation: "N/A",
    exposure: "N/A",
    cadence: "Live",
    score: 84,
    timeline: [48, 66, 72, 61, 52, 44],
    events: [
      { time: "20:30", title: "Local sky model synchronized", meta: "UTC+3 observatory frame", tone: "best" },
      { time: "22:00", title: "Transparency plateau", meta: "Water vapor stable", tone: "good" },
      { time: "03:40", title: "Pre-dawn seeing improves", meta: "Jet stream edge passes", tone: "good" },
    ],
  },
  {
    id: "mars",
    name: "Mars",
    short: "Ma",
    color: "#ff8b64",
    orbit: 190,
    period: 687,
    mag: "-1.3",
    distance: "0.62 AU",
    phase: "86%",
    visibility: "Good",
    ra: "05h 32m",
    dec: "+23 deg 11m",
    constellation: "Gemini",
    altitude: "38 deg",
    azimuth: "112 deg",
    bestTime: "21:08",
    seeing: "7.4/10",
    transparency: "88%",
    moonSeparation: "83 deg",
    exposure: "8 ms",
    cadence: "6 min",
    score: 84,
    timeline: [42, 68, 61, 45, 31, 48],
    events: [
      { time: "19:42", title: "Mars clears eastern ridge", meta: "Altitude 18.4 deg", tone: "good" },
      { time: "21:08", title: "Best seeing window", meta: "Cloud model 7%", tone: "best" },
      { time: "23:31", title: "Moon separation widens", meta: "83 deg from target", tone: "good" },
      { time: "02:16", title: "Magnitude begins fading", meta: "Delta +0.07", tone: "warn" },
    ],
  },
  {
    id: "jupiter",
    name: "Jupiter",
    short: "Ju",
    color: "#f1c08a",
    orbit: 242,
    period: 4333,
    mag: "-2.5",
    distance: "4.18 AU",
    phase: "99%",
    visibility: "Excellent",
    ra: "07h 03m",
    dec: "+22 deg 58m",
    constellation: "Cancer",
    altitude: "51 deg",
    azimuth: "151 deg",
    bestTime: "22:44",
    seeing: "8.1/10",
    transparency: "90%",
    moonSeparation: "97 deg",
    exposure: "14 ms",
    cadence: "4 min",
    score: 91,
    timeline: [36, 58, 78, 74, 52, 31],
    events: [
      { time: "20:26", title: "Great Red Spot rotation begins", meta: "Central meridian in 2h 18m", tone: "good" },
      { time: "22:44", title: "Best Jovian detail window", meta: "Seeing 8.1/10", tone: "best" },
      { time: "00:12", title: "Io shadow transit", meta: "High contrast expected", tone: "good" },
      { time: "03:04", title: "Altitude drops below 30 deg", meta: "Airmass rising", tone: "warn" },
    ],
  },
  {
    id: "saturn",
    name: "Saturn",
    short: "Sa",
    color: "#e7d48a",
    orbit: 292,
    period: 10759,
    mag: "0.6",
    distance: "8.91 AU",
    phase: "100%",
    visibility: "Good",
    ra: "23h 12m",
    dec: "-07 deg 24m",
    constellation: "Aquarius",
    altitude: "34 deg",
    azimuth: "209 deg",
    bestTime: "01:38",
    seeing: "7.0/10",
    transparency: "86%",
    moonSeparation: "72 deg",
    exposure: "24 ms",
    cadence: "8 min",
    score: 79,
    timeline: [12, 22, 44, 64, 59, 41],
    events: [
      { time: "23:10", title: "Saturn clears urban light shelf", meta: "Altitude 22.8 deg", tone: "good" },
      { time: "01:38", title: "Ring plane steadies", meta: "Seeing 7.0/10", tone: "best" },
      { time: "03:22", title: "Transparency softens", meta: "Humidity +9%", tone: "warn" },
    ],
  },
];

const presets = ["Tonight", "Opposition", "Transit", "Retrograde"];
const viewModes = ["Solar", "Sky", "Telescope"];
const layerLabels = {
  orbits: "Orbital paths",
  grid: "Coordinate grid",
  constellations: "Constellations",
  fov: "Field overlay",
};

function polar(cx, cy, radius, angle) {
  const rad = (angle - 90) * (Math.PI / 180);
  return { x: cx + radius * Math.cos(rad), y: cy + radius * Math.sin(rad) };
}

function getTarget(id) {
  return targets.find((item) => item.id === id) ?? targets[3];
}

function OrbitCanvas({ selected, day, layers, speed, viewMode, onSearch, onRecenter }) {
  const target = getTarget(selected);
  const planets = useMemo(() => {
    return targets.map((planet, index) => {
      const angle = ((day * 365 * speed) / planet.period) * 360 + index * 38;
      return { ...planet, angle, pos: polar(360, 310, planet.orbit, angle) };
    });
  }, [day, speed]);
  const activePlanet = planets.find((planet) => planet.id === selected) ?? planets[3];
  const nextPoint = polar(360, 310, activePlanet.orbit, activePlanet.angle + 34);
  const previousPoint = polar(360, 310, activePlanet.orbit, activePlanet.angle - 22);
  const showConstellations = layers.constellations || viewMode === "Sky";
  const showFov = layers.fov || viewMode === "Telescope";

  return (
    <section className={`orbital-stage mode-${viewMode.toLowerCase()}`} aria-label="Interactive orbital visualizer">
      <div className="stage-header">
        <div>
          <p className="eyebrow">Live ephemeris model</p>
          <h1>ORRERY LAB</h1>
        </div>
        <div className="stage-actions">
          <button onClick={onSearch}><Search size={16} /> Search sky</button>
          <button className="icon-btn" aria-label="Recenter view" onClick={onRecenter}><LocateFixed size={18} /></button>
        </div>
      </div>

      <svg className="orbit-map" viewBox="0 0 720 620" role="img" aria-label={`Orbital map focused on ${target.name}`}>
        <defs>
          <radialGradient id="sunGlow" cx="50%" cy="50%" r="50%">
            <stop offset="0%" stopColor="#fff1ad" stopOpacity="1" />
            <stop offset="52%" stopColor="#e99b48" stopOpacity=".42" />
            <stop offset="100%" stopColor="#e99b48" stopOpacity="0" />
          </radialGradient>
          <filter id="softGlow">
            <feGaussianBlur stdDeviation="4" result="blur" />
            <feMerge>
              <feMergeNode in="blur" />
              <feMergeNode in="SourceGraphic" />
            </feMerge>
          </filter>
        </defs>

        {layers.grid && (
          <g className="grid-lines">
            {Array.from({ length: 10 }, (_, index) => (
              <line key={`v-${index}`} x1={80 + index * 62} x2={80 + index * 62} y1="40" y2="574" />
            ))}
            {Array.from({ length: 8 }, (_, index) => (
              <line key={`h-${index}`} x1="60" x2="660" y1={72 + index * 64} y2={72 + index * 64} />
            ))}
          </g>
        )}

        {viewMode === "Sky" && (
          <g className="sky-overlay">
            <path d="M80 470 C210 390 520 390 650 470" />
            <text x="88" y="492">W</text>
            <text x="350" y="425">ALT 45</text>
            <text x="624" y="492">E</text>
          </g>
        )}

        {layers.orbits && planets.map((planet) => (
          <ellipse
            key={planet.id}
            className={planet.id === selected ? "orbit active" : "orbit"}
            cx="360"
            cy="310"
            rx={planet.orbit}
            ry={planet.orbit * 0.62}
          />
        ))}

        <path
          className="trajectory"
          d={`M ${previousPoint.x} ${previousPoint.y * 0.62 + 117.8} Q ${activePlanet.pos.x} ${activePlanet.pos.y * 0.62 + 117.8} ${nextPoint.x} ${nextPoint.y * 0.62 + 117.8}`}
        />
        <circle cx="360" cy="310" r="54" fill="url(#sunGlow)" />
        <circle cx="360" cy="310" r="16" className="sun-core" />

        {planets.map((planet) => {
          const y = planet.pos.y * 0.62 + 117.8;
          const active = planet.id === selected;
          return (
            <g key={planet.id} className={active ? "planet selected" : "planet"}>
              {active && <circle cx={planet.pos.x} cy={y} r="24" fill={planet.color} opacity=".12" />}
              <circle cx={planet.pos.x} cy={y} r={active ? 10 : 6} fill={planet.color} filter={active ? "url(#softGlow)" : ""} />
              {active && viewMode === "Telescope" && <circle cx={planet.pos.x} cy={y} r="38" className="guide-ring" />}
              <text x={planet.pos.x + 14} y={y + 4}>{planet.short}</text>
            </g>
          );
        })}

        {showConstellations && (
          <g className="constellation">
            <path d="M96 108 L168 84 L224 136 L294 100" />
            <path d="M538 454 L584 400 L636 430 L656 372" />
            <circle cx="96" cy="108" r="2" />
            <circle cx="168" cy="84" r="2" />
            <circle cx="224" cy="136" r="2" />
            <circle cx="294" cy="100" r="2" />
            <circle cx="538" cy="454" r="2" />
            <circle cx="584" cy="400" r="2" />
            <circle cx="636" cy="430" r="2" />
            <circle cx="656" cy="372" r="2" />
          </g>
        )}

        {showFov && (
          <g className="fov-overlay">
            <circle cx="360" cy="310" r="118" />
            <circle cx="360" cy="310" r="62" />
            <line x1="242" x2="478" y1="310" y2="310" />
            <line x1="360" x2="360" y1="192" y2="428" />
            <text x="384" y="205">0.74 deg FOV</text>
          </g>
        )}
      </svg>

      <div className="stage-readout">
        <span><Focus size={15} /> Target lock: {target.name}</span>
        <span>RA {target.ra}</span>
        <span>DEC {target.dec}</span>
        <span>ALT {target.altitude}</span>
      </div>
    </section>
  );
}

function TargetRail({ selected, setSelected, preset, setPreset }) {
  return (
    <aside className="target-rail" aria-label="Target selection">
      <div className="brand">
        <Aperture size={26} />
        <span>ORRERY<br />LAB</span>
      </div>
      <nav className="target-list">
        {targets.map((target) => (
          <button
            key={target.id}
            className={selected === target.id ? "target active" : "target"}
            onClick={() => setSelected(target.id)}
            aria-pressed={selected === target.id}
            style={{ "--planet": target.color }}
          >
            <span className="target-dot" />
            <span>{target.name}</span>
          </button>
        ))}
      </nav>
      <div className="preset-box">
        <p>Preset</p>
        {presets.map((item) => (
          <button key={item} className={preset === item ? "chip active" : "chip"} aria-pressed={preset === item} onClick={() => setPreset(item)}>{item}</button>
        ))}
      </div>
    </aside>
  );
}

function Inspector({ selected, viewMode, setViewMode, layers, setLayers, planItems, onAddPlan }) {
  const target = getTarget(selected);
  const inPlan = planItems.includes(selected);
  return (
    <aside className="inspector" aria-label="Observation inspector">
      <div className="panel-title">
        <div>
          <p className="eyebrow">Observation target</p>
          <h2>{target.name}</h2>
        </div>
        <Telescope size={22} />
      </div>

      <div className="mode-toggle" aria-label="View mode">
        {viewModes.map((mode) => (
          <button key={mode} className={viewMode === mode ? "active" : ""} aria-pressed={viewMode === mode} onClick={() => setViewMode(mode)}>{mode}</button>
        ))}
      </div>

      <div className="metric-grid">
        <Metric icon={<Radio size={17} />} label="Distance" value={target.distance} />
        <Metric icon={<Sparkles size={17} />} label="Magnitude" value={target.mag} />
        <Metric icon={<Moon size={17} />} label="Phase" value={target.phase} />
        <Metric icon={<Eye size={17} />} label="Visibility" value={target.visibility} />
      </div>

      <div className="quality-strip" aria-label="Forecast quality">
        <Quality icon={<CloudMoon size={16} />} label="Seeing" value={target.seeing} />
        <Quality icon={<Star size={16} />} label="Transparency" value={target.transparency} />
        <Quality icon={<Crosshair size={16} />} label="Best" value={target.bestTime} />
      </div>

      <div className="plan-card">
        <div>
          <p className="eyebrow">Session recipe</p>
          <strong>{target.constellation} / {target.azimuth}</strong>
          <span>{target.exposure} exposure, {target.cadence} cadence</span>
        </div>
        <button onClick={onAddPlan} className={inPlan ? "plan-action saved" : "plan-action"}>
          {inPlan ? <Check size={16} /> : <Plus size={16} />}
          {inPlan ? "Planned" : "Add"}
        </button>
      </div>

      <div className="layer-box">
        <div className="row-title"><Layers3 size={17} /> Layers</div>
        {Object.entries(layers).map(([key, enabled]) => (
          <label className="toggle-row" key={key}>
            <span>{layerLabels[key]}</span>
            <input
              type="checkbox"
              checked={enabled}
              onChange={() => setLayers((current) => ({ ...current, [key]: !current[key] }))}
            />
          </label>
        ))}
      </div>

      <div className="events">
        <div className="row-title"><CalendarClock size={17} /> Upcoming windows</div>
        {target.events.map((event) => (
          <article className={`event ${event.tone}`} key={`${target.id}-${event.time}`}>
            <time>{event.time}</time>
            <div>
              <h3>{event.title}</h3>
              <p>{event.meta}</p>
            </div>
          </article>
        ))}
      </div>
    </aside>
  );
}

function Metric({ icon, label, value }) {
  return (
    <div className="metric">
      <span>{icon}</span>
      <p>{label}</p>
      <strong>{value}</strong>
    </div>
  );
}

function Quality({ icon, label, value }) {
  return (
    <div className="quality">
      {icon}
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  );
}

function TimeDeck({ day, setDay, playing, setPlaying, speed, setSpeed }) {
  const date = useMemo(() => {
    const base = new Date("2026-04-27T20:30:00");
    base.setDate(base.getDate() + Math.round(day * 32));
    return base.toLocaleDateString("en-US", { month: "short", day: "numeric", year: "numeric" });
  }, [day]);

  return (
    <footer className="time-deck">
      <div className="transport">
        <button className="icon-btn" aria-label="Previous step" onClick={() => setDay(Math.max(0, day - 0.03))}><ChevronLeft size={18} /></button>
        <button className="play" aria-label={playing ? "Pause model" : "Play model"} onClick={() => setPlaying(!playing)}>{playing ? <Pause size={18} /> : <Play size={18} />}</button>
        <button className="icon-btn" aria-label="Next step" onClick={() => setDay(Math.min(1, day + 0.03))}><ChevronRight size={18} /></button>
        <button className="icon-btn" aria-label="Reset time" onClick={() => setDay(0.46)}><RotateCcw size={18} /></button>
      </div>
      <div className="scrubber">
        <div className="time-labels">
          <span><Clock3 size={15} /> {date}</span>
          <span>20:30 UTC+3</span>
        </div>
        <input
          aria-label="Time scrubber"
          type="range"
          min="0"
          max="1"
          step="0.001"
          value={day}
          onChange={(event) => setDay(Number(event.target.value))}
        />
      </div>
      <div className="speed-control">
        <FastForward size={16} />
        <select aria-label="Playback speed" value={speed} onChange={(event) => setSpeed(Number(event.target.value))}>
          <option value="0.5">0.5x</option>
          <option value="1">1x</option>
          <option value="2">2x</option>
          <option value="4">4x</option>
        </select>
      </div>
    </footer>
  );
}

function VisibilityTimeline({ selected }) {
  const target = getTarget(selected);
  return (
    <section className="timeline-panel" aria-label="Visibility timeline">
      <div className="timeline-heading">
        <div>
          <p className="eyebrow">Tonight from Istanbul</p>
          <h2>{target.name} visibility timeline</h2>
        </div>
        <div className="score"><Gauge size={18} /> {target.score} observing score</div>
      </div>
      <div className="timeline-grid">
        {["18:00", "20:00", "22:00", "00:00", "02:00", "04:00"].map((time, index) => (
          <div key={time} className="timeline-column">
            <span>{time}</span>
            <div className="bar" style={{ height: `${target.timeline[index]}px` }} />
          </div>
        ))}
      </div>
    </section>
  );
}

function SearchPanel({ open, query, setQuery, selected, onSelect, onClose }) {
  const filteredTargets = targets.filter((target) => {
    const text = `${target.name} ${target.constellation} ${target.visibility}`.toLowerCase();
    return text.includes(query.toLowerCase());
  });
  if (!open) return null;

  return (
    <div className="modal-backdrop" role="presentation">
      <section className="search-panel" role="dialog" aria-modal="true" aria-label="Search sky targets">
        <div className="search-top">
          <div>
            <p className="eyebrow">Command search</p>
            <h2>Search sky</h2>
          </div>
          <button className="icon-btn" aria-label="Close search" onClick={onClose}><X size={18} /></button>
        </div>
        <label className="search-field">
          <Search size={18} />
          <input autoFocus value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Planet, constellation, visibility..." />
        </label>
        <div className="search-results">
          {filteredTargets.map((target) => (
            <button key={target.id} className={target.id === selected ? "search-result active" : "search-result"} onClick={() => onSelect(target.id)} style={{ "--planet": target.color }}>
              <span className="target-dot" />
              <strong>{target.name}</strong>
              <span>{target.constellation}</span>
              <em>{target.visibility}</em>
            </button>
          ))}
        </div>
      </section>
    </div>
  );
}

function App() {
  const [selected, setSelected] = useState("mars");
  const [preset, setPreset] = useState("Tonight");
  const [viewMode, setViewMode] = useState("Solar");
  const [day, setDay] = useState(0.46);
  const [playing, setPlaying] = useState(false);
  const [speed, setSpeed] = useState(1);
  const [layers, setLayers] = useState({ orbits: true, grid: true, constellations: true, fov: false });
  const [searchOpen, setSearchOpen] = useState(false);
  const [query, setQuery] = useState("");
  const [planItems, setPlanItems] = useState(["mars"]);
  const [toast, setToast] = useState("");
  const target = getTarget(selected);

  React.useEffect(() => {
    if (!playing) return;
    const timer = window.setInterval(() => {
      setDay((current) => (current >= 1 ? 0 : current + 0.002 * speed));
    }, 80);
    return () => window.clearInterval(timer);
  }, [playing, speed]);

  React.useEffect(() => {
    if (!toast) return;
    const timer = window.setTimeout(() => setToast(""), 1800);
    return () => window.clearTimeout(timer);
  }, [toast]);

  function selectTarget(id) {
    setSelected(id);
    setSearchOpen(false);
    setQuery("");
  }

  function recenter() {
    setDay(0.46);
    setViewMode("Solar");
    setLayers({ orbits: true, grid: true, constellations: true, fov: false });
    setToast("View recentered on tonight's model");
  }

  function addPlanItem() {
    setPlanItems((current) => current.includes(selected) ? current : [...current, selected]);
    setToast(`${target.name} added to observing plan`);
  }

  return (
    <main className="app-shell">
      <TargetRail selected={selected} setSelected={setSelected} preset={preset} setPreset={setPreset} />
      <div className="workspace">
        <div className="topbar">
          <div className="location-pill"><MapPin size={16} /> Istanbul / 41.01 N, 28.97 E</div>
          <div className="status-pill"><Satellite size={16} /> Pro forecast synced</div>
          <div className="status-pill"><Activity size={16} /> {preset} model</div>
          <div className="status-pill"><TimerReset size={16} /> {target.cadence} cadence</div>
        </div>
        <div className="visual-grid">
          <OrbitCanvas
            selected={selected}
            day={day}
            layers={layers}
            speed={speed}
            viewMode={viewMode}
            onSearch={() => setSearchOpen(true)}
            onRecenter={recenter}
          />
          <Inspector
            selected={selected}
            viewMode={viewMode}
            setViewMode={setViewMode}
            layers={layers}
            setLayers={setLayers}
            planItems={planItems}
            onAddPlan={addPlanItem}
          />
        </div>
        <TimeDeck day={day} setDay={setDay} playing={playing} setPlaying={setPlaying} speed={speed} setSpeed={setSpeed} />
        <VisibilityTimeline selected={selected} />
      </div>
      <SearchPanel open={searchOpen} query={query} setQuery={setQuery} selected={selected} onSelect={selectTarget} onClose={() => setSearchOpen(false)} />
      {toast && <div className="toast" role="status">{toast}</div>}
    </main>
  );
}

createRoot(document.getElementById("root")).render(<App />);
