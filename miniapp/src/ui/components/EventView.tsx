import type { Importance, MonitorEvent } from '../../data/schema'
import { formatDate } from '../../lib/date'
import { openExternal } from '../../telegram'
import { CriteriaChips, MetricChips } from './Chips'
import { verdictLabel } from './VerdictBadge'

const IMPORTANCE_EMOJI: Record<Importance, string> = {
  critical: '🔴',
  high: '🟠',
  medium: '🟡',
}

/** Типы событий из schema/event.schema.json. */
const TYPE_LABELS: Record<string, string> = {
  financial_report: 'Отчёт',
  dividend: 'Дивиденды',
  debt: 'Долг',
  management: 'Менеджмент',
  shareholder: 'Акционеры',
  sanctions: 'Санкции',
  regulation: 'Регулирование',
  commodity_cycle: 'Сырьевой цикл',
  nav_rent: 'СЧА и аренда',
  price_filter: 'Цена и доходность',
  other: 'Прочее',
}

/** Человеческое название типа; незнакомый тип показываем как есть. */
export function typeLabel(type: string): string {
  return TYPE_LABELS[type] ?? type
}

export function importanceMark(importance: Importance): string {
  return IMPORTANCE_EMOJI[importance] ?? '🟡'
}

export function EventView({ event }: { event: MonitorEvent }) {
  return (
    <article className="event">
      <div className="row small muted">
        <span>{importanceMark(event.importance)}</span>
        <span>{formatDate(event.date)}</span>
        <span>·</span>
        <span>{typeLabel(event.type)}</span>
        {event.verdict && (
          <>
            <span>·</span>
            <span>{verdictLabel(event.verdict).toLowerCase()}</span>
          </>
        )}
      </div>
      <div className="headline">{event.title}</div>
      {event.summary && <div className="small muted">{event.summary}</div>}
      <CriteriaChips criteria={event.criteria} />
      <MetricChips metrics={event.metrics} />
      {event.sources.length > 0 && (
        <div className="sources">
          {event.sources.map((source) => (
            <a
              key={source.url}
              href={source.url}
              onClick={(nativeEvent) => {
                nativeEvent.preventDefault()
                openExternal(source.url)
              }}
            >
              {source.title} ↗
            </a>
          ))}
        </div>
      )}
    </article>
  )
}
