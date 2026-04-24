import React, { useState } from 'react';

export const SimulateButton = ({ scenario, title, description }) => {
  const [loading, setLoading] = useState(false);

  const handleClick = async () => {
    setLoading(true);
    try {
      await fetch('http://localhost:8080/api/simulate', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ scenario }),
      });
    } catch (error) {
      console.error('Simulation failed', error);
    } finally {
      setTimeout(() => setLoading(false), 500); // Visual feedback
    }
  };

  return (
    <button 
      className="sim-btn" 
      data-scenario={scenario}
      onClick={handleClick}
      disabled={loading}
    >
      <h3>{title} {loading && '⏳'}</h3>
      <p>{description}</p>
    </button>
  );
};
