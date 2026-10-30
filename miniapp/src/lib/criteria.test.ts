import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'
import { parseCriteriaMarkdown } from './criteria'
import { formatDate } from './date'
import { colorForTicker, initialsOf } from './hash'
import { describeMetric } from './metrics'

const criteriaMarkdown = readFileSync(
  fileURLToPath(new URL('../mock/CRITERIA.md', import.meta.url)),
  'utf8',
)

describe('parseCriteriaMarkdown', () => {
  // В репозитории данных критерии лежат таблицами, а не заголовками.
  it('достаёт все одиннадцать кодов из CRITERIA.md репозитория', () => {
    const dictionary = parseCriteriaMarkdown(criteriaMarkdown)

    expect(Object.keys(dictionary).sort()).toEqual(
      ['C1', 'C2', 'C3', 'C4', 'C5', 'C6', 'C7', 'C8', 'P1', 'P2', 'P3'].sort(),
    )
  })

  it('трёхколоночная строка даёт название и пояснение', () => {
    const dictionary = parseCriteriaMarkdown(criteriaMarkdown)

    expect(dictionary.C1.title).toBe('Вечная тема')
    expect(dictionary.C1.text).toContain('нужный людям и 500 лет назад')
    expect(dictionary.C8.title).toBe('Экспортёр (плюс)')
  })

  it('двухколоночная строка даёт правило целиком', () => {
    const dictionary = parseCriteriaMarkdown(criteriaMarkdown)

    expect(dictionary.P1.title).toContain('дивдоходность ниже порога')
    expect(dictionary.P3.title).toContain('критерий Штернлихта')
  })

  it('жирный и обратные кавычки из таблицы убираются', () => {
    expect(parseCriteriaMarkdown(criteriaMarkdown).P1.title).not.toContain('**')
  })

  it('понимает и разметку заголовками, если она появится', () => {
    const dictionary = parseCriteriaMarkdown(
      ['### C1. Про прибыль', 'тело', '', '### **P2** — Ликвидность', 'другое тело'].join('\n'),
    )

    expect(dictionary.C1.title).toBe('Про прибыль')
    expect(dictionary.P2.title).toBe('Ликвидность')
    expect(dictionary.P2.text).toBe('другое тело')
  })

  it('заголовок важнее строки таблицы для того же кода', () => {
    const dictionary = parseCriteriaMarkdown(
      ['| C1 | Коротко | из таблицы |', '', '## C1 — Подробно', 'из заголовка'].join('\n'),
    )

    expect(dictionary.C1.title).toBe('Подробно')
    expect(dictionary.C1.text).toBe('из заголовка')
  })

  it('без CRITERIA.md словарь просто пустой', () => {
    expect(parseCriteriaMarkdown('')).toEqual({})
  })

  it('строка заголовка таблицы критерием не считается', () => {
    expect(parseCriteriaMarkdown('| Код | Критерий | Что проверять |\n|---|---|---|')).toEqual({})
  })
})

describe('describeMetric', () => {
  it('переводит технические ключи агента и добавляет единицы', () => {
    expect(describeMetric('dividend_rub', '37,64')).toEqual({ label: 'Дивиденд', value: '37,64 ₽' })
    expect(describeMetric('yield_pct', '12,7')).toEqual({ label: 'Доходность', value: '12,7%' })
    expect(describeMetric('net_debt_bn_rub', '325')).toEqual({
      label: 'Чистый долг',
      value: '325 млрд ₽',
    })
    expect(describeMetric('net_debt_to_ebitda', '2,1')).toEqual({
      label: 'ЧД / EBITDA',
      value: '2,1',
    })
  })

  it('незнакомый ключ не теряется, а становится читаемым', () => {
    expect(describeMetric('some_new_metric_pct', '5')).toEqual({
      label: 'Some new metric',
      value: '5%',
    })
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
  // Файлов в logos/ в репозитории пока нет — заглушка работает всегда.
  it('цвет зависит только от тикера', () => {
    expect(colorForTicker('SBER')).toBe(colorForTicker('sber'))
    expect(colorForTicker('SBER')).not.toBe(colorForTicker('SIBN'))
  })

  it('берёт первые две буквы тикера', () => {
    expect(initialsOf('SBER')).toBe('SB')
    expect(initialsOf('PARUS-SBL')).toBe('PA')
    expect(initialsOf('—')).toBe('??')
  })
})
