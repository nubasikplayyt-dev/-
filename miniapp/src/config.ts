/** Настройки берутся из .env на этапе сборки, см. .env.example. */
export const config = {
  owner: import.meta.env.VITE_DATA_OWNER ?? 'nubasikplayyt-dev',
  repo: import.meta.env.VITE_DATA_REPO ?? 'invest-watch',
  branch: import.meta.env.VITE_DATA_BRANCH ?? 'main',
  /** Пусто — читаем публичный репозиторий напрямую; иначе через Cloudflare Worker. */
  proxy: (import.meta.env.VITE_DATA_PROXY ?? '').replace(/\/+$/, ''),
  /** Время жизни кэша ответов. */
  cacheTtlMs: 5 * 60 * 1000,
  /** VITE_USE_MOCK=1 — работать на моковых данных из src/mock, без сети. */
  useMock: import.meta.env.VITE_USE_MOCK === '1',
}

/** Полный URL файла в репозитории данных. */
export function fileUrl(path: string): string {
  const clean = path.replace(/^\/+/, '')
  if (config.proxy) return `${config.proxy}/data/${clean}`
  return `https://raw.githubusercontent.com/${config.owner}/${config.repo}/${config.branch}/${clean}`
}
