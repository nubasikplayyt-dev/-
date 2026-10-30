/**
 * Подписи ключевых чисел события. Агент пишет их техническими ключами
 * (`net_debt_to_ebitda`, `yield_pct`), а показывать их надо по-русски.
 * Незнакомый ключ не теряется: из него делается читаемая подпись,
 * а суффикс единицы измерения переносится к значению.
 */
const LABELS: Record<string, string> = {
  dividend_rub: 'Дивиденд',
  payout_rub_month: 'Выплата в месяц',
  payout_pct: 'Payout',
  yield_pct: 'Доходность',
  price_rub: 'Цена',
  nav_rub: 'СЧА пая',
  premium_pct: 'Премия к СЧА',
  discount_pct: 'Дисконт к СЧА',
  net_debt_bn_rub: 'Чистый долг',
  net_debt_to_ebitda: 'ЧД / EBITDA',
  fcf_bn_rub: 'FCF',
  net_profit_bn_rub: 'Чистая прибыль',
  revenue_bn_rub: 'Выручка',
  ebitda_bn_rub: 'EBITDA',
  capex_bn_rub: 'Капекс',
  roe_pct: 'ROE',
  cost_of_risk_pct: 'Стоимость риска',
  coupon_pct: 'Купон',
  volume_bn_rub: 'Объём',
  brent_usd: 'Brent',
  urals_discount_usd: 'Дисконт Urals',
  sulphur_usd_t: 'Сера',
  dap_usd_t: 'DAP',
  usd_rub: 'USD/RUB',
}

/** Суффикс ключа → единица измерения рядом со значением. */
const UNITS: [RegExp, string][] = [
  [/_bn_rub$/, 'млрд ₽'],
  [/_rub_month$/, '₽/мес'],
  [/_rub$/, '₽'],
  [/_usd_t$/, '$/т'],
  [/_usd$/, '$'],
  [/_pct$/, '%'],
]

export interface DisplayMetric {
  label: string
  value: string
}

export function describeMetric(name: string, value: string): DisplayMetric {
  const unit = UNITS.find(([pattern]) => pattern.test(name))?.[1] ?? ''
  // Процент пишется вплотную к числу, остальные единицы — через пробел.
  const separator = unit === '%' ? '' : ' '
  return {
    label: LABELS[name] ?? humanize(name),
    value: unit ? `${value}${separator}${unit}` : value,
  }
}

/** `net_debt_to_ebitda` → «Net debt to ebitda»: лучше, чем сырой ключ. */
function humanize(name: string): string {
  const words = name.replace(/_(bn|mn)?_?(rub|usd|pct|t|month)$/i, '').split('_').filter(Boolean)
  if (words.length === 0) return name
  const text = words.join(' ')
  return text.charAt(0).toUpperCase() + text.slice(1)
}
