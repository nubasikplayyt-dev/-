import { useState } from 'react'
import { colorForTicker, initialsOf } from '../../lib/hash'
import { fileUrl } from '../../config'

interface Props {
  ticker: string
  logo?: string
  large?: boolean
}

/**
 * Логотип бумаги. Путь берётся из поля `logo` в watchlist.json; если файла нет
 * или он не загрузился — рисуем круг с первыми буквами тикера, цвет по хэшу.
 */
export function Logo({ ticker, logo, large }: Props) {
  const [broken, setBroken] = useState(false)
  const size = large ? 'logo logo-lg' : 'logo'

  if (logo && !broken) {
    const source = /^https?:\/\//.test(logo) ? logo : fileUrl(logo)
    return (
      <img
        className={size}
        src={source}
        alt={ticker}
        loading="lazy"
        onError={() => setBroken(true)}
      />
    )
  }

  return (
    <div
      className={`${size} logo-fallback`}
      style={{ background: colorForTicker(ticker) }}
      aria-label={ticker}
      role="img"
    >
      {initialsOf(ticker)}
    </div>
  )
}
