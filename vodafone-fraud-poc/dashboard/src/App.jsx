import React, { useEffect, useState, useRef } from 'react'
import { AlertCard } from './components/AlertCard'
import { SimulateButton } from './components/SimulateButton'

function App() {
  const [alerts, setAlerts] = useState([]);
  const alertsEndRef = useRef(null);

  useEffect(() => {
    const eventSource = new EventSource('http://localhost:8080/api/alerts');

    eventSource.onmessage = (event) => {
      try {
        const newAlert = JSON.parse(event.data);
        setAlerts((prev) => [...prev, newAlert]);
      } catch (e) {
        console.error("Failed to parse alert", e);
      }
    };

    eventSource.onerror = (error) => {
      console.error("SSE Error", error);
    };

    return () => {
      eventSource.close();
    };
  }, []);

  useEffect(() => {
    if (alertsEndRef.current) {
      alertsEndRef.current.scrollIntoView({ behavior: 'smooth' });
    }
  }, [alerts]);

  return (
    <>
      <header className="header">
        <h1>
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="var(--accent-color)" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
            <path d="M12 2v20M17 5H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6" />
          </svg>
          Vodafone Fraud Detection
        </h1>
        <div className="status-badge">
          <div className="status-dot"></div>
          Flink Pipeline Active
        </div>
      </header>

      <main className="main-content">
        <section className="panel">
          <div className="panel-header">
            <h2>Simulate Scenarios</h2>
            <p>Inject specific fraud patterns into the Flink streams.</p>
          </div>
          
          <div className="sim-buttons">
            <SimulateButton 
              scenario="SIM_CLONE" 
              title="SIM Clone (Velocity)" 
              description="Same MSISDN appears in Germany, then Turkiye within 5 mins."
            />
            <SimulateButton 
              scenario="SEQUENTIAL_DIALING" 
              title="Sequential Dialing" 
              description="Caller rapidly dials sequentially incremented numbers."
            />
            <SimulateButton 
              scenario="STATICAL_RULE" 
              title="Statical Rule Anomaly" 
              description="New SIM (< 3 days), low data, dials 10 distinct numbers."
            />
            <SimulateButton 
              scenario="CALL_FORWARDING" 
              title="Distance Forwarding" 
              description="Caller A calls B, but B forwards to multiple C numbers."
            />
          </div>
        </section>

        <section className="panel">
          <div className="panel-header">
            <h2>Live Feed</h2>
            <p>Real-time alerts detected by Apache Flink.</p>
          </div>
          
          <div className="alerts-container">
            {alerts.length === 0 ? (
              <div className="empty-state">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <circle cx="12" cy="12" r="10"></circle>
                  <line x1="12" y1="8" x2="12" y2="12"></line>
                  <line x1="12" y1="16" x2="12.01" y2="16"></line>
                </svg>
                <p>Waiting for fraud events...</p>
              </div>
            ) : (
              alerts.map((alert, idx) => (
                <AlertCard key={idx} alert={alert} />
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
