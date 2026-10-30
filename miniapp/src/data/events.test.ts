import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { describe, expect, it, vi } from 'vitest'
import { compareCriteriaCodes, parseEventsJsonl, parseFeed, parseStatus } from './schema'

const read = (relative: string) =>
  readFileSync(fileURLToPath(new URL(`../mock/${relative}`, import.meta.url)), 'utf8')

describe('parseEventsJsonl', () => {
  it('читает моковый events.jsonl целиком', () => {
    const { events, skipped } = parseEventsJsonl(read('data/sber/events.jsonl'), 'sber')

    expect(skipped).toEqual([])
    expect(events).toHaveLength(4)
    expect(events.every((event) => event.asset_id === 'sber')).toBe(true)
  })

  it('сортирует события по дате, новые сверху', () => {
    const { events } = parseEventsJsonl(read('data/sber/events.jsonl'), 'sber')
    const dates = events.map((event) => event.date)

    expect(dates).toEqual([...dates].sort().reverse())
  })

  it('разбирает metrics из объекта в чипы и приводит числа к строкам', () => {
    const line = JSON.stringify({
      id: 'e1',
      asset_id: 'sber',
      date: '2026-10-28',
      type: 'report',
      importance: 'high',
      title: 'Отчёт',
      metrics: { ROE: 24.1, 'Чистая прибыль': '1,32 трлн ₽', Пусто: null },
    })

    const { events } = parseEventsJsonl(line)

    expect(events[0].metrics).toEqual([
      { name: 'ROE', value: '24,1' },
      { name: 'Чистая прибыль', value: '1,32 трлн ₽' },
    ])
  })

  it('принимает sources строками и объектами, битые ссылки отбрасывает', () => {
    const line = JSON.stringify({
      id: 'e2',
      asset_id: 'sber',
      date: '2026-10-28',
      type: 'other',
      importance: 'low',
      title: 'Новость',
      sources: ['https://www.example.com/a', { url: 'https://b.example/c', title: 'Б' }, 'не ссылка'],
    })

    const { events } = parseEventsJsonl(line)

    expect(events[0].sources).toEqual([
      { title: 'example.com', url: 'https://www.example.com/a' },
      { title: 'Б', url: 'https://b.example/c' },
    ])
  })

  it('битая строка пропускается, остальные читаются', () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {})
    const text = [
      '{ это не json',
      JSON.stringify({ id: 'ok', asset_id: 'sber', date: '2026-10-01', type: 'report', importance: 'low', title: 'Живое событие' }),
      '{"id":"нет-заголовка","asset_id":"sber","date":"2026-10-02","type":"report","importance":"low"}',
    ].join('\n')

    const { events, skipped } = parseEventsJsonl(text)

    expect(events.map((event) => event.id)).toEqual(['ok'])
    expect(skipped).toEqual([1, 3])
    expect(warn).toHaveBeenCalled()
    warn.mockRestore()
  })

  it('пустые строки и комментарии не считаются ошибкой', () => {
    const text = `\n# комментарий\n// тоже комментарий\n\n${JSON.stringify({
      id: 'x',
      asset_id: 'sber',
      date: '2026-10-05',
      type: 'report',
      importance: 'low',
      title: 'Т',
    })}\n`

    const { events, skipped } = parseEventsJsonl(text)

    expect(events).toHaveLength(1)
    expect(skipped).toEqual([])
  })

  it('неизвестные поля сохраняются', () => {
    const line = JSON.stringify({
      id: 'e3',
      asset_id: 'sber',
      date: '2026-10-28',
      type: 'report',
      importance: 'low',
      title: 'Отчёт',
      custom_field: { foo: 'bar' },
    })

    const { events } = parseEventsJsonl(line)

    expect((events[0] as Record<string, unknown>).custom_field).toEqual({ foo: 'bar' })
  })

  it('неизвестная важность не роняет разбор, а становится low', () => {
    const line = JSON.stringify({
      id: 'e4',
      asset_id: 'sber',
      date: '2026-10-28',
      type: 'report',
      importance: 'ОЧЕНЬ ВАЖНО',
      title: 'Отчёт',
    })

    expect(parseEventsJsonl(line).events[0].importance).toBe('low')
  })

  it('notify читается и из строки «true»', () => {
    const line = JSON.stringify({
      id: 'e5',
      asset_id: 'sber',
      date: '2026-10-28',
      type: 'report',
      importance: 'low',
      title: 'Отчёт',
      notify: 'true',
    })

    expect(parseEventsJsonl(line).events[0].notify).toBe(true)
  })
})

describe('parseFeed', () => {
  it('читает feed.json из объекта с полем events', () => {
    const feed = parseFeed(JSON.parse(read('feed/feed.json')))

    expect(feed.skipped).toEqual([])
    expect(feed.events).toHaveLength(4)
    expect(feed.events[0].date).toBe('2026-10-28')
  })

  it('читает feed.json, записанный просто массивом', () => {
    const raw = JSON.parse(read('feed/feed.json')) as { events: unknown[] }

    expect(parseFeed(raw.events).events).toHaveLength(4)
  })
})

describe('parseStatus', () => {
  it('читает критерии, записанные объектом', () => {
    const status = parseStatus(JSON.parse(read('data/sber/status.json')))

    expect(status.verdict).toBe('buy_zone')
    expect(status.criteria).toHaveLength(11)
    expect(status.criteria.map((criterion) => criterion.code)).toEqual([
      'C1', 'C2', 'C3', 'C4', 'C5', 'C6', 'C7', 'C8', 'P1', 'P2', 'P3',
    ])
    expect(status.criteria[7].status).toBe('n/a')
  })

  it('читает критерии, записанные массивом, и нормализует статусы', () => {
    const status = parseStatus({
      verdict: 'sell_candidate',
      criteria: [
        { code: 'P1', status: 'OK', note: 'норма' },
        { code: 'C2', state: 'N/A' },
        { code: 'C1', result: 'warning', comment: 'дорого' },
      ],
    })

    expect(status.criteria.map((criterion) => [criterion.code, criterion.status])).toEqual([
      ['C1', 'warn'],
      ['C2', 'n/a'],
      ['P1', 'pass'],
    ])
    expect(status.criteria[0].note).toBe('дорого')
  })

  it('C-критерии идут перед P-критериями, внутри группы — по номеру', () => {
    const codes = ['P2', 'C10', 'C2', 'P1', 'C1'].map((code) => ({ code }))

    expect(codes.sort(compareCriteriaCodes).map((item) => item.code)).toEqual([
      'C1', 'C2', 'C10', 'P1', 'P2',
    ])
  })

  it('неизвестный вердикт не роняет экран', () => {
    expect(parseStatus({ verdict: 'что-то новое' }).verdict).toBe('watch')
  })
})
