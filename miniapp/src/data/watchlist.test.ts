import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { describe, expect, it, vi } from 'vitest'
import { parseWatchlist } from './schema'
import { flagOf } from '../lib/flag'

const watchlistRaw = () =>
  JSON.parse(
    readFileSync(fileURLToPath(new URL('../mock/config/watchlist.json', import.meta.url)), 'utf8'),
  ) as Record<string, unknown>

describe('parseWatchlist', () => {
  it('читает моковый watchlist', () => {
    const watchlist = parseWatchlist(watchlistRaw())

    expect(watchlist.sections).toHaveLength(1)
    expect(watchlist.sections[0].id).toBe('ru')
    expect(watchlist.sections[0].assets.map((asset) => asset.id)).toEqual(['sber', 'lkoh', 'gmkn'])
  })

  // Главное обещание из README: новая бумага подхватывается без правок кода.
  it('новая бумага в assets[] появляется сама, без изменения кода', () => {
    const raw = watchlistRaw() as { sections: { assets: unknown[] }[] }
    raw.sections[0].assets.push({
      id: 'moex',
      ticker: 'MOEX',
      name: 'Московская биржа',
      short_name: 'Мосбиржа',
      isin: 'RU000A0JR4A1',
      logo: 'assets/logos/moex.png',
    })

    const watchlist = parseWatchlist(raw)
    const assets = watchlist.sections[0].assets

    expect(assets).toHaveLength(4)
    expect(assets[3]).toMatchObject({ id: 'moex', ticker: 'MOEX', short_name: 'Мосбиржа' })
  })

  it('бумага без short_name и logo читается, экран обойдётся заглушкой', () => {
    const raw = watchlistRaw() as { sections: { assets: unknown[] }[] }
    raw.sections[0].assets.push({ id: 'rosn', ticker: 'ROSN', name: 'Роснефть' })

    const asset = parseWatchlist(raw).sections[0].assets[3]

    expect(asset.short_name).toBeUndefined()
    expect(asset.logo).toBeUndefined()
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

  // Второе обещание README: новая страна — это только новый объект в sections[].
  it('новый раздел для другой страны появляется сам, флаг считается по коду', () => {
    const raw = watchlistRaw() as { sections: unknown[] }
    raw.sections.push({
      id: 'us',
      title: 'Акции США',
      country: 'US',
      currency: 'USD',
      yield_floor_pct: 3,
      assets: [{ id: 'ko', ticker: 'KO', name: 'Coca-Cola' }],
    })

    const watchlist = parseWatchlist(raw)

    expect(watchlist.sections.map((section) => section.id)).toEqual(['ru', 'us'])
    expect(flagOf(watchlist.sections[1].country)).toBe('🇺🇸')
    expect(flagOf(watchlist.sections[0].country)).toBe('🇷🇺')
  })

  it('битая бумага пропускается, остальные в разделе остаются', () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {})
    const raw = watchlistRaw() as { sections: { assets: unknown[] }[] }
    raw.sections[0].assets.push({ name: 'Без тикера и без id' })

    const assets = parseWatchlist(raw).sections[0].assets

    expect(assets.map((asset) => asset.id)).toEqual(['sber', 'lkoh', 'gmkn'])
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
        { id: 'sber', ticker: 'SBER', name: 'Сбербанк', section: 'ru' },
        { id: 'ko', ticker: 'KO', name: 'Coca-Cola', section_id: 'us' },
      ],
    })

    expect(watchlist.sections[0].assets.map((asset) => asset.id)).toEqual(['sber'])
    expect(watchlist.sections[1].assets.map((asset) => asset.id)).toEqual(['ko'])
  })

  it('неизвестный код страны даёт нейтральный значок вместо падения', () => {
    expect(flagOf('ZZZZ')).toBe('🌐')
    expect(flagOf('')).toBe('🌐')
  })

  it('watchlist без разделов — это ошибка, о ней надо сказать вслух', () => {
    expect(() => parseWatchlist({ sections: [] })).toThrow()
  })
})
