import type { Metric } from '../../data/schema'

export function MetricChips({ metrics }: { metrics: Metric[] }) {
  if (metrics.length === 0) return null
  return (
    <div className="chips">
      {metrics.map((metric) => (
        <span className="chip" key={metric.name}>
          <span className="muted">{metric.name}</span>
          <b>{metric.value}</b>
        </span>
      ))}
    </div>
  )
}
