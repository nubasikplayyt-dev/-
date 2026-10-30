import type { Criterion, Metric } from '../../data/schema'
import { describeMetric } from '../../lib/metrics'

export function MetricChips({ metrics }: { metrics: Metric[] }) {
  if (metrics.length === 0) return null
  return (
    <div className="chips">
      {metrics.map((metric) => {
        const shown = describeMetric(metric.name, metric.value)
        return (
          <span className="chip" key={metric.name}>
            <span className="muted">{shown.label}</span>
            <b>{shown.value}</b>
          </span>
        )
      })}
    </div>
  )
}

const STATE_ICON: Record<string, string> = { pass: '✓', warn: '!', fail: '✕', 'n/a': '–' }
const STATE_CLASS: Record<string, string> = {
  pass: 'state-pass',
  warn: 'state-warn',
  fail: 'state-fail',
  'n/a': 'state-na',
}

/** Критерии, которые затронуло событие: код, значок статуса и пометка. */
export function CriteriaChips({ criteria }: { criteria: Criterion[] }) {
  if (criteria.length === 0) return null
  return (
    <div className="chips">
      {criteria.map((criterion) => (
        <span className="chip" key={criterion.code} title={criterion.note}>
          <b>{criterion.code}</b>
          <span className={STATE_CLASS[criterion.status]}>{STATE_ICON[criterion.status]}</span>
          {criterion.note && <span className="muted">{criterion.note}</span>}
        </span>
      ))}
    </div>
  )
}

export { STATE_ICON, STATE_CLASS }
