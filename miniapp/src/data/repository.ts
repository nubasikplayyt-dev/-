import {
  parseEventsJsonl,
  parseFeed,
  parseStatus,
  parseWatchlist,
  type Asset,
  type MonitorEvent,
  type Section,
  type Status,
  type Watchlist,
} from './schema'
import { fetchJson, fetchText } from './source'

export interface AssetRef {
  asset: Asset
  section: Section
}

/** Плоский список всех бумаг со ссылкой на их раздел. */
export function flattenAssets(watchlist: Watchlist): AssetRef[] {
  return watchlist.sections.flatMap((section) =>
    section.assets.map((asset) => ({ asset, section })),
  )
}

export async function loadWatchlist(): Promise<Watchlist> {
  const raw = await fetchJson('config/watchlist.json')
  if (raw === null) throw new Error('config/watchlist.json не найден в репозитории данных')
  return parseWatchlist(raw)
}

export async function loadStatus(assetId: string): Promise<Status | null> {
  const raw = await fetchJson(`data/${assetId}/status.json`)
  if (raw === null) return null
  try {
    return parseStatus(raw)
  } catch (error) {
    console.warn(`data/${assetId}/status.json пропущен`, error)
    return null
  }
}

export async function loadEvents(assetId: string): Promise<MonitorEvent[]> {
  const text = await fetchText(`data/${assetId}/events.jsonl`)
  if (text === null) return []
  return parseEventsJsonl(text, assetId).events
}

export async function loadFeed(): Promise<MonitorEvent[]> {
  const raw = await fetchJson('feed/feed.json')
  if (raw === null) return []
  return parseFeed(raw).events
}

export async function loadCriteriaText(): Promise<string> {
  return (await fetchText('CRITERIA.md')) ?? ''
}

/** Статусы всех бумаг сразу: главный экран показывает вердикты в сетке. */
export async function loadAllStatuses(refs: AssetRef[]): Promise<Map<string, Status>> {
  const entries = await Promise.all(
    refs.map(async ({ asset }) => [asset.id, await loadStatus(asset.id)] as const),
  )
  const map = new Map<string, Status>()
  for (const [id, status] of entries) if (status) map.set(id, status)
  return map
}

/** События всех бумаг сразу — нужны главному экрану для точек «непрочитано». */
export async function loadAllEvents(refs: AssetRef[]): Promise<Map<string, MonitorEvent[]>> {
  const entries = await Promise.all(
    refs.map(async ({ asset }) => [asset.id, await loadEvents(asset.id)] as const),
  )
  return new Map(entries)
}
