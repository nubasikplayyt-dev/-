/**
 * Флаг эмодзи по коду страны ISO-3166 alpha-2.
 * Буквы кода переводятся в regional indicator symbols — новые страны
 * начинают работать без правок кода.
 */
export function flagOf(country: string): string {
  const code = country.trim().toUpperCase()
  if (!/^[A-Z]{2}$/.test(code)) return '🌐'
  const base = 0x1f1e6 - 'A'.charCodeAt(0)
  return String.fromCodePoint(...[...code].map((letter) => letter.charCodeAt(0) + base))
}
