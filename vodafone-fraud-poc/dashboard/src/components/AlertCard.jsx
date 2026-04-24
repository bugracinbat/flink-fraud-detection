import React from 'react';

const icons = {
  VelocityFraud: '🌍',
  SequentialDialing: '📞',
  StaticalRule: '📊',
  CallForwardingDistance: '🔄'
};

const labels = {
  VelocityFraud: 'SIM Clone / Velocity',
  SequentialDialing: 'Sequential Dialing',
  StaticalRule: 'Statical Anomaly',
  CallForwardingDistance: 'Distance Forwarding'
};

export const AlertCard = ({ alert }) => {
  const time = new Date(alert.timestamp).toLocaleTimeString();
  const icon = icons[alert.fraudType] || '⚠️';
  const label = labels[alert.fraudType] || alert.fraudType;

  return (
    <div className="alert-card" data-type={alert.fraudType}>
      <div className="alert-icon">
        {icon}
      </div>
      <div className="alert-content">
        <div className="alert-header">
          <span className="alert-type">{label}</span>
          <span className="alert-time">{time}</span>
        </div>
        <div className="alert-msisdn">{alert.msisdn}</div>
        <div className="alert-desc">{alert.description}</div>
      </div>
    </div>
  );
};
