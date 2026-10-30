const MONTHS = ['янв', 'фев', 'мар', 'апр', 'мая', 'июн', 'июл', 'авг', 'сен', 'окт', 'ноя', 'дек']

/** «2026-10-30» → «30 окт 2026». Непонятную строку возвращаем как есть. */
export function formatDate(value: string | undefined): string {
  if (!value) return '—'
  const match = /^(\d{4})-(\d{2})-(\d{2})/.exec(value.trim())
  if (!match) return value
  const [, year, month, day] = match
  const monthName = MONTHS[Number(month) - 1]
  if (!monthName) return value
  return `${Number(day)} ${monthName} ${year}`
}

/** Ключ сортировки: дата без времени, чтобы события одного дня не прыгали. */
export function dateKey(value: string | undefined): string {
  return (value ?? '').slice(0, 10)
}
