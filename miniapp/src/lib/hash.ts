/** Устойчивый хэш строки — нужен, чтобы цвет заглушки логотипа не менялся. */
export function hashString(value: string): number {
  let hash = 0
  for (let index = 0; index < value.length; index += 1) {
    hash = (hash << 5) - hash + value.charCodeAt(index)
    hash |= 0
  }
  return Math.abs(hash)
}

/** Цвет заглушки логотипа по тикеру: один тикер — всегда один цвет. */
export function colorForTicker(ticker: string): string {
  const hue = hashString(ticker.toUpperCase()) % 360
  return `hsl(${hue} 52% 42%)`
}

/** Первые буквы тикера для заглушки. */
export function initialsOf(ticker: string): string {
  return ticker.replace(/[^\p{L}\p{N}]/gu, '').slice(0, 2).toUpperCase() || '??'
}
