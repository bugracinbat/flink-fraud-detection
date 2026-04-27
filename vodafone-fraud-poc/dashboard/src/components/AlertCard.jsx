const alertMeta = {
  VELOCITY_FRAUD_SIM_CLONE: {
    label: 'SIM Clone Velocity',
    accent: 'velocity',
    shortCode: 'VL',
  },
  SEQUENTIAL_DIALING: {
    label: 'Sequential Dialing',
    accent: 'sequential',
    shortCode: 'SQ',
  },
  STATICAL_RULE_FRAUD: {
    label: 'Static Rule Fraud',
    accent: 'static',
    shortCode: 'ST',
  },
  DISTANCE_FORWARDING_FRAUD: {
    label: 'Forwarding Fan-out',
    accent: 'forwarding',
    shortCode: 'FW',
  },
}

export const AlertCard = ({ alert }) => {
  const meta = alertMeta[alert.fraudType] || {
    label: alert.fraudType,
    accent: 'unknown',
    shortCode: '!',
  }
  const time = new Date(alert.timestamp).toLocaleTimeString([], {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  })

  return (
    <article className="alert-card" data-accent={meta.accent}>
      <div className="alert-icon" aria-hidden="true">
        {meta.shortCode}
      </div>
      <div className="alert-content">
        <div className="alert-header">
          <span className="alert-type">{meta.label}</span>
          <time className="alert-time">{time}</time>
        </div>
        <div className="alert-msisdn">{alert.msisdn}</div>
        <p className="alert-desc">{alert.description}</p>
      </div>
    </article>
  )
}
