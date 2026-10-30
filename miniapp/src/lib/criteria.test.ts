import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'
import { parseCriteriaMarkdown } from './criteria'
import { formatDate } from './date'
import { colorForTicker, initialsOf } from './hash'

describe('parseCriteriaMarkdown', () => {
  it('достаёт все критерии из мокового CRITERIA.md', () => {
    const markdown = readFileSync(
      fileURLToPath(new URL('../mock/CRITERIA.md', import.meta.url)),
      'utf8',
    )

    const dictionary = parseCriteriaMarkdown(markdown)

    expect(Object.keys(dictionary).sort()).toEqual([
      'C1', 'C2', 'C3', 'C4', 'C5', 'C6', 'C7', 'C8', 'P1', 'P2', 'P3',
    ].sort())
    expect(dictionary.C1.title).toBe('Оценка по прибыли')
    expect(dictionary.C1.text).toContain('P/E ниже среднего по сектору')
    expect(dictionary.P3.text).toContain('не дублирует риск')
  })

  it('понимает разные разделители в заголовке', () => {
    const dictionary = parseCriteriaMarkdown(
      ['### C1. Про прибыль', 'тело', '', '### **P2** — Ликвидность', 'другое тело'].join('\n'),
    )

    expect(dictionary.C1.title).toBe('Про прибыль')
    expect(dictionary.P2.title).toBe('Ликвидность')
    expect(dictionary.P2.text).toBe('другое тело')
  })

  it('без CRITERIA.md словарь просто пустой', () => {
    expect(parseCriteriaMarkdown('')).toEqual({})
  })
})

describe('formatDate', () => {
  it('выдаёт формат «30 окт 2026»', () => {
    expect(formatDate('2026-10-30')).toBe('30 окт 2026')
    expect(formatDate('2026-01-05T12:00:00Z')).toBe('5 янв 2026')
  })

  it('непонятную дату возвращает как есть, пустую — прочерком', () => {
    expect(formatDate('когда-нибудь')).toBe('когда-нибудь')
    expect(formatDate(undefined)).toBe('—')
  })
})

describe('заглушка логотипа', () => {
  it('цвет зависит только от тикера', () => {
    expect(colorForTicker('SBER')).toBe(colorForTicker('sber'))
    expect(colorForTicker('SBER')).not.toBe(colorForTicker('LKOH'))
  })

  it('берёт первые две буквы тикера', () => {
    expect(initialsOf('SBER')).toBe('SB')
    expect(initialsOf('KO')).toBe('KO')
    expect(initialsOf('—')).toBe('??')
  })
})
