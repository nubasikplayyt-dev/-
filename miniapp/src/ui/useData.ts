import { useCallback, useEffect, useState } from 'react'
import {
  flattenAssets,
  loadAllEvents,
  loadAllStatuses,
  loadCriteriaText,
  loadFeed,
  loadWatchlist,
  type AssetRef,
} from '../data/repository'
import { parseCriteriaMarkdown, type CriteriaDictionary } from '../lib/criteria'
import type { MonitorEvent, Status, Watchlist } from '../data/schema'
import { clearCache } from '../data/source'

export interface AppData {
  watchlist: Watchlist
  refs: AssetRef[]
  statuses: Map<string, Status>
  events: Map<string, MonitorEvent[]>
  feed: MonitorEvent[]
  criteria: CriteriaDictionary
}

export interface DataState {
  data: AppData | null
  error: string | null
  loading: boolean
  refresh: () => void
}

async function loadAll(): Promise<AppData> {
  const watchlist = await loadWatchlist()
  const refs = flattenAssets(watchlist)
  const [statuses, events, feed, criteriaText] = await Promise.all([
    loadAllStatuses(refs),
    loadAllEvents(refs),
    loadFeed(),
    loadCriteriaText(),
  ])
  return { watchlist, refs, statuses, events, feed, criteria: parseCriteriaMarkdown(criteriaText) }
}

export function useData(): DataState {
  const [data, setData] = useState<AppData | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [nonce, setNonce] = useState(0)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    loadAll()
      .then((loaded) => {
        if (cancelled) return
        setData(loaded)
        setError(null)
      })
      .catch((cause: unknown) => {
        if (cancelled) return
        console.error(cause)
        setError(cause instanceof Error ? cause.message : 'Не удалось загрузить данные')
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [nonce])

  const refresh = useCallback(() => {
    clearCache()
    setNonce((value) => value + 1)
  }, [])

  return { data, error, loading, refresh }
}
