import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { describe, expect, it, vi } from 'vitest'
import {
  EVENT_TYPES,
  IMPORTANCES,
  VERDICTS,
  CRITERION_STATES,
  compareCriteriaCodes,
  parseEventsJsonl,
  parseFeed,
  parseStatus,
} from './schema'

const read = (relative: string) =>
  readFileSync(fileURLToPath(new URL(`../mock/${relative}`, import.meta.url)), 'utf8')

const eventSchemaJson = JSON.parse(read('schema/event.schema.json')) as {
  required: string[]
  properties: Record<string, { enum?: string[]; patternProperties?: Record<string, unknown> }>
}

// Моки — срез настоящего репозитория данных nubasikplayyt-dev/invest-watch.
// Строки событий в них помечены комментарием как примеры для разработки:
// в самом репозитории events.jsonl пока пустые.

describe('сверка с schema/event.schema.json', () => {
  it('типы событий совпадают со схемой', () => {
    expect([...EVENT_TYPES].sort()).toEqual([...(eventSchemaJson.properties.type.enum ?? [])].sort())
  })

  it('уровни важности совпадают со схемой', () => {
    expect([...IMPORTANCES].sort()).toEqual(
      [...(eventSchemaJson.properties.importance.enum ?? [])].sort(),
    )
  })

  it('вердикты совпадают со схемой', () => {
    expect([...VERDICTS].sort()).toEqual([...(eventSchemaJson.properties.verdict.enum ?? [])].sort())
  })

  it('все обязательные поля схемы читаются парсером', () => {
    const line = JSON.stringify({
      id: 'SBER-20261028-x',
      asset_id: 'SBER',
      date: '2026-10-28',
      type: 'financial_report',
      importance: 'high',
      title: 'Отчёт',
      summary: 'Сводка.',
      criteria: { C6: { status: 'pass', note: 'норма' } },
      verdict: 'buy_zone',
      sources: [{ title: 'Источник', url: 'https://example.com' }],
      notify: true,
    })

    const event = parseEventsJsonl(line).events[0]

    for (const field of eventSchemaJson.required) {
      expect(event, `поле ${field}`).toHaveProperty(field)
    }
    expect(event.criteria).toEqual([{ code: 'C6', status: 'pass', note: 'норма' }])
    expect(event.verdict).toBe('buy_zone')
    expect(event.notify).toBe(true)
  })
})

describe('parseEventsJsonl', () => {
  it('читает моковый events.jsonl и пропускает строки-комментарии', () => {
    const { events, skipped } = parseEventsJsonl(read('data/SBER/events.jsonl'), 'SBER')

    expect(skipped).toEqual([])
    expect(events).toHaveLength(3)
    expect(events.every((event) => event.asset_id === 'SBER')).toBe(true)
  })

  it('сортирует события по дате, новые сверху', () => {
    const { events } = parseEventsJsonl(read('data/SBER/events.jsonl'), 'SBER')
    const dates = events.map((event) => event.date)

    expect(dates).toEqual(['2026-10-28', '2026-09-12', '2026-08-04'])
  })

  it('разбирает metrics объектом: числа форматируются по-русски, null отбрасывается', () => {
    const line = JSON.stringify({
      id: 'e1',
      asset_id: 'SBER',
      date: '2026-10-28',
      type: 'dividend',
      importance: 'critical',
      title: 'Дивиденд',
      metrics: { dividend_rub: 37.64, yield_pct: 12.7, net_debt_bn_rub: null },
    })

    expect(parseEventsJsonl(line).events[0].metrics).toEqual([
      { name: 'dividend_rub', value: '37,64' },
      { name: 'yield_pct', value: '12,7' },
    ])
  })

  it('читает criteria события и сортирует C перед P', () => {
    const line = JSON.stringify({
      id: 'e2',
      asset_id: 'SIBN',
      date: '2026-09-30',
      type: 'dividend',
      importance: 'critical',
      title: 'Дивиденд',
      criteria: {
        P2: { status: 'warn', note: 'фаза цикла' },
        C6: { status: 'pass' },
        P1: { status: 'pass', note: '8,4%' },
      },
    })

    expect(parseEventsJsonl(line).events[0].criteria).toEqual([
      { code: 'C6', status: 'pass', note: '' },
      { code: 'P1', status: 'pass', note: '8,4%' },
      { code: 'P2', status: 'warn', note: 'фаза цикла' },
    ])
  })

  it('принимает sources строками и объектами, битые ссылки отбрасывает', () => {
    const line = JSON.stringify({
      id: 'e3',
      asset_id: 'SBER',
      date: '2026-10-28',
      type: 'other',
      importance: 'medium',
      title: 'Новость',
      sources: ['https://www.e-disclosure.ru/a', { url: 'https://b.example/c', title: 'Б' }, 'не ссылка'],
    })

    expect(parseEventsJsonl(line).events[0].sources).toEqual([
      { title: 'e-disclosure.ru', url: 'https://www.e-disclosure.ru/a' },
      { title: 'Б', url: 'https://b.example/c' },
    ])
  })

  it('битая строка пропускается, остальные читаются', () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {})
    const text = [
      '{ это не json',
      JSON.stringify({
        id: 'ok',
        asset_id: 'SBER',
        date: '2026-10-01',
        type: 'debt',
        importance: 'medium',
        title: 'Живое событие',
      }),
      '{"id":"нет-заголовка","asset_id":"SBER","date":"2026-10-02","type":"debt","importance":"medium"}',
    ].join('\n')

    const { events, skipped } = parseEventsJsonl(text)

    expect(events.map((event) => event.id)).toEqual(['ok'])
    expect(skipped).toEqual([1, 3])
    expect(warn).toHaveBeenCalled()
    warn.mockRestore()
  })

  it('пустой events.jsonl — это не ошибка', () => {
    expect(parseEventsJsonl('').events).toEqual([])
    expect(parseEventsJsonl('\n\n').skipped).toEqual([])
  })

  it('неизвестные поля сохраняются', () => {
    const line = JSON.stringify({
      id: 'e4',
      asset_id: 'SBER',
      date: '2026-10-28',
      type: 'financial_report',
      importance: 'high',
      title: 'Отчёт',
      detected_at: '2026-10-28T19:05:00+03:00',
      custom_field: { foo: 'bar' },
    })

    const event = parseEventsJsonl(line).events[0]

    expect(event.detected_at).toBe('2026-10-28T19:05:00+03:00')
    expect((event as Record<string, unknown>).custom_field).toEqual({ foo: 'bar' })
  })

  it('«low» вне схемы показывается как medium, а не роняет строку', () => {
    const line = JSON.stringify({
      id: 'e5',
      asset_id: 'SBER',
      date: '2026-10-28',
      type: 'other',
      importance: 'low',
      title: 'Мелочь',
    })

    expect(parseEventsJsonl(line).events[0].importance).toBe('medium')
  })
})

describe('parseFeed', () => {
  it('читает feed.json с полем items', () => {
    const feed = parseFeed(JSON.parse(read('feed/feed.json')))

    expect(feed.skipped).toEqual([])
    expect(feed.events).toHaveLength(9)
    expect(feed.events[0].date).toBe('2026-10-30')
  })

  it('пустая лента репозитория читается без ошибок', () => {
    expect(parseFeed({ generated_at: '2026-10-30T13:30:00+03:00', items: [] }).events).toEqual([])
  })

  it('читает ленту, записанную просто массивом', () => {
    const raw = JSON.parse(read('feed/feed.json')) as { items: unknown[] }

    expect(parseFeed(raw.items).events).toHaveLength(9)
  })
})

describe('parseStatus', () => {
  it('читает вложенный status.verdict и status.updated', () => {
    const status = parseStatus(JSON.parse(read('data/SBER/status.json')))

    expect(status.verdict).toBe('buy_zone')
    expect(status.updated_at).toBe('2026-10-30')
    expect(status.asset_id).toBe('SBER')
  })

  it('читает watch массивом строк', () => {
    const status = parseStatus(JSON.parse(read('data/SBER/status.json')))

    expect(status.watch).toEqual([
      'Резервы и стоимость риска во 2П 2026 и 2027',
      'Рекомендация дивидендов за 2026 г. (весна 2027)',
    ])
  })

  it('читает критерии объектом, в том числе без note', () => {
    const status = parseStatus(JSON.parse(read('data/SBER/status.json')))

    expect(status.criteria.map((criterion) => criterion.code)).toEqual([
      'C1', 'C2', 'C3', 'C4', 'C5', 'C6', 'C7', 'C8', 'P1', 'P2',
    ])
    expect(status.criteria.find((criterion) => criterion.code === 'C7')).toEqual({
      code: 'C7',
      status: 'n/a',
      note: '',
    })
  })

  it('неполный набор критериев читается как есть', () => {
    const status = parseStatus(JSON.parse(read('data/PARUS-SBL/status.json')))

    expect(status.verdict).toBe('do_not_buy')
    expect(status.criteria.map((criterion) => criterion.code)).toEqual([
      'C1', 'C2', 'C3', 'C6', 'P1', 'P3',
    ])
    expect(status.criteria.find((criterion) => criterion.code === 'P3')?.status).toBe('fail')
  })

  it('все статусы критериев в репозитории укладываются в шкалу CRITERIA.md', () => {
    for (const asset of ['SBER', 'SIBN', 'PHOR', 'PARUS-SBL']) {
      for (const criterion of parseStatus(JSON.parse(read(`data/${asset}/status.json`))).criteria) {
        expect(CRITERION_STATES, `${asset} / ${criterion.code}`).toContain(criterion.status)
      }
    }
  })

  it('плоская раскладка без вложенного status тоже читается', () => {
    const status = parseStatus({
      verdict: 'sell_candidate',
      updated_at: '2026-10-30',
      watch: 'Одна строка вместо массива',
      criteria: [
        { code: 'P1', status: 'OK', note: 'норма' },
        { code: 'C2', state: 'N/A' },
        { code: 'C1', result: 'warning', comment: 'дорого' },
      ],
    })

    expect(status.verdict).toBe('sell_candidate')
    expect(status.watch).toEqual(['Одна строка вместо массива'])
    expect(status.criteria.map((criterion) => [criterion.code, criterion.status])).toEqual([
      ['C1', 'warn'],
      ['C2', 'n/a'],
      ['P1', 'pass'],
    ])
  })

  it('C-критерии идут перед P-критериями, внутри группы — по номеру', () => {
    const codes = ['P2', 'C10', 'C2', 'P1', 'C1'].map((code) => ({ code }))

    expect(codes.sort(compareCriteriaCodes).map((item) => item.code)).toEqual([
      'C1', 'C2', 'C10', 'P1', 'P2',
    ])
  })

  it('неизвестный вердикт не роняет экран', () => {
    expect(parseStatus({ status: { verdict: 'что-то новое' } }).verdict).toBe('watch')
  })
})
