import { z } from 'zod'

// Схемы намеренно терпимые: неизвестные поля сохраняются, а у полей,
// названия которых в репозитории данных могут отличаться, разобраны синонимы.
// Вся подгонка под реальный формат делается здесь и больше нигде.

export const VERDICTS = ['buy_zone', 'hold', 'watch', 'do_not_buy', 'sell_candidate'] as const
export type Verdict = (typeof VERDICTS)[number]

export const CRITERION_STATES = ['pass', 'warn', 'fail', 'n/a'] as const
export type CriterionState = (typeof CRITERION_STATES)[number]

export const IMPORTANCES = ['critical', 'high', 'medium', 'low'] as const
export type Importance = (typeof IMPORTANCES)[number]

const verdict = z.enum(VERDICTS).catch('watch')
const importance = z.enum(IMPORTANCES).catch('low')

const criterionState = z.preprocess((value) => {
  if (typeof value !== 'string') return value
  const normalized = value.trim().toLowerCase().replace(/[\s_-]/g, '')
  if (normalized === 'na' || normalized === 'none' || normalized === '') return 'n/a'
  if (normalized === 'ok' || normalized === 'yes') return 'pass'
  if (normalized === 'warning') return 'warn'
  if (normalized === 'no') return 'fail'
  return value.trim().toLowerCase()
}, z.enum(CRITERION_STATES).catch('n/a'))

const pickFirst = (source: Record<string, unknown>, names: string[]): unknown => {
  for (const name of names) {
    const value = source[name]
    if (value !== undefined && value !== null && value !== '') return value
  }
  return undefined
}

// ───────────────────────────── watchlist.json ─────────────────────────────

export const assetSchema = z
  .object({
    id: z.string().min(1),
    ticker: z.string().min(1),
    name: z.string().min(1),
    short_name: z.string().optional(),
    isin: z.string().optional(),
    logo: z.string().optional(),
    currency: z.string().optional(),
  })
  .passthrough()
export type Asset = z.infer<typeof assetSchema>

export const sectionSchema = z
  .object({
    id: z.string().min(1),
    title: z.string().min(1),
    country: z.string().min(2).max(3),
    currency: z.string().optional(),
    yield_floor_pct: z.number().optional(),
    assets: z.array(assetSchema).default([]),
  })
  .passthrough()
export type Section = z.infer<typeof sectionSchema>

export const watchlistSchema = z
  .object({
    sections: z.array(sectionSchema).min(1),
  })
  .passthrough()
export type Watchlist = z.infer<typeof watchlistSchema>

/**
 * Разбирает watchlist.json. Поддержаны две раскладки:
 *   1) assets вложены в каждый раздел (основной вариант);
 *   2) общий массив assets верхнего уровня, где у каждой бумаги есть section.
 * Раздел с битой записью не теряется целиком: отбрасывается только эта запись.
 */
export function parseWatchlist(raw: unknown): Watchlist {
  const root = asRecord(raw)
  const rawSections = Array.isArray(root.sections) ? root.sections : []
  const looseAssets = Array.isArray(root.assets) ? root.assets : []

  const sections: Section[] = []
  for (const rawSection of rawSections) {
    const record = asRecord(rawSection)
    const own = Array.isArray(record.assets) ? record.assets : []
    const inherited = looseAssets.filter((item) => {
      const asset = asRecord(item)
      return asset.section === record.id || asset.section_id === record.id
    })

    const assets: Asset[] = []
    for (const candidate of [...own, ...inherited]) {
      const parsed = assetSchema.safeParse(normalizeAsset(candidate))
      if (parsed.success) assets.push(parsed.data)
      else warn('asset', candidate, parsed.error)
    }

    const parsed = sectionSchema.safeParse({ ...record, assets })
    if (parsed.success) sections.push(parsed.data)
    else warn('section', rawSection, parsed.error)
  }

  return watchlistSchema.parse({ ...root, sections })
}

function normalizeAsset(raw: unknown): unknown {
  const record = asRecord(raw)
  const ticker = pickFirst(record, ['ticker', 'symbol', 'secid'])
  return {
    ...record,
    id: pickFirst(record, ['id', 'asset_id']) ?? ticker,
    ticker,
    name: pickFirst(record, ['name', 'title', 'full_name']) ?? ticker,
    short_name: pickFirst(record, ['short_name', 'shortName', 'short']),
  }
}

// ────────────────────────────── status.json ───────────────────────────────

export const criterionSchema = z
  .object({
    code: z.string().min(1),
    status: criterionState,
    note: z.string().default(''),
  })
  .passthrough()
export type Criterion = z.infer<typeof criterionSchema>

export const statusSchema = z
  .object({
    asset_id: z.string().optional(),
    verdict,
    updated_at: z.string().optional(),
    watch: z.string().default(''),
    criteria: z.array(criterionSchema).default([]),
  })
  .passthrough()
export type Status = z.infer<typeof statusSchema>

/**
 * Разбирает status.json. criteria принимается и объектом {"C1": {...}},
 * и массивом [{code, status, note}]; значение критерия может быть
 * просто строкой статуса.
 */
export function parseStatus(raw: unknown): Status {
  const record = asRecord(raw)
  const criteria: Criterion[] = []

  const push = (code: string, value: unknown) => {
    const body = typeof value === 'string' ? { status: value } : asRecord(value)
    const parsed = criterionSchema.safeParse({
      ...body,
      code,
      status: pickFirst(body, ['status', 'state', 'result']) ?? 'n/a',
      note: pickFirst(body, ['note', 'comment', 'reason', 'text']) ?? '',
    })
    if (parsed.success) criteria.push(parsed.data)
    else warn('criterion', value, parsed.error)
  }

  const rawCriteria = record.criteria
  if (Array.isArray(rawCriteria)) {
    for (const item of rawCriteria) {
      const body = asRecord(item)
      const code = pickFirst(body, ['code', 'id', 'criterion'])
      if (typeof code === 'string') push(code, body)
      else warn('criterion', item, new Error('нет кода критерия'))
    }
  } else if (rawCriteria && typeof rawCriteria === 'object') {
    for (const [code, value] of Object.entries(rawCriteria)) push(code, value)
  }

  return statusSchema.parse({
    ...record,
    verdict: pickFirst(record, ['verdict', 'decision', 'rating']) ?? 'watch',
    updated_at: pickFirst(record, ['updated_at', 'updatedAt', 'date', 'as_of']),
    watch: pickFirst(record, ['watch', 'watching', 'watch_items']) ?? '',
    criteria: criteria.sort(compareCriteriaCodes),
  })
}

/** C1…C8 идут перед P1…P3, внутри группы — по номеру. */
export function compareCriteriaCodes(a: { code: string }, b: { code: string }): number {
  const parse = (code: string) => {
    const match = /^([A-Za-z]+)(\d+)$/.exec(code.trim())
    return match ? { group: match[1].toUpperCase(), index: Number(match[2]) } : { group: code, index: 0 }
  }
  const left = parse(a.code)
  const right = parse(b.code)
  if (left.group !== right.group) return left.group < right.group ? -1 : 1
  return left.index - right.index
}

// ───────────────────────────── events.jsonl ───────────────────────────────

export const sourceSchema = z
  .object({
    title: z.string().min(1),
    url: z.string().url(),
  })
  .passthrough()
export type Source = z.infer<typeof sourceSchema>

export const metricSchema = z.object({
  name: z.string().min(1),
  value: z.string().min(1),
})
export type Metric = z.infer<typeof metricSchema>

export const eventSchema = z
  .object({
    id: z.string().min(1),
    asset_id: z.string().min(1),
    date: z.string().min(1),
    type: z.string().min(1),
    importance,
    title: z.string().min(1),
    summary: z.string().default(''),
    notify: z.boolean().default(false),
    metrics: z.array(metricSchema).default([]),
    sources: z.array(sourceSchema).default([]),
  })
  .passthrough()
export type MonitorEvent = z.infer<typeof eventSchema>

export function parseEvent(raw: unknown, fallbackAssetId?: string): MonitorEvent {
  const record = asRecord(raw)
  const date = pickFirst(record, ['date', 'published_at', 'timestamp', 'ts', 'created_at'])
  return eventSchema.parse({
    ...record,
    id: pickFirst(record, ['id', 'event_id', 'uid']) ?? synthesizeId(record, date),
    asset_id: pickFirst(record, ['asset_id', 'asset', 'ticker']) ?? fallbackAssetId,
    date,
    type: pickFirst(record, ['type', 'event_type', 'kind']) ?? 'other',
    importance: pickFirst(record, ['importance', 'severity', 'priority']) ?? 'low',
    title: pickFirst(record, ['title', 'headline', 'name']),
    summary: pickFirst(record, ['summary', 'description', 'text', 'body']) ?? '',
    notify: toBoolean(pickFirst(record, ['notify', 'should_notify'])),
    metrics: normalizeMetrics(record.metrics),
    sources: normalizeSources(pickFirst(record, ['sources', 'links', 'refs'])),
  })
}

export interface ParsedEvents {
  events: MonitorEvent[]
  /** Номера строк (с 1), которые не прошли разбор. Экран из-за них не падает. */
  skipped: number[]
}

/**
 * Разбирает events.jsonl построчно. Битая строка пропускается с записью
 * в консоль, остальные события читаются как обычно. Пустые строки и
 * строки-комментарии (`#` или `//`) игнорируются молча.
 */
export function parseEventsJsonl(text: string, fallbackAssetId?: string): ParsedEvents {
  const events: MonitorEvent[] = []
  const skipped: number[] = []

  text.split('\n').forEach((line, index) => {
    const trimmed = line.trim()
    if (!trimmed || trimmed.startsWith('#') || trimmed.startsWith('//')) return
    try {
      events.push(parseEvent(JSON.parse(trimmed), fallbackAssetId))
    } catch (error) {
      skipped.push(index + 1)
      console.warn(`events.jsonl: строка ${index + 1} пропущена`, error)
    }
  })

  events.sort((a, b) => (a.date < b.date ? 1 : a.date > b.date ? -1 : 0))
  return { events, skipped }
}

/** feed/feed.json — массив событий или объект {events: [...]}. */
export function parseFeed(raw: unknown): ParsedEvents {
  const list = Array.isArray(raw) ? raw : (asRecord(raw).events ?? asRecord(raw).items)
  const events: MonitorEvent[] = []
  const skipped: number[] = []

  if (Array.isArray(list)) {
    list.forEach((item, index) => {
      try {
        events.push(parseEvent(item))
      } catch (error) {
        skipped.push(index + 1)
        console.warn(`feed.json: запись ${index + 1} пропущена`, error)
      }
    })
  }

  events.sort((a, b) => (a.date < b.date ? 1 : a.date > b.date ? -1 : 0))
  return { events, skipped }
}

// ───────────────────────────────── утилиты ────────────────────────────────

function asRecord(value: unknown): Record<string, unknown> {
  return value && typeof value === 'object' && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : {}
}

function toBoolean(value: unknown): boolean {
  if (typeof value === 'boolean') return value
  if (typeof value === 'string') return ['true', '1', 'yes', 'да'].includes(value.trim().toLowerCase())
  return false
}

function synthesizeId(record: Record<string, unknown>, date: unknown): string {
  const title = pickFirst(record, ['title', 'headline', 'name'])
  return `${String(date ?? 'нет-даты')}:${String(title ?? 'без-заголовка')}`
}

function normalizeMetrics(raw: unknown): Metric[] {
  if (Array.isArray(raw)) {
    return raw
      .map((item) => {
        if (typeof item === 'string') return { name: item, value: '' }
        const record = asRecord(item)
        const name = pickFirst(record, ['name', 'label', 'key'])
        const value = pickFirst(record, ['value', 'val'])
        return name ? { name: String(name), value: formatMetricValue(value) } : null
      })
      .filter((item): item is Metric => item !== null && item.value !== '')
  }
  if (raw && typeof raw === 'object') {
    return Object.entries(raw as Record<string, unknown>)
      .map(([name, value]) => ({ name, value: formatMetricValue(value) }))
      .filter((metric) => metric.value !== '')
  }
  return []
}

function formatMetricValue(value: unknown): string {
  if (value === undefined || value === null) return ''
  if (typeof value === 'number') return new Intl.NumberFormat('ru-RU').format(value)
  if (typeof value === 'boolean') return value ? 'да' : 'нет'
  return String(value)
}

function normalizeSources(raw: unknown): Source[] {
  if (!Array.isArray(raw)) return []
  return raw
    .map((item) => {
      if (typeof item === 'string') return { title: hostOf(item), url: item }
      const record = asRecord(item)
      const url = pickFirst(record, ['url', 'href', 'link'])
      if (typeof url !== 'string') return null
      const title = pickFirst(record, ['title', 'name', 'source'])
      return { ...record, title: String(title ?? hostOf(url)), url }
    })
    .filter((item): item is Source => {
      if (item === null) return false
      const parsed = sourceSchema.safeParse(item)
      if (!parsed.success) console.warn('источник пропущен', item)
      return parsed.success
    })
}

function hostOf(url: string): string {
  try {
    return new URL(url).hostname.replace(/^www\./, '')
  } catch {
    return 'источник'
  }
}

function warn(what: string, value: unknown, error: unknown): void {
  console.warn(`Пропущена запись (${what})`, value, error)
}
