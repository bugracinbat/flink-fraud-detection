import { useState } from 'react'

export const SimulateButton = ({ scenario, alertCount, intensity, onSimulate }) => {
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  const handleClick = async () => {
    setLoading(true)
    setError('')

    try {
      await onSimulate(scenario.id)
    } catch (simulationError) {
      setError(simulationError.message)
    } finally {
      window.setTimeout(() => setLoading(false), 450)
    }
  }

  return (
    <button
      className="sim-btn"
      data-accent={scenario.accent}
      onClick={handleClick}
      disabled={loading}
      type="button"
    >
      <span className="sim-icon" aria-hidden="true" />
      <span className="sim-copy">
        <span className="sim-title-row">
          <strong>{scenario.title}</strong>
          <span>{scenario.kind === 'Near miss' ? '0x' : alertCount}</span>
        </span>
        <span className="sim-description">{error || `${scenario.kind}: ${scenario.description}`}</span>
      </span>
      <span className="sim-action">{loading ? '...' : `Run ${intensity.toLowerCase()}`}</span>
    </button>
  )
}
