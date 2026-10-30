import {
  backButton,
  init,
  isTMA,
  miniApp,
  openLink,
  retrieveLaunchParams,
  themeParams,
  viewport,
} from '@telegram-apps/sdk-react'

let ready = false

/**
 * Поднимает SDK Telegram. Вне Telegram (обычный браузер, `npm run dev`)
 * тихо переходит в обычный веб-режим: приложение должно открываться и так.
 */
export function setupTelegram(): void {
  if (ready) return
  ready = true

  if (!isTMA()) {
    document.documentElement.dataset.env = 'browser'
    return
  }

  try {
    init()
    miniApp.mountSync()
    themeParams.mountSync()
    // Цвета Telegram попадают в CSS как --tg-theme-* и подхватываются стилями.
    themeParams.bindCssVars()
    miniApp.bindCssVars()
    if (viewport.mount.isAvailable()) {
      void viewport.mount().then(() => viewport.bindCssVars())
    }
    backButton.mount()
    miniApp.ready()
    document.documentElement.dataset.env = 'telegram'
  } catch (error) {
    console.warn('SDK Telegram не поднялся, работаем как обычная страница', error)
    document.documentElement.dataset.env = 'browser'
  }
}

/** Тёмная тема: спрашиваем Telegram, иначе — системную настройку. */
export function isDarkTheme(): boolean {
  try {
    if (themeParams.isMounted()) return Boolean(themeParams.isDark())
  } catch {
    // ниже
  }
  return window.matchMedia?.('(prefers-color-scheme: dark)').matches ?? false
}

/** Параметр из deep link `?startapp=<asset_id>` — открывает сразу экран бумаги. */
export function startParam(): string | null {
  try {
    const params = retrieveLaunchParams()
    const value = params.tgWebAppStartParam
    return value ? String(value) : null
  } catch {
    // Вне Telegram поддерживаем тот же параметр в адресной строке.
    return new URLSearchParams(window.location.search).get('startapp')
  }
}

/** Подписка на кнопку «назад» с отпиской — для useEffect. */
export function onBackButton(handler: () => void): () => void {
  try {
    if (!backButton.isMounted()) return () => {}
    backButton.show()
    const off = backButton.onClick(handler)
    return () => {
      off()
      backButton.hide()
    }
  } catch (error) {
    console.warn('Кнопка «назад» недоступна', error)
    return () => {}
  }
}

/** Ссылки на источники открываем во внешнем браузере, а не внутри Mini App. */
export function openExternal(url: string): void {
  try {
    if (isTMA()) {
      openLink(url)
      return
    }
  } catch {
    // ниже
  }
  window.open(url, '_blank', 'noopener,noreferrer')
}
