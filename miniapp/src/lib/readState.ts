const KEY = 'portfolio-miniapp:seen-events'

type SeenMap = Record<string, string[]>

function read(): SeenMap {
  try {
    const raw = localStorage.getItem(KEY)
    return raw ? (JSON.parse(raw) as SeenMap) : {}
  } catch {
    return {}
  }
}

function write(map: SeenMap): void {
  try {
    localStorage.setItem(KEY, JSON.stringify(map))
  } catch {
    // Приватный режим или заблокированное хранилище — точки просто не запоминаются.
  }
}

/** Есть ли у бумаги непрочитанные события с notify: true. */
export function hasUnread(assetId: string, notifyEventIds: string[]): boolean {
  const seen = new Set(read()[assetId] ?? [])
  return notifyEventIds.some((id) => !seen.has(id))
}

/** Отмечает события бумаги прочитанными — вызывается при открытии её экрана. */
export function markSeen(assetId: string, eventIds: string[]): void {
  if (eventIds.length === 0) return
  const map = read()
  const merged = new Set([...(map[assetId] ?? []), ...eventIds])
  // Храним не больше 500 идентификаторов на бумагу, чтобы не раздувать хранилище.
  map[assetId] = [...merged].slice(-500)
  write(map)
}
