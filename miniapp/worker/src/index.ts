/**
 * Cloudflare Worker для мини-приложения «Портфель по критериям».
 *
 * Делает две вещи:
 *   1. GET /data/<путь> — прокси чтения приватного репозитория данных.
 *      Токен GitHub лежит в секрете воркера и во фронтенд не попадает.
 *   2. POST /tg/webhook — вебхук Telegram-бота: /start, /mute, /unmute.
 *      Список заглушённых бумаг и chat_id подписчиков хранится в KV.
 *   3. GET /mutes?chat_id=... — список заглушённых бумаг, его читает
 *      GitHub Action перед отправкой уведомлений.
 */

export interface Env {
  STATE: KVNamespace
  DATA_OWNER: string
  DATA_REPO: string
  DATA_BRANCH: string
  ALLOWED_ORIGIN: string
  GITHUB_TOKEN?: string
  TELEGRAM_BOT_TOKEN?: string
  TELEGRAM_WEBHOOK_SECRET?: string
}

const CHATS_KEY = 'chats'
const mutesKey = (chatId: string) => `mutes:${chatId}`

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url)

    if (request.method === 'OPTIONS') {
      return new Response(null, { status: 204, headers: corsHeaders(env) })
    }

    if (url.pathname.startsWith('/data/')) {
      return proxyData(url.pathname.slice('/data/'.length), env)
    }

    if (url.pathname === '/tg/webhook' && request.method === 'POST') {
      return handleWebhook(request, env)
    }

    if (url.pathname === '/mutes') {
      return handleMutes(request, url, env)
    }

    return new Response('Not found', { status: 404 })
  },
}

// ───────────────────────────── прокси данных ──────────────────────────────

function corsHeaders(env: Env): Record<string, string> {
  return {
    'Access-Control-Allow-Origin': env.ALLOWED_ORIGIN || '*',
    'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
    'Access-Control-Allow-Headers': 'Content-Type',
  }
}

/** Пропускаем только пути репозитория данных, которые читает приложение. */
function isAllowedPath(path: string): boolean {
  if (path.includes('..') || path.startsWith('/')) return false
  return (
    path === 'config/watchlist.json' ||
    path === 'feed/feed.json' ||
    path === 'CRITERIA.md' ||
    path === 'schema/event.schema.json' ||
    /^data\/[\w.-]+\/(status\.json|events\.jsonl)$/.test(path) ||
    /^digests\/[\w.-]+\.md$/.test(path) ||
    /^assets\/logos\/[\w.-]+\.(png|jpg|jpeg|svg|webp)$/.test(path)
  )
}

async function proxyData(path: string, env: Env): Promise<Response> {
  const decoded = decodeURIComponent(path)
  if (!isAllowedPath(decoded)) {
    return new Response('Путь не разрешён', { status: 403, headers: corsHeaders(env) })
  }

  const api = `https://api.github.com/repos/${env.DATA_OWNER}/${env.DATA_REPO}/contents/${decoded}?ref=${env.DATA_BRANCH}`
  const upstream = await fetch(api, {
    headers: {
      Accept: 'application/vnd.github.raw',
      Authorization: env.GITHUB_TOKEN ? `Bearer ${env.GITHUB_TOKEN}` : '',
      'User-Agent': 'portfolio-miniapp-worker',
    },
    // Кэш на стороне Cloudflare, чтобы не упираться в лимит GitHub API.
    cf: { cacheTtl: 300, cacheEverything: true },
  })

  if (upstream.status === 404) {
    return new Response('Нет такого файла', { status: 404, headers: corsHeaders(env) })
  }
  if (!upstream.ok) {
    return new Response(`GitHub ответил ${upstream.status}`, {
      status: 502,
      headers: corsHeaders(env),
    })
  }

  return new Response(upstream.body, {
    status: 200,
    headers: {
      ...corsHeaders(env),
      'Content-Type': contentTypeFor(decoded),
      'Cache-Control': 'public, max-age=300',
    },
  })
}

function contentTypeFor(path: string): string {
  if (path.endsWith('.json')) return 'application/json; charset=utf-8'
  if (path.endsWith('.jsonl')) return 'application/x-ndjson; charset=utf-8'
  if (path.endsWith('.md')) return 'text/markdown; charset=utf-8'
  if (path.endsWith('.svg')) return 'image/svg+xml'
  if (path.endsWith('.png')) return 'image/png'
  if (path.endsWith('.webp')) return 'image/webp'
  if (/\.jpe?g$/.test(path)) return 'image/jpeg'
  return 'text/plain; charset=utf-8'
}

// ─────────────────────────────── бот ──────────────────────────────────────

interface TelegramUpdate {
  message?: {
    chat?: { id?: number }
    text?: string
  }
}

async function handleWebhook(request: Request, env: Env): Promise<Response> {
  // Telegram присылает секрет в заголовке — так чужой запрос не пройдёт.
  const secret = request.headers.get('X-Telegram-Bot-Api-Secret-Token')
  if (!env.TELEGRAM_WEBHOOK_SECRET || secret !== env.TELEGRAM_WEBHOOK_SECRET) {
    return new Response('Нет доступа', { status: 401 })
  }

  let update: TelegramUpdate
  try {
    update = (await request.json()) as TelegramUpdate
  } catch {
    return new Response('ok')
  }

  const chatId = update.message?.chat?.id
  const text = (update.message?.text ?? '').trim()
  if (chatId === undefined || !text.startsWith('/')) return new Response('ok')

  const reply = await runCommand(String(chatId), text, env)
  if (reply) await sendMessage(env, String(chatId), reply)
  return new Response('ok')
}

async function runCommand(chatId: string, text: string, env: Env): Promise<string> {
  // Команда может прийти как «/mute@MyBot sber» — хвост после @ отбрасываем.
  const [rawCommand, ...rest] = text.split(/\s+/)
  const command = rawCommand.split('@')[0].toLowerCase()
  const argument = rest.join(' ').trim().toLowerCase()

  switch (command) {
    case '/start': {
      await rememberChat(chatId, env)
      return [
        'Готово — уведомления по портфелю будут приходить сюда.',
        '',
        `Ваш chat_id: <code>${chatId}</code>`,
        'Впишите его в секрет <code>TELEGRAM_CHAT_ID</code> репозитория с данными.',
        '',
        'Команды:',
        '<code>/mute &lt;id бумаги&gt;</code> — не присылать уведомления по бумаге',
        '<code>/unmute &lt;id бумаги&gt;</code> — вернуть уведомления',
        '<code>/muted</code> — список заглушённых',
      ].join('\n')
    }

    case '/mute': {
      if (!argument) return 'Укажите бумагу: <code>/mute sber</code>'
      const muted = await readMutes(chatId, env)
      if (muted.includes(argument)) return `Бумага <b>${escapeHtml(argument)}</b> и так заглушена.`
      await writeMutes(chatId, [...muted, argument], env)
      return `Уведомления по <b>${escapeHtml(argument)}</b> отключены.`
    }

    case '/unmute': {
      if (!argument) return 'Укажите бумагу: <code>/unmute sber</code>'
      const muted = await readMutes(chatId, env)
      if (!muted.includes(argument)) return `Бумага <b>${escapeHtml(argument)}</b> не была заглушена.`
      await writeMutes(chatId, muted.filter((item) => item !== argument), env)
      return `Уведомления по <b>${escapeHtml(argument)}</b> снова включены.`
    }

    case '/muted': {
      const muted = await readMutes(chatId, env)
      return muted.length === 0
        ? 'Заглушённых бумаг нет.'
        : `Заглушены: ${muted.map((item) => `<b>${escapeHtml(item)}</b>`).join(', ')}`
    }

    default:
      return 'Не знаю такой команды. Есть /start, /mute, /unmute и /muted.'
  }
}

async function readMutes(chatId: string, env: Env): Promise<string[]> {
  const raw = await env.STATE.get(mutesKey(chatId))
  if (!raw) return []
  try {
    const parsed = JSON.parse(raw)
    return Array.isArray(parsed) ? parsed.map(String) : []
  } catch {
    return []
  }
}

async function writeMutes(chatId: string, mutes: string[], env: Env): Promise<void> {
  await env.STATE.put(mutesKey(chatId), JSON.stringify([...new Set(mutes)].sort()))
}

async function rememberChat(chatId: string, env: Env): Promise<void> {
  const raw = await env.STATE.get(CHATS_KEY)
  const chats: string[] = raw ? (JSON.parse(raw) as string[]) : []
  if (chats.includes(chatId)) return
  await env.STATE.put(CHATS_KEY, JSON.stringify([...chats, chatId]))
}

async function sendMessage(env: Env, chatId: string, html: string): Promise<void> {
  if (!env.TELEGRAM_BOT_TOKEN) return
  await fetch(`https://api.telegram.org/bot${env.TELEGRAM_BOT_TOKEN}/sendMessage`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      chat_id: chatId,
      text: html,
      parse_mode: 'HTML',
      disable_web_page_preview: true,
    }),
  })
}

function escapeHtml(value: string): string {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
}

// ──────────────────── список заглушённых для GitHub Action ────────────────

async function handleMutes(request: Request, url: URL, env: Env): Promise<Response> {
  // Эндпоинт закрыт тем же секретом, что и вебхук: его дёргает только
  // GitHub Action, и список заглушённых бумаг наружу не светится.
  const secret = request.headers.get('X-Mutes-Secret')
  if (!env.TELEGRAM_WEBHOOK_SECRET || secret !== env.TELEGRAM_WEBHOOK_SECRET) {
    return new Response('Нет доступа', { status: 401 })
  }

  const chatId = url.searchParams.get('chat_id')
  if (!chatId) {
    return Response.json({ muted: [] }, { status: 400 })
  }
  return Response.json({ muted: await readMutes(chatId, env) })
}
