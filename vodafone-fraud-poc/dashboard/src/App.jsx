import { useEffect, useMemo, useRef, useState } from 'react'
import { AlertCard } from './components/AlertCard'
import { SimulateButton } from './components/SimulateButton'

const API_BASE_URL = 'http://localhost:8080'

const scenarios = [
  { id: 'SIM_CLONE', title: 'SIM Clone Velocity', detector: 'VELOCITY_FRAUD_SIM_CLONE', accent: 'velocity', kind: 'Fraud', description: 'Same subscriber appears in Germany, then Turkiye five minutes later.' },
  { id: 'SEQUENTIAL_DIALING', title: 'Sequential Dialing', detector: 'SEQUENTIAL_DIALING', accent: 'sequential', kind: 'Fraud', description: 'One caller rapidly dials incrementing destination numbers.' },
  { id: 'STATICAL_RULE', title: 'Static Rule Anomaly', detector: 'STATICAL_RULE_FRAUD', accent: 'static', kind: 'Fraud', description: 'A fresh SIM with low data usage calls many distinct numbers.' },
  { id: 'CALL_FORWARDING', title: 'Forwarding Fan-out', detector: 'DISTANCE_FORWARDING_FRAUD', accent: 'forwarding', kind: 'Fraud', description: 'A callee redirects several short calls to different numbers.' },
  { id: 'NEAR_MISS_SIM_CLONE', title: 'Near Miss: Location', detector: null, accent: 'velocity', kind: 'Near miss', description: 'Repeated benign location pings that should stay quiet.' },
  { id: 'NEAR_MISS_SEQUENTIAL', title: 'Near Miss: Sequence', detector: null, accent: 'sequential', kind: 'Near miss', description: 'Only three sequential calls, below the alert threshold.' },
  { id: 'NEAR_MISS_STATICAL_RULE', title: 'Near Miss: Static Rule', detector: null, accent: 'static', kind: 'Near miss', description: 'Nine distinct callees, one short of the SQL rule.' },
  { id: 'NEAR_MISS_CALL_FORWARDING', title: 'Near Miss: Forwarding', detector: null, accent: 'forwarding', kind: 'Near miss', description: 'Three forwarded destinations, below the forwarding threshold.' },
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

const detectorLabels = {
  VELOCITY_FRAUD_SIM_CLONE: 'SIM Clone',
  SEQUENTIAL_DIALING: 'Sequential',
  STATICAL_RULE_FRAUD: 'Static Rule',
  DISTANCE_FORWARDING_FRAUD: 'Forwarding',
}

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

function bucketEvents(events) {
  const now = Date.now()
  const buckets = Array.from({ length: 12 }, (_, index) => ({
    label: `${11 - index}s`,
    total: 0,
    alerts: 0,
  }))

  events.forEach((event) => {
    const ageSeconds = Math.floor((now - event.timestamp) / 1000)
    if (ageSeconds >= 0 && ageSeconds < buckets.length) {
      const bucket = buckets[buckets.length - 1 - ageSeconds]
      bucket.total += 1
      if (event.streamType === 'ALERT') bucket.alerts += 1
    }
  })

  return buckets
}

function App() {
  const [alerts, setAlerts] = useState([])
  const [streamEvents, setStreamEvents] = useState([])
  const [connectionState, setConnectionState] = useState('connecting')
  const [eventState, setEventState] = useState('connecting')
  const [serverStatus, setServerStatus] = useState(null)
  const [activeFilter, setActiveFilter] = useState('ALL')
  const [intensity, setIntensity] = useState('LOW')
  const [lastSimulation, setLastSimulation] = useState(null)
  const [streamPaused, setStreamPaused] = useState(false)
  const [followTail, setFollowTail] = useState(true)
  const [showBackground, setShowBackground] = useState(true)
  const [manualType, setManualType] = useState('CDR')
  const [manualMsisdn, setManualMsisdn] = useState('905423184726')
  const [manualTarget, setManualTarget] = useState('905337092418')
  const [manualRunId, setManualRunId] = useState('')
  const [manualMessage, setManualMessage] = useState('')
  const [demoMessage, setDemoMessage] = useState('')
  const alertsEndRef = useRef(null)
  const streamEndRef = useRef(null)

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
    const timer = window.setInterval(loadStatus, 2000)
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

    eventSource.onerror = () => setConnectionState('reconnecting')
    return () => eventSource.close()
  }, [])

  useEffect(() => {
    const eventSource = new EventSource(`${API_BASE_URL}/api/events`)

    eventSource.onopen = () => setEventState('connected')
    eventSource.onmessage = (event) => {
      if (streamPaused) return
      try {
        const streamEvent = JSON.parse(event.data)
        setStreamEvents((prev) => [...prev.slice(-249), streamEvent])
      } catch (error) {
        console.error('Failed to parse stream event', error)
      }
    }

    eventSource.onerror = () => setEventState('reconnecting')
    return () => eventSource.close()
  }, [streamPaused])

  useEffect(() => {
    if (alertsEndRef.current) {
      alertsEndRef.current.scrollIntoView({ behavior: 'smooth' })
    }
  }, [alerts])

  useEffect(() => {
    if (followTail && streamEndRef.current) {
      streamEndRef.current.scrollIntoView({ behavior: 'smooth' })
    }
  }, [streamEvents, followTail])

  const alertCounts = useMemo(() => {
    return alerts.reduce((counts, alert) => {
      counts[alert.fraudType] = (counts[alert.fraudType] || 0) + 1
      return counts
    }, {})
  }, [alerts])

  const visibleStreamEvents = useMemo(() => {
    return showBackground ? streamEvents : streamEvents.filter((event) => !event.background)
  }, [showBackground, streamEvents])

  const streamBuckets = useMemo(() => bucketEvents(visibleStreamEvents), [visibleStreamEvents])
  const eventMix = useMemo(() => {
    return visibleStreamEvents.reduce((mix, event) => {
      mix[event.streamType] = (mix[event.streamType] || 0) + 1
      return mix
    }, {})
  }, [visibleStreamEvents])
  const detectorMix = useMemo(() => {
    return alerts.reduce((mix, alert) => {
      const label = detectorLabels[alert.fraudType] || alert.fraudType
      mix[label] = (mix[label] || 0) + 1
      return mix
    }, {})
  }, [alerts])

  const filteredAlerts = activeFilter === 'ALL'
    ? alerts
    : alerts.filter((alert) => alert.fraudType === activeFilter)

  const handleSimulate = async (scenarioId) => {
    const response = await fetch(`${API_BASE_URL}/api/simulate`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ scenario: scenarioId, intensity }),
    })

    const payload = await response.json()
    if (!response.ok) throw new Error(payload.message || 'Simulation failed')

    setLastSimulation({ ...payload, triggeredAt: Date.now() })
    setConnectionState('connected')
    return payload
  }

  const handleProcessEvent = async (event) => {
    event.preventDefault()
    setManualMessage('')

    const body = {
      streamType: manualType,
      msisdn: manualMsisdn,
      runId: manualRunId || undefined,
    }

    if (manualType === 'CDR') {
      body.callee = manualTarget
      body.duration = 30
      body.cellSite = 'Cell-M'
      body.dataUsageMb = 25
      body.simAgeDays = 120
    } else {
      body.location = manualTarget
    }

    try {
      const response = await fetch(`${API_BASE_URL}/api/process-event`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
      })
      const payload = await response.json()
      if (!response.ok) throw new Error(payload.message || 'Event rejected')
      setManualMessage(`Processed ${payload.streamType} event`)
    } catch (error) {
      setManualMessage(error.message)
    }
  }

  const handleStartDemo = async () => {
    setDemoMessage('')
    try {
      const response = await fetch(`${API_BASE_URL}/api/demo`, { method: 'POST' })
      const payload = await response.json()
      if (!response.ok) throw new Error(payload.message || 'Could not start demo')
      setServerStatus((current) => current ? { ...current, demo: payload } : current)
      setDemoMessage('Autopilot demo started')
    } catch (error) {
      setDemoMessage(error.message)
    }
  }

  const handleStopDemo = async () => {
    setDemoMessage('')
    try {
      const response = await fetch(`${API_BASE_URL}/api/demo`, { method: 'DELETE' })
      const payload = await response.json()
      if (!response.ok) throw new Error(payload.message || 'Could not stop demo')
      setServerStatus((current) => current ? { ...current, demo: payload } : current)
      setDemoMessage('Autopilot demo stopped')
    } catch (error) {
      setDemoMessage(error.message)
    }
  }

  const handleResetDemo = async () => {
    setDemoMessage('')
    try {
      const response = await fetch(`${API_BASE_URL}/api/demo/reset`, { method: 'POST' })
      const payload = await response.json()
      if (!response.ok) throw new Error(payload.message || 'Could not reset demo state')
      setAlerts([])
      setStreamEvents([])
      setLastSimulation(null)
      setServerStatus((current) => current ? {
        ...current,
        demo: payload,
        activeRuns: [],
        completedRuns: [],
        queuedCdrEvents: 0,
        queuedLocationEvents: 0,
        simulationsTriggered: 0,
        alertsBroadcast: 0,
        eventsScheduled: 0,
        eventsEmitted: 0,
      } : current)
      setDemoMessage('Demo state reset')
    } catch (error) {
      setDemoMessage(error.message)
    }
  }

  const activeRuns = serverStatus?.activeRuns ?? []
  const completedRuns = serverStatus?.completedRuns ?? []
  const matchedRuns = completedRuns.filter((run) => run.expectedAlerts === run.actualAlerts).length
  const detectionScore = completedRuns.length > 0 ? Math.round((matchedRuns / completedRuns.length) * 100) : 0
  const emitted = serverStatus?.eventsEmitted ?? 0
  const scheduled = serverStatus?.eventsScheduled ?? 0
  const demo = serverStatus?.demo
  const demoSteps = demo?.steps ?? []
  const demoRunning = Boolean(demo?.running)
  const activeDemoStep = demoSteps.find((step) => step.state === 'active') ?? null
  const nextDemoStep = demoSteps.find((step) => step.state === 'pending') ?? null
  const demoRunPrefix = demo?.demoId && activeDemoStep ? `${demo.demoId}-${activeDemoStep.scenario}` : ''
  const storyRun = demoRunPrefix
    ? [...activeRuns, ...completedRuns].find((run) => run.runId.startsWith(demoRunPrefix))
    : null
  const highlightedRunIds = new Set((demo?.demoId ? activeRuns : [])
    .filter((run) => run.runId.startsWith(demo.demoId))
    .map((run) => run.runId))
  if (storyRun) highlightedRunIds.add(storyRun.runId)
  const storyVerdict = storyRun
    ? `${verdictFor(storyRun)} · ${storyRun.actualAlerts}/${storyRun.expectedAlerts} alerts`
    : demoRunning
      ? 'Waiting for this run to emit'
      : 'Ready'
  const streamRate = streamBuckets.slice(-5).reduce((sum, bucket) => sum + bucket.total, 0) / 5
  const statusLabel = connectionState === 'connected' && eventState === 'connected'
    ? 'Streams connected'
    : connectionState === 'offline'
      ? 'Server offline'
      : 'Reconnecting'

  return (
    <>
      <header className="header">
        <div className="brand-lockup">
          <span className="brand-mark">V</span>
          <div>
            <h1>Vodafone Fraud Detection</h1>
            <p>Apache Flink live stream console</p>
          </div>
        </div>
        <div className={`status-badge ${connectionState}`}>
          <span className="status-dot" />
          {statusLabel}
        </div>
      </header>

      <main className="main-content stream-layout">
        <section className="control-panel">
          <div className="panel-header">
            <h2>Simulation Scenarios</h2>
            <p>Inject fraud and near-miss traffic into the stream.</p>
          </div>

          <div className="intensity-control" aria-label="Simulation intensity">
            {intensityOptions.map((option) => (
              <button key={option.id} type="button" className={intensity === option.id ? 'active' : ''} onClick={() => setIntensity(option.id)}>
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

          <form className="manual-event" onSubmit={handleProcessEvent}>
            <div className="panel-header">
              <h2>Process Event</h2>
              <p>Push one custom event directly into the Flink sources.</p>
            </div>
            <div className="manual-grid">
              <label>
                Type
                <select value={manualType} onChange={(event) => setManualType(event.target.value)}>
                  <option value="CDR">CDR</option>
                  <option value="LOCATION">Location</option>
                </select>
              </label>
              <label>
                MSISDN
                <input value={manualMsisdn} onChange={(event) => setManualMsisdn(event.target.value)} />
              </label>
              <label>
                {manualType === 'CDR' ? 'Callee' : 'Location'}
                <input value={manualTarget} onChange={(event) => setManualTarget(event.target.value)} />
              </label>
              <label>
                Run ID
                <input placeholder="auto" value={manualRunId} onChange={(event) => setManualRunId(event.target.value)} />
              </label>
            </div>
            <button type="submit" className="process-btn">Process</button>
            {manualMessage && <p className="manual-message">{manualMessage}</p>}
          </form>
        </section>

        <section className="feed-panel">
          <section className="demo-panel">
            <div className="feed-toolbar">
              <div>
                <h2>Autopilot Demo</h2>
                <p>{demoRunning ? `Next step in ${formatUptime(demo.nextStepInMs ?? 0)}` : 'Run a scripted demo with narration markers and expected outcomes.'}</p>
              </div>
              <div className="stream-controls">
                <button type="button" className={demoRunning ? 'active' : ''} onClick={handleStartDemo} disabled={demoRunning}>Start Demo</button>
                <button type="button" onClick={handleStopDemo} disabled={!demoRunning}>Stop</button>
                <button type="button" onClick={handleResetDemo}>Reset</button>
              </div>
            </div>
            <div className="story-panel">
              <div>
                <span>Active story</span>
                <strong>{activeDemoStep?.title ?? (demoRunning ? 'Preparing next chapter' : 'Ready for scripted demo')}</strong>
                <p>{activeDemoStep?.message ?? 'Start the autopilot to show near misses, fraud escalation, and alert quality as one guided stream.'}</p>
              </div>
              <div>
                <span>Current run</span>
                <strong>{storyRun?.scenario.replaceAll('_', ' ') ?? 'No active run'}</strong>
                <p>{storyRun ? `${storyRun.emittedEvents}/${storyRun.totalEvents} events emitted · ${storyRun.intensity}` : 'Rows will highlight as the active scenario enters the stream.'}</p>
              </div>
              <div>
                <span>Expected vs actual</span>
                <strong>{storyVerdict}</strong>
                <p>{nextDemoStep ? `Next: ${nextDemoStep.title}` : demoRunning ? 'Finishing scripted sequence' : 'Timeline is reset and ready.'}</p>
              </div>
            </div>
            <div className="demo-timeline">
              {demoSteps.map((step) => (
                <div className="demo-step" data-state={step.state} key={`${step.index}-${step.scenario}`}>
                  <span>{step.index + 1}</span>
                  <strong>{step.title}</strong>
                  <small>{step.scenario.replaceAll('_', ' ')}</small>
                </div>
              ))}
            </div>
            {demoMessage && <p className="manual-message">{demoMessage}</p>}
          </section>

          <div className="pipeline-strip">
            {['Simulation', 'Sources', 'Detectors', 'Alert Sink', 'Dashboard'].map((stage, index) => (
              <div className={activeRuns.length > 0 || visibleStreamEvents.length > 0 ? 'active' : ''} key={stage}>
                <span>{index + 1}</span>
                {stage}
              </div>
            ))}
          </div>

          <div className="metrics-grid">
            <div className="metric"><span>Events emitted</span><strong>{emitted}</strong></div>
            <div className="metric"><span>Stream rate</span><strong>{streamRate.toFixed(1)}/s</strong></div>
            <div className="metric"><span>Total alerts</span><strong>{alerts.length}</strong></div>
            <div className="metric"><span>Run match rate</span><strong>{completedRuns.length > 0 ? `${detectionScore}%` : '-'}</strong></div>
            <div className="metric"><span>Queued events</span><strong>{(serverStatus?.queuedCdrEvents ?? 0) + (serverStatus?.queuedLocationEvents ?? 0)}</strong></div>
            <div className="metric"><span>Progress</span><strong>{scheduled > 0 ? `${Math.round((emitted / scheduled) * 100)}%` : '-'}</strong></div>
          </div>

          <div className="charts-grid">
            <section className="chart-panel">
              <div className="feed-toolbar compact">
                <div>
                  <h2>Throughput</h2>
                  <p>Events per second, last 12 seconds</p>
                </div>
              </div>
              <div className="bar-chart">
                {streamBuckets.map((bucket) => {
                  const max = Math.max(1, ...streamBuckets.map((item) => item.total))
                  return (
                    <div className="bar-column" key={bucket.label}>
                      <span style={{ height: `${Math.max(4, (bucket.total / max) * 100)}%` }} />
                      <small>{bucket.total}</small>
                    </div>
                  )
                })}
              </div>
            </section>

            <section className="chart-panel">
              <div className="feed-toolbar compact">
                <div>
                  <h2>Event Mix</h2>
                  <p>Visible stream composition</p>
                </div>
              </div>
              <div className="mix-list">
                {['CDR', 'LOCATION', 'ALERT'].map((type) => {
                  const count = eventMix[type] || 0
                  const max = Math.max(1, ...Object.values(eventMix), count)
                  return (
                    <div className="mix-row" key={type}>
                      <span>{type}</span>
                      <div><i style={{ width: `${(count / max) * 100}%` }} /></div>
                      <strong>{count}</strong>
                    </div>
                  )
                })}
              </div>
            </section>

            <section className="chart-panel">
              <div className="feed-toolbar compact">
                <div>
                  <h2>Detector Mix</h2>
                  <p>Alerts by detector</p>
                </div>
              </div>
              <div className="mix-list">
                {Object.entries(detectorLabels).map(([type, label]) => {
                  const count = detectorMix[label] || 0
                  const max = Math.max(1, ...Object.values(detectorMix), count)
                  return (
                    <div className="mix-row" key={type}>
                      <span>{label}</span>
                      <div><i style={{ width: `${(count / max) * 100}%` }} /></div>
                      <strong>{count}</strong>
                    </div>
                  )
                })}
              </div>
            </section>
          </div>

          {lastSimulation && (
            <div className="simulation-receipt">
              <span>Last scheduled run</span>
              <strong>{lastSimulation.scenario.replaceAll('_', ' ')} · {lastSimulation.intensity}</strong>
              <p>{lastSimulation.totalEvents} events over {formatUptime(lastSimulation.durationMs)} · expected {lastSimulation.expectedAlerts}, actual {lastSimulation.actualAlerts}</p>
            </div>
          )}

          {activeRuns.length > 0 && (
            <div className="active-runs">
              <span>Active runs</span>
              {activeRuns.map((run) => (
                <div className={`run-progress ${highlightedRunIds.has(run.runId) ? 'highlighted' : ''}`} key={run.runId}>
                  <div>
                    <strong>{run.scenario.replaceAll('_', ' ')}</strong>
                    <small>{run.emittedEvents}/{run.totalEvents} · {run.actualAlerts}/{run.expectedAlerts} alerts</small>
                  </div>
                  <progress max={run.totalEvents} value={run.emittedEvents} />
                </div>
              ))}
            </div>
          )}

          <section className="stream-console">
            <div className="feed-toolbar">
              <div>
                <h2>Live Event Stream</h2>
                <p>{visibleStreamEvents.length} retained rows from generated, manual, and alert events</p>
              </div>
              <div className="stream-controls">
                <button type="button" className={streamPaused ? 'active' : ''} onClick={() => setStreamPaused((value) => !value)}>{streamPaused ? 'Resume' : 'Pause'}</button>
                <button type="button" className={followTail ? 'active' : ''} onClick={() => setFollowTail((value) => !value)}>Follow</button>
                <button type="button" className={showBackground ? 'active' : ''} onClick={() => setShowBackground((value) => !value)}>Noise</button>
                <button type="button" onClick={() => setStreamEvents([])}>Clear</button>
              </div>
            </div>

            <div className="stream-tape">
              {visibleStreamEvents.length === 0 ? (
                <div className="stream-empty">Waiting for stream events...</div>
              ) : (
                visibleStreamEvents.map((event, index) => {
                  const highlighted = Boolean(event.runId && highlightedRunIds.has(event.runId))
                  return (
                    <div className={`stream-row ${highlighted ? 'highlighted' : ''}`} data-type={event.streamType} key={`${event.timestamp}-${index}`}>
                      <time>{new Date(event.timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' })}</time>
                      <span className="stream-type">{event.streamType}</span>
                      <span className="stream-main">
                        {event.streamType === 'CDR' && `${event.msisdn} -> ${event.callee}`}
                        {event.streamType === 'LOCATION' && `${event.msisdn} @ ${event.location}`}
                        {event.streamType === 'ALERT' && `${event.fraudType} · ${event.msisdn}`}
                        {event.streamType === 'NARRATION' && `${event.title}: ${event.message}`}
                      </span>
                      <span className="stream-run">{event.runId || event.demoId || 'no-run'}</span>
                      <span className="stream-source">{event.background ? 'noise' : event.source || 'signal'}</span>
                    </div>
                  )
                })
              )}
              <div ref={streamEndRef} />
            </div>
          </section>

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
              <h2>Alert Feed</h2>
              <p>{filteredAlerts.length} visible alerts from the current session</p>
            </div>
            <button className="clear-btn" type="button" onClick={() => setAlerts([])} disabled={alerts.length === 0}>Clear</button>
          </div>

          <div className="filter-bar" role="tablist" aria-label="Alert filters">
            {filters.map((filter) => (
              <button key={filter.id} type="button" role="tab" className={activeFilter === filter.id ? 'active' : ''} onClick={() => setActiveFilter(filter.id)}>
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
