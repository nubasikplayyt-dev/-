export type CriteriaDictionary = Record<string, { title: string; text: string }>

/**
 * Достаёт описания критериев из CRITERIA.md. Ищется заголовок любого уровня,
 * начинающийся с кода (C1, P3 и т. п.); телом считается всё до следующего
 * заголовка того же или более высокого уровня.
 */
export function parseCriteriaMarkdown(markdown: string): CriteriaDictionary {
  const dictionary: CriteriaDictionary = {}
  const lines = markdown.split('\n')

  let current: { code: string; level: number; title: string; body: string[] } | null = null
  const flush = () => {
    if (!current) return
    dictionary[current.code] = {
      title: current.title,
      text: current.body.join('\n').trim(),
    }
    current = null
  }

  for (const line of lines) {
    const heading = /^(#{1,6})\s+(.*)$/.exec(line)
    if (heading) {
      const level = heading[1].length
      const text = heading[2].trim()
      const code = /^\*{0,2}([CP]\d{1,2})\*{0,2}\s*[—–\-.:)]?\s*(.*)$/.exec(text)
      if (code) {
        flush()
        current = { code: code[1].toUpperCase(), level, title: code[2].trim() || code[1], body: [] }
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
