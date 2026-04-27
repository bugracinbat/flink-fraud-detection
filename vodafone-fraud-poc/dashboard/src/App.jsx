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
    description: 'Same subscriber appears in Germany, then Turkiye five minutes later.',
  },
  {
    id: 'SEQUENTIAL_DIALING',
    title: 'Sequential Dialing',
    detector: 'SEQUENTIAL_DIALING',
    accent: 'sequential',
    description: 'One caller rapidly dials incrementing destination numbers.',
  },
  {
    id: 'STATICAL_RULE',
    title: 'Static Rule Anomaly',
    detector: 'STATICAL_RULE_FRAUD',
    accent: 'static',
    description: 'A fresh SIM with low data usage calls many distinct numbers.',
  },
  {
    id: 'CALL_FORWARDING',
    title: 'Forwarding Fan-out',
    detector: 'DISTANCE_FORWARDING_FRAUD',
    accent: 'forwarding',
    description: 'A callee redirects several short calls to different numbers.',
  },
]

const filters = [
  { id: 'ALL', label: 'All' },
  ...scenarios.map((scenario) => ({ id: scenario.detector, label: scenario.title })),
]

function formatUptime(ms = 0) {
  const seconds = Math.floor(ms / 1000)
  const minutes = Math.floor(seconds / 60)
  const hours = Math.floor(minutes / 60)

  if (hours > 0) return `${hours}h ${minutes % 60}m`
  if (minutes > 0) return `${minutes}m ${seconds % 60}s`
  return `${seconds}s`
}

function App() {
  const [alerts, setAlerts] = useState([])
  const [connectionState, setConnectionState] = useState('connecting')
  const [serverStatus, setServerStatus] = useState(null)
  const [activeFilter, setActiveFilter] = useState('ALL')
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
      body: JSON.stringify({ scenario: scenarioId }),
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

          <div className="sim-buttons">
            {scenarios.map((scenario) => (
              <SimulateButton
                key={scenario.id}
                scenario={scenario}
                alertCount={alertCounts[scenario.detector] || 0}
                onSimulate={handleSimulate}
              />
            ))}
          </div>

          {lastSimulation && (
            <div className="simulation-receipt">
              <span>Last injection</span>
              <strong>{lastSimulation.scenario.replaceAll('_', ' ')}</strong>
              <p>
                {lastSimulation.cdrEvents} CDRs, {lastSimulation.locationEvents} location events queued
              </p>
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
              <span>Simulations</span>
              <strong>{serverStatus?.simulationsTriggered ?? 0}</strong>
            </div>
            <div className="metric">
              <span>Queued events</span>
              <strong>{(serverStatus?.queuedCdrEvents ?? 0) + (serverStatus?.queuedLocationEvents ?? 0)}</strong>
            </div>
            <div className="metric">
              <span>Server uptime</span>
              <strong>{serverStatus ? formatUptime(serverStatus.uptimeMs) : '-'}</strong>
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
