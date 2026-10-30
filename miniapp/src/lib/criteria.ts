export type CriteriaDictionary = Record<string, { title: string; text: string }>

/**
 * Достаёт описания критериев из CRITERIA.md.
 *
 * В репозитории данных они лежат таблицами:
 *   | C1 | Вечная тема | Бизнес, нужный людям и 500 лет назад… |
 *   | P1 | Не покупать, если доходность ниже порога… |
 * Трёхколоночная строка даёт название и пояснение, двухколоночная — только
 * правило. На случай другой разметки понимаются и заголовки вида «## C1 — …».
 */
export function parseCriteriaMarkdown(markdown: string): CriteriaDictionary {
  const dictionary: CriteriaDictionary = {}

  for (const [code, entry] of Object.entries(fromTables(markdown))) dictionary[code] = entry
  // Заголовки важнее: если автор расписал критерий подробно, берём его текст.
  for (const [code, entry] of Object.entries(fromHeadings(markdown))) dictionary[code] = entry

  return dictionary
}

const CODE = /^\*{0,2}([CP]\d{1,2})\*{0,2}$/

function fromTables(markdown: string): CriteriaDictionary {
  const dictionary: CriteriaDictionary = {}

  for (const line of markdown.split('\n')) {
    const trimmed = line.trim()
    if (!trimmed.startsWith('|')) continue

    const cells = trimmed
      .replace(/^\|/, '')
      .replace(/\|$/, '')
      .split('|')
      .map((cell) => cell.trim())
    if (cells.length < 2) continue

    const code = CODE.exec(cells[0])
    if (!code) continue

    // Три колонки — «код | название | пояснение», две — «код | правило».
    const [title, text] =
      cells.length >= 3 ? [cells[1], cells.slice(2).join(' ')] : [cells[1], '']

    dictionary[code[1].toUpperCase()] = {
      title: stripMarkdown(title) || code[1],
      text: stripMarkdown(text),
    }
  }

  return dictionary
}

function fromHeadings(markdown: string): CriteriaDictionary {
  const dictionary: CriteriaDictionary = {}
  let current: { code: string; level: number; title: string; body: string[] } | null = null

  const flush = () => {
    if (!current) return
    dictionary[current.code] = { title: current.title, text: current.body.join('\n').trim() }
    current = null
  }

  for (const line of markdown.split('\n')) {
    const heading = /^(#{1,6})\s+(.*)$/.exec(line)
    if (heading) {
      const level = heading[1].length
      const text = heading[2].trim()
      const match = /^\*{0,2}([CP]\d{1,2})\*{0,2}\s*[—–\-.:)]?\s*(.*)$/.exec(text)
      if (match) {
        flush()
        current = { code: match[1].toUpperCase(), level, title: match[2].trim() || match[1], body: [] }
        continue
      }
      if (current && level <= current.level) flush()
      continue
    }
    if (current) current.body.push(line)
  }
  flush()

  return dictionary
}

/** Убирает жирный, курсив и обратные кавычки — в списке они только мешают. */
function stripMarkdown(value: string): string {
  return value
    .replace(/\*\*(.+?)\*\*/g, '$1')
    .replace(/`(.+?)`/g, '$1')
    .replace(/\*(.+?)\*/g, '$1')
    .trim()
}
