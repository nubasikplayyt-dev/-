import type { Verdict } from '../../data/schema'

// Значения вердиктов описаны в AGENT.md репозитория данных.
const LABELS: Record<Verdict, string> = {
  buy_zone: 'Зона покупки',
  hold: 'Держать',
  watch: 'Наблюдать',
  do_not_buy: 'Не покупать',
  sell_candidate: 'Кандидат на продажу',
}

export function verdictLabel(verdict: Verdict | undefined): string {
  return verdict ? LABELS[verdict] : 'Нет оценки'
}

export function VerdictBadge({ verdict }: { verdict: Verdict | undefined }) {
  if (!verdict) {
    return <span className="badge" style={{ background: 'var(--state-na)' }}>Нет оценки</span>
  }
  return (
    <span className="badge" style={{ background: `var(--verdict-${verdict})` }}>
      {LABELS[verdict]}
    </span>
  )
}
