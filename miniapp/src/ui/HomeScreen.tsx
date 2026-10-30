import { displayTicker } from '../data/schema'
import { flagOf } from '../lib/flag'
import { hasUnread } from '../lib/readState'
import { formatDate } from '../lib/date'
import { Logo } from './components/Logo'
import { VerdictBadge } from './components/VerdictBadge'
import { importanceMark } from './components/EventView'
import type { AppData } from './useData'

interface Props {
  data: AppData
  onOpenAsset: (assetId: string) => void
  onRefresh: () => void
  loading: boolean
}

export function HomeScreen({ data, onOpenAsset, onRefresh, loading }: Props) {
  // «Важное» — только critical и high, как договорились по feed.json.
  const important = data.feed
    .filter((event) => event.importance === 'critical' || event.importance === 'high')
    .slice(0, 5)

  const nameById = new Map(data.refs.map(({ asset }) => [asset.id, displayTicker(asset)]))

  return (
    <div className="app">
      <div className="toolbar">
        <h1 style={{ fontSize: 20, margin: 0 }}>Портфель по критериям</h1>
        <button type="button" className="ghost-button" onClick={onRefresh} disabled={loading}>
          {loading ? 'Обновляю…' : 'Обновить'}
        </button>
      </div>

      {important.length > 0 && (
        <>
          <div className="section-title">Важное</div>
          <div className="card">
            {important.map((event) => (
              <button
                type="button"
                className="feed-item"
                key={event.id}
                onClick={() => onOpenAsset(event.asset_id)}
              >
                <div className="row small muted">
                  <span>{importanceMark(event.importance)}</span>
                  <span>{nameById.get(event.asset_id) ?? event.asset_id}</span>
                  <span>·</span>
                  <span>{formatDate(event.date)}</span>
                </div>
                <div className="headline">{event.title}</div>
              </button>
            ))}
          </div>
        </>
      )}

      {data.watchlist.sections.map((section) => (
        <section key={section.id}>
          <div className="section-title">
            <span className="flag">{flagOf(section.country)}</span>
            <span>{section.title}</span>
          </div>
          {section.assets.length === 0 ? (
            <div className="card small muted">В разделе пока нет бумаг.</div>
          ) : (
            <div className="asset-grid">
              {section.assets.map((asset) => {
                const status = data.statuses.get(asset.id)
                const notifyIds = (data.events.get(asset.id) ?? [])
                  .filter((event) => event.notify)
                  .map((event) => event.id)
                return (
                  <button
                    type="button"
                    className="asset-card"
                    key={asset.id}
                    onClick={() => onOpenAsset(asset.id)}
                  >
                    {hasUnread(asset.id, notifyIds) && <span className="unread-dot" />}
                    <Logo ticker={displayTicker(asset)} logo={asset.logo} />
                    <div>
                      <div className="ticker">{displayTicker(asset)}</div>
                      <div className="name">{asset.name}</div>
                    </div>
                    <VerdictBadge verdict={status?.verdict} />
                  </button>
                )
              })}
            </div>
          )}
        </section>
      ))}
    </div>
  )
}
