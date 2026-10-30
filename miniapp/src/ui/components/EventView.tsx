import type { Importance, MonitorEvent } from '../../data/schema'
import { formatDate } from '../../lib/date'
import { openExternal } from '../../telegram'
import { MetricChips } from './Chips'

const IMPORTANCE_EMOJI: Record<Importance, string> = {
  critical: '🔴',
  high: '🟠',
  medium: '🟡',
  low: '⚪️',
}

const TYPE_LABELS: Record<string, string> = {
  report: 'Отчёт',
  dividend: 'Дивиденды',
  debt: 'Долг',
  buyback: 'Выкуп',
  guidance: 'Прогноз',
  rating: 'Рейтинг',
  mna: 'Сделки M&A',
  regulatory: 'Регулирование',
  management: 'Менеджмент',
  other: 'Прочее',
}

/** Человеческое название типа события; незнакомый тип показываем как есть. */
export function typeLabel(type: string): string {
  return TYPE_LABELS[type] ?? type
}

export function importanceMark(importance: Importance): string {
  return IMPORTANCE_EMOJI[importance]
}

export function EventView({ event }: { event: MonitorEvent }) {
  return (
    <article className="event">
      <div className="row small muted">
        <span>{importanceMark(event.importance)}</span>
        <span>{formatDate(event.date)}</span>
        <span>·</span>
        <span>{typeLabel(event.type)}</span>
      </div>
      <div className="headline">{event.title}</div>
      {event.summary && <div className="small muted">{event.summary}</div>}
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
