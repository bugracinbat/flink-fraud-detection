import { useEffect, useMemo, useRef, useState } from 'react'
import { AlertCard } from './components/AlertCard'
import { SimulateButton } from './components/SimulateButton'

const API_BASE_URL = 'http://localhost:8080'

const scenarios = [
  {
    id: 'SIM_CLONE',
    title: 'SIM Clone Velocity',
    detector: 'VELOCITY_FRAUD_SIM_CLONE',
    accent: 'velocity',
    kind: 'Fraud',
    description: 'Same subscriber appears in Germany, then Turkiye five minutes later.',
  },
  {
    id: 'SEQUENTIAL_DIALING',
    title: 'Sequential Dialing',
    detector: 'SEQUENTIAL_DIALING',
    accent: 'sequential',
    kind: 'Fraud',
    description: 'One caller rapidly dials incrementing destination numbers.',
  },
  {
    id: 'STATICAL_RULE',
    title: 'Static Rule Anomaly',
    detector: 'STATICAL_RULE_FRAUD',
    accent: 'static',
    kind: 'Fraud',
    description: 'A fresh SIM with low data usage calls many distinct numbers.',
  },
  {
    id: 'CALL_FORWARDING',
    title: 'Forwarding Fan-out',
    detector: 'DISTANCE_FORWARDING_FRAUD',
    accent: 'forwarding',
    kind: 'Fraud',
    description: 'A callee redirects several short calls to different numbers.',
  },
  {
    id: 'NEAR_MISS_SIM_CLONE',
    title: 'Near Miss: Location',
    detector: null,
    accent: 'velocity',
    kind: 'Near miss',
    description: 'Repeated benign location pings that should stay quiet.',
  },
  {
    id: 'NEAR_MISS_SEQUENTIAL',
    title: 'Near Miss: Sequence',
    detector: null,
    accent: 'sequential',
    kind: 'Near miss',
    description: 'Only three sequential calls, below the alert threshold.',
  },
  {
    id: 'NEAR_MISS_STATICAL_RULE',
    title: 'Near Miss: Static Rule',
    detector: null,
    accent: 'static',
    kind: 'Near miss',
    description: 'Nine distinct callees, one short of the SQL rule.',
  },
  {
    id: 'NEAR_MISS_CALL_FORWARDING',
    title: 'Near Miss: Forwarding',
    detector: null,
    accent: 'forwarding',
    kind: 'Near miss',
    description: 'Three forwarded destinations, below the forwarding threshold.',
  },
]

const filters = [
  { id: 'ALL', label: 'All' },
  { id: 'VELOCITY_FRAUD_SIM_CLONE', label: 'SIM Clone Velocity' },
  { id: 'SEQUENTIAL_DIALING', label: 'Sequential Dialing' },
  { id: 'STATICAL_RULE_FRAUD', label: 'Static Rule Fraud' },
  { id: 'DISTANCE_FORWARDING_FRAUD', label: 'Forwarding Fan-out' },
]

const intensityOptions = [
  { id: 'LOW', label: 'Low', detail: 'Short demo run' },
  { id: 'MEDIUM', label: 'Medium', detail: 'More fraud plus noise' },
  { id: 'HIGH', label: 'High', detail: 'Heavy stream burst' },
]

function formatUptime(ms = 0) {
  const seconds = Math.floor(ms / 1000)
  const minutes = Math.floor(seconds / 60)
  const hours = Math.floor(minutes / 60)

  if (hours > 0) return `${hours}h ${minutes % 60}m`
  if (minutes > 0) return `${minutes}m ${seconds % 60}s`
  return `${seconds}s`
}

function verdictFor(run) {
  if (!run) return 'Pending'
  if (run.expectedAlerts === run.actualAlerts) return 'Matched'
  if (run.expectedAlerts === 0 && run.actualAlerts > 0) return 'False positive'
  if (run.expectedAlerts > 0 && run.actualAlerts === 0) return 'Missed'
  return run.alertDelta > 0 ? 'Extra alerts' : 'Under detected'
}

function App() {
  const [alerts, setAlerts] = useState([])
  const [connectionState, setConnectionState] = useState('connecting')
  const [serverStatus, setServerStatus] = useState(null)
  const [activeFilter, setActiveFilter] = useState('ALL')
  const [intensity, setIntensity] = useState('LOW')
  const [lastSimulation, setLastSimulation] = useState(null)
  const alertsEndRef = useRef(null)

  useEffect(() => {
    const loadStatus = async () => {
      try {
        const response = await fetch(`${API_BASE_URL}/api/status`)
        if (!response.ok) throw new Error(`Status check failed: ${response.status}`)
        setServerStatus(await response.json())
      } catch {
        setServerStatus(null)
        setConnectionState((current) => (current === 'connected' ? current : 'offline'))
      }
    }

    loadStatus()
    const timer = window.setInterval(loadStatus, 5000)
    return () => window.clearInterval(timer)
  }, [])

  useEffect(() => {
    const eventSource = new EventSource(`${API_BASE_URL}/api/alerts`)

    eventSource.onopen = () => setConnectionState('connected')
    eventSource.onmessage = (event) => {
      try {
        const newAlert = JSON.parse(event.data)
        setAlerts((prev) => [...prev.slice(-99), newAlert])
      } catch (error) {
        console.error('Failed to parse alert', error)
      }
    }

    eventSource.onerror = () => {
      setConnectionState('reconnecting')
    }

    return () => {
      eventSource.close()
    }
  }, [])

  useEffect(() => {
    if (alertsEndRef.current) {
      alertsEndRef.current.scrollIntoView({ behavior: 'smooth' })
    }
  }, [alerts])

  const alertCounts = useMemo(() => {
    return alerts.reduce((counts, alert) => {
      counts[alert.fraudType] = (counts[alert.fraudType] || 0) + 1
      return counts
    }, {})
  }, [alerts])

  const filteredAlerts = activeFilter === 'ALL'
    ? alerts
    : alerts.filter((alert) => alert.fraudType === activeFilter)

  const handleSimulate = async (scenarioId) => {
    const response = await fetch(`${API_BASE_URL}/api/simulate`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ scenario: scenarioId, intensity }),
    })

    const payload = await response.json()
    if (!response.ok) {
      throw new Error(payload.message || 'Simulation failed')
    }

    setLastSimulation({
      ...payload,
      triggeredAt: Date.now(),
    })
    setConnectionState('connected')
    return payload
  }

  const statusLabel = {
    connected: 'Connected',
    connecting: 'Connecting',
    reconnecting: 'Reconnecting',
    offline: 'Server offline',
  }[connectionState]

  const activeRuns = serverStatus?.activeRuns ?? []
  const completedRuns = serverStatus?.completedRuns ?? []
  const matchedRuns = completedRuns.filter((run) => run.expectedAlerts === run.actualAlerts).length
  const detectionScore = completedRuns.length > 0 ? Math.round((matchedRuns / completedRuns.length) * 100) : 0

  return (
    <>
      <header className="header">
        <div className="brand-lockup">
          <span className="brand-mark">V</span>
          <div>
            <h1>Vodafone Fraud Detection</h1>
            <p>Apache Flink simulation console</p>
          </div>
        </div>
        <div className={`status-badge ${connectionState}`}>
          <span className="status-dot" />
          {statusLabel}
        </div>
      </header>

      <main className="main-content">
        <section className="control-panel">
          <div className="panel-header">
            <h2>Simulation Scenarios</h2>
            <p>Inject fraud patterns into the streaming pipeline and watch detections land live.</p>
          </div>

          <div className="intensity-control" aria-label="Simulation intensity">
            {intensityOptions.map((option) => (
              <button
                key={option.id}
                type="button"
                className={intensity === option.id ? 'active' : ''}
                onClick={() => setIntensity(option.id)}
              >
                <strong>{option.label}</strong>
                <span>{option.detail}</span>
              </button>
            ))}
          </div>

          <div className="sim-buttons">
            {scenarios.map((scenario) => (
              <SimulateButton
                key={scenario.id}
                scenario={scenario}
                alertCount={scenario.detector ? alertCounts[scenario.detector] || 0 : 0}
                intensity={intensity}
                onSimulate={handleSimulate}
              />
            ))}
          </div>

          {lastSimulation && (
            <div className="simulation-receipt">
              <span>Last scheduled run</span>
              <strong>{lastSimulation.scenario.replaceAll('_', ' ')} · {lastSimulation.intensity}</strong>
              <p>
                {lastSimulation.totalEvents} events over {formatUptime(lastSimulation.durationMs)} · expected {lastSimulation.expectedAlerts}, actual {lastSimulation.actualAlerts}
              </p>
            </div>
          )}

          {activeRuns.length > 0 && (
            <div className="active-runs">
              <span>Active runs</span>
              {activeRuns.map((run) => (
                <div className="run-progress" key={run.runId}>
                  <div>
                    <strong>{run.scenario.replaceAll('_', ' ')}</strong>
                    <small>{run.emittedEvents}/{run.totalEvents} · {run.actualAlerts}/{run.expectedAlerts} alerts</small>
                  </div>
                  <progress max={run.totalEvents} value={run.emittedEvents} />
                </div>
              ))}
            </div>
          )}
        </section>

        <section className="feed-panel">
          <div className="metrics-grid">
            <div className="metric">
              <span>Total alerts</span>
              <strong>{alerts.length}</strong>
            </div>
            <div className="metric">
              <span>Run match rate</span>
              <strong>{completedRuns.length > 0 ? `${detectionScore}%` : '-'}</strong>
            </div>
            <div className="metric">
              <span>Simulations</span>
              <strong>{serverStatus?.simulationsTriggered ?? 0}</strong>
            </div>
            <div className="metric">
              <span>Queued events</span>
              <strong>{(serverStatus?.queuedCdrEvents ?? 0) + (serverStatus?.queuedLocationEvents ?? 0)}</strong>
            </div>
            <div className="metric">
              <span>Active runs</span>
              <strong>{activeRuns.length}</strong>
            </div>
            <div className="metric">
              <span>Server uptime</span>
              <strong>{serverStatus ? formatUptime(serverStatus.uptimeMs) : '-'}</strong>
            </div>
          </div>

          <div className="run-history">
            <div className="feed-toolbar compact">
              <div>
                <h2>Run Quality</h2>
                <p>{completedRuns.length} completed runs with expected vs actual alert counts</p>
              </div>
            </div>

            <div className="run-table">
              {completedRuns.length === 0 ? (
                <div className="run-row empty">Completed runs will appear here.</div>
              ) : (
                completedRuns.slice(0, 8).map((run) => (
                  <div className="run-row" data-verdict={verdictFor(run)} key={run.runId}>
                    <div>
                      <strong>{run.scenario.replaceAll('_', ' ')}</strong>
                      <span>{run.intensity} · {run.totalEvents} events · {formatUptime(run.durationMs)}</span>
                    </div>
                    <div className="run-alerts">
                      <span>{run.expectedAlerts} expected</span>
                      <strong>{run.actualAlerts} actual</strong>
                    </div>
                    <div className="run-verdict">{verdictFor(run)}</div>
                  </div>
                ))
              )}
            </div>
          </div>

          <div className="feed-toolbar">
            <div>
              <h2>Live Alert Feed</h2>
              <p>{filteredAlerts.length} visible alerts from the current session</p>
            </div>
            <button className="clear-btn" type="button" onClick={() => setAlerts([])} disabled={alerts.length === 0}>
              Clear
            </button>
          </div>

          <div className="filter-bar" role="tablist" aria-label="Alert filters">
            {filters.map((filter) => (
              <button
                key={filter.id}
                type="button"
                role="tab"
                className={activeFilter === filter.id ? 'active' : ''}
                onClick={() => setActiveFilter(filter.id)}
              >
                {filter.label}
              </button>
            ))}
          </div>

          <div className="alerts-container">
            {filteredAlerts.length === 0 ? (
              <div className="empty-state">
                <div className="empty-icon">!</div>
                <p>{alerts.length === 0 ? 'Waiting for fraud events...' : 'No alerts match this filter.'}</p>
              </div>
            ) : (
              filteredAlerts.map((alert, idx) => (
                <AlertCard key={`${alert.timestamp}-${alert.msisdn}-${idx}`} alert={alert} />
              ))
            )}
            <div ref={alertsEndRef} />
          </div>
        </section>
      </main>
    </>
  )
}

export default App
