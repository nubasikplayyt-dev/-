import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { describe, expect, it, vi } from 'vitest'
import { displayTicker, parseWatchlist } from './schema'
import { flagOf } from '../lib/flag'

const watchlistRaw = () =>
  JSON.parse(
    readFileSync(fileURLToPath(new URL('../mock/config/watchlist.json', import.meta.url)), 'utf8'),
  ) as Record<string, unknown>

describe('parseWatchlist', () => {
  it('читает watchlist репозитория данных', () => {
    const watchlist = parseWatchlist(watchlistRaw())

    expect(watchlist.sections).toHaveLength(1)
    expect(watchlist.sections[0]).toMatchObject({
      id: 'ru',
      title: 'Российские акции',
      country: 'RU',
      currency: 'RUB',
      yield_floor_pct: 5,
    })
    expect(watchlist.sections[0].assets.map((asset) => asset.id)).toEqual([
      'SBER', 'SIBN', 'PHOR', 'PARUS-SBL',
    ])
  })

  it('сохраняет поля бумаги, которые нужны экрану', () => {
    const assets = parseWatchlist(watchlistRaw()).sections[0].assets

    expect(assets[1]).toMatchObject({
      id: 'SIBN',
      ticker: 'SIBN',
      name: 'Газпром нефть',
      is_commodity: true,
      commodity: 'oil',
      logo: 'logos/SIBN.svg',
    })
    expect(assets[3]).toMatchObject({
      id: 'PARUS-SBL',
      type: 'real_estate_fund',
      qualified_only: true,
      disclosure_url: 'https://investfunds.ru/funds/7279/',
    })
  })

  it('ir_url: null не ломает разбор', () => {
    expect(parseWatchlist(watchlistRaw()).sections[0].assets[0].ir_url).toBeNull()
  })

  // У ЗПИФ в поле ticker лежит ISIN — показывать его крупно бессмысленно.
  it('displayTicker берёт id, когда тикер совпадает с ISIN', () => {
    const assets = parseWatchlist(watchlistRaw()).sections[0].assets

    expect(displayTicker(assets[0])).toBe('SBER')
    expect(displayTicker(assets[3])).toBe('PARUS-SBL')
  })

  // Главное обещание README: новая бумага подхватывается без правок кода.
  it('новая бумага в assets[] появляется сама, без изменения кода', () => {
    const raw = watchlistRaw() as { sections: { assets: unknown[] }[] }
    raw.sections[0].assets.push({
      id: 'MOEX',
      ticker: 'MOEX',
      name: 'Московская биржа',
      type: 'stock',
      isin: 'RU000A0JR4A1',
      is_commodity: false,
      logo: 'logos/MOEX.svg',
      ir_url: null,
      disclosure_url: null,
    })

    const assets = parseWatchlist(raw).sections[0].assets

    expect(assets).toHaveLength(5)
    expect(assets[4]).toMatchObject({ id: 'MOEX', ticker: 'MOEX', name: 'Московская биржа' })
    expect(displayTicker(assets[4])).toBe('MOEX')
  })

  it('бумага по минимальному шаблону из README репозитория тоже читается', () => {
    const raw = watchlistRaw() as { sections: { assets: unknown[] }[] }
    raw.sections[0].assets.push({ id: 'ROSN', ticker: 'ROSN', name: 'Роснефть' })

    const asset = parseWatchlist(raw).sections[0].assets[4]

    expect(asset.logo).toBeUndefined()
    expect(asset.is_commodity).toBeUndefined()
  })

  it('id и name достраиваются из тикера, если их не указали', () => {
    const watchlist = parseWatchlist({
      sections: [{ id: 'ru', title: 'Российские акции', country: 'RU', assets: [{ ticker: 'TATN' }] }],
    })

    expect(watchlist.sections[0].assets[0]).toMatchObject({
      id: 'TATN',
      name: 'TATN',
      ticker: 'TATN',
    })
  })

  // Второе обещание: новая страна — это только новый объект в sections[].
  it('новый раздел для другой страны появляется сам, флаг считается по коду', () => {
    const raw = watchlistRaw() as { sections: unknown[] }
    raw.sections.push({
      id: 'us',
      title: 'Акции США',
      country: 'US',
      currency: 'USD',
      yield_floor_pct: 1.0,
      assets: [{ id: 'KO', ticker: 'KO', name: 'Coca-Cola', type: 'stock' }],
    })

    const watchlist = parseWatchlist(raw)

    expect(watchlist.sections.map((section) => section.id)).toEqual(['ru', 'us'])
    expect(watchlist.sections[1].assets).toHaveLength(1)
    expect(flagOf(watchlist.sections[1].country)).toBe('🇺🇸')
    expect(flagOf(watchlist.sections[0].country)).toBe('🇷🇺')
  })

  it('битая бумага пропускается, остальные в разделе остаются', () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {})
    const raw = watchlistRaw() as { sections: { assets: unknown[] }[] }
    raw.sections[0].assets.push({ name: 'Без тикера и без id' })

    const assets = parseWatchlist(raw).sections[0].assets

    expect(assets.map((asset) => asset.id)).toEqual(['SBER', 'SIBN', 'PHOR', 'PARUS-SBL'])
    expect(warn).toHaveBeenCalled()
    warn.mockRestore()
  })

  it('поддержана и раскладка с общим массивом assets верхнего уровня', () => {
    const watchlist = parseWatchlist({
      sections: [
        { id: 'ru', title: 'Российские акции', country: 'RU' },
        { id: 'us', title: 'Акции США', country: 'US' },
      ],
      assets: [
        { id: 'SBER', ticker: 'SBER', name: 'Сбербанк', section: 'ru' },
        { id: 'KO', ticker: 'KO', name: 'Coca-Cola', section_id: 'us' },
      ],
    })

    expect(watchlist.sections[0].assets.map((asset) => asset.id)).toEqual(['SBER'])
    expect(watchlist.sections[1].assets.map((asset) => asset.id)).toEqual(['KO'])
  })

  it('неизвестный код страны даёт нейтральный значок вместо падения', () => {
    expect(flagOf('ZZZZ')).toBe('🌐')
    expect(flagOf('')).toBe('🌐')
  })

  it('watchlist без разделов — это ошибка, о ней надо сказать вслух', () => {
    expect(() => parseWatchlist({ sections: [] })).toThrow()
  })
})
