import { config, fileUrl } from '../config'

interface CacheEntry {
  at: number
  body: string | null
}

const cache = new Map<string, CacheEntry>()

// Моковый срез репозитория данных: src/mock повторяет его раскладку один в один.
// Глоб неленивый специально — так моки уезжают в отдельные чанки и не попадают
// в основной бандл боевой сборки.
const mockFiles = import.meta.glob('../mock/**/*', {
  query: '?raw',
  import: 'default',
}) as Record<string, () => Promise<string>>

async function readMock(path: string): Promise<string | null> {
  const load = mockFiles[`../mock/${path.replace(/^\/+/, '')}`]
  return load ? await load() : null
}

/** Сбрасывает кэш — вызывается кнопкой «Обновить». */
export function clearCache(): void {
  cache.clear()
}

/**
 * Читает файл из репозитория данных. Ответ кэшируется на 5 минут,
 * отсутствие файла (404) — тоже, чтобы не долбить сеть на каждой перерисовке.
 */
export async function fetchText(path: string): Promise<string | null> {
  if (config.useMock) return readMock(path)

  const cached = cache.get(path)
  if (cached && Date.now() - cached.at < config.cacheTtlMs) return cached.body

  const response = await fetch(fileUrl(path), { cache: 'no-store' })
  if (response.status === 404) {
    cache.set(path, { at: Date.now(), body: null })
    return null
  }
  if (!response.ok) {
    throw new Error(`Не удалось прочитать ${path}: HTTP ${response.status}`)
  }

  const body = await response.text()
  cache.set(path, { at: Date.now(), body })
  return body
}

export async function fetchJson(path: string): Promise<unknown | null> {
  const text = await fetchText(path)
  if (text === null) return null
  try {
    return JSON.parse(text)
  } catch (error) {
    console.warn(`${path}: не разобрался как JSON`, error)
    return null
  }
}
