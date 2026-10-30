import { useEffect, useMemo, useState } from 'react'
import { displayTicker } from '../data/schema'
import { formatDate } from '../lib/date'
import { flagOf } from '../lib/flag'
import { markSeen } from '../lib/readState'
import { onBackButton, openExternal } from '../telegram'
import { CriteriaTable } from './components/CriteriaTable'
import { EventView, typeLabel } from './components/EventView'
import { Logo } from './components/Logo'
import { VerdictBadge } from './components/VerdictBadge'
import type { AppData } from './useData'

type Tab = { kind: 'events'; type: string | null } | { kind: 'dividends' }

interface Props {
  data: AppData
  assetId: string
  onBack: () => void
}

export function AssetScreen({ data, assetId, onBack }: Props) {
  const ref = data.refs.find(({ asset }) => asset.id === assetId)
  const events = data.events.get(assetId) ?? []
  const status = data.statuses.get(assetId)
  const [tab, setTab] = useState<Tab>({ kind: 'events', type: null })

  // Кнопка «назад» Telegram ведёт на главный экран, как и системный жест.
  useEffect(() => onBackButton(onBack), [onBack])

  // Открыли экран — события бумаги считаются прочитанными.
  useEffect(() => {
    markSeen(
      assetId,
      events.filter((event) => event.notify).map((event) => event.id),
    )
  }, [assetId, events])

  // Тип dividend не попадает в фильтры: под него есть отдельная вкладка.
  const types = useMemo(() => {
    const unique = new Set(events.map((event) => event.type))
    unique.delete('dividend')
    return [...unique].sort((a, b) => typeLabel(a).localeCompare(typeLabel(b), 'ru'))
  }, [events])

  if (!ref) {
    return (
      <div className="app">
        <div className="error-box">
          Бумага <b>{assetId}</b> не найдена в <code>config/watchlist.json</code>.
          <div style={{ marginTop: 12 }}>
            <button type="button" className="ghost-button" onClick={onBack}>
              На главный экран
            </button>
          </div>
        </div>
      </div>
    )
  }

  const { asset, section } = ref
  const dividends = events.filter((event) => event.type === 'dividend')
  const visible =
    tab.kind === 'dividends'
      ? dividends
      : tab.type === null
        ? events
        : events.filter((event) => event.type === tab.type)

  return (
    <div className="app">
      <button type="button" className="ghost-button" onClick={onBack}>
        ← Назад
      </button>

      <div className="card" style={{ marginTop: 12 }}>
        <div className="row">
          <Logo ticker={displayTicker(asset)} logo={asset.logo} large />
          <div style={{ minWidth: 0 }}>
            <div style={{ fontSize: 17, fontWeight: 700 }}>{asset.name}</div>
            <div className="small muted">
              {flagOf(section.country)} {displayTicker(asset)}
              {asset.isin ? ` · ${asset.isin}` : ''}
            </div>
          </div>
        </div>
        <div className="spread" style={{ marginTop: 12 }}>
          <VerdictBadge verdict={status?.verdict} />
          <span className="small muted">Обновлено {formatDate(status?.updated_at)}</span>
        </div>

        {(asset.qualified_only || asset.is_commodity) && (
          <div className="chips" style={{ marginTop: 10 }}>
            {asset.qualified_only && <span className="chip">Только для квалифицированных</span>}
            {asset.is_commodity && (
              <span className="chip">
                <span className="muted">Сырьевая</span>
                {asset.commodity && <b>{commodityLabel(asset.commodity)}</b>}
              </span>
            )}
          </div>
        )}

        {(asset.ir_url || asset.disclosure_url) && (
          <div className="sources" style={{ marginTop: 10 }}>
            {asset.ir_url && <SourceLink url={asset.ir_url} title="Страница для инвесторов" />}
            {asset.disclosure_url && <SourceLink url={asset.disclosure_url} title="Раскрытие" />}
          </div>
        )}
      </div>

      <div className="section-title">Критерии</div>
      <div className="card">
        <CriteriaTable criteria={status?.criteria ?? []} dictionary={data.criteria} />
      </div>

      <div className="section-title">Что отслеживаем</div>
      <div className="card small">
        {status && status.watch.length > 0 ? (
          <ul className="watch-list">
            {status.watch.map((item) => (
              <li key={item}>{item}</li>
            ))}
          </ul>
        ) : (
          <span className="muted">Пока ничего не отмечено.</span>
        )}
      </div>

      <div className="tabs" role="tablist">
        <button
          type="button"
          role="tab"
          className="tab"
          aria-selected={tab.kind === 'events' && tab.type === null}
          onClick={() => setTab({ kind: 'events', type: null })}
        >
          Все события
        </button>
        {dividends.length > 0 && (
          <button
            type="button"
            role="tab"
            className="tab"
            aria-selected={tab.kind === 'dividends'}
            onClick={() => setTab({ kind: 'dividends' })}
          >
            Дивиденды
          </button>
        )}
        {types.map((type) => (
          <button
            type="button"
            role="tab"
            key={type}
            className="tab"
            aria-selected={tab.kind === 'events' && tab.type === type}
            onClick={() => setTab({ kind: 'events', type })}
          >
            {typeLabel(type)}
          </button>
        ))}
      </div>

      <div className="card">
        {visible.length === 0 ? (
          <div className="small muted">Событий нет.</div>
        ) : tab.kind === 'dividends' ? (
          <DividendTable events={visible} />
        ) : (
          visible.map((event) => <EventView event={event} key={event.id} />)
        )}
      </div>
    </div>
  )
}

const COMMODITIES: Record<string, string> = {
  oil: 'нефть',
  gas: 'газ',
  fertilizers: 'удобрения',
  metals: 'металлы',
  gold: 'золото',
  coal: 'уголь',
}

function commodityLabel(commodity: string): string {
  return COMMODITIES[commodity] ?? commodity
}

function SourceLink({ url, title }: { url: string; title: string }) {
  return (
    <a
      href={url}
      className="small"
      onClick={(nativeEvent) => {
        nativeEvent.preventDefault()
        openExternal(url)
      }}
    >
      {title} ↗
    </a>
  )
}

/** Вкладка «Дивиденды»: все события type: dividend таблицей. */
function DividendTable({ events }: { events: AppData['feed'] }) {
  return (
    <table className="table">
      <thead>
        <tr>
          <th>Дата</th>
          <th>Событие</th>
          <th>Показатели</th>
        </tr>
      </thead>
      <tbody>
        {events.map((event) => (
          <tr key={event.id}>
            <td style={{ whiteSpace: 'nowrap' }}>{formatDate(event.date)}</td>
            <td>
              <div>{event.title}</div>
              {event.sources[0] && (
                <a
                  href={event.sources[0].url}
                  className="small"
                  onClick={(nativeEvent) => {
                    nativeEvent.preventDefault()
                    openExternal(event.sources[0].url)
                  }}
                >
                  {event.sources[0].title} ↗
                </a>
              )}
            </td>
            <td>
              {event.metrics.length === 0
                ? '—'
                : event.metrics.map((metric) => (
                    <div key={metric.name}>
                      <span className="muted">{metric.name}: </span>
                      {metric.value}
                    </div>
                  ))}
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}
