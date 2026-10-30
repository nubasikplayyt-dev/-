import { useEffect, useState } from 'react'
import { AssetScreen } from './ui/AssetScreen'
import { HomeScreen } from './ui/HomeScreen'
import { useData } from './ui/useData'
import { config } from './config'
import { startParam } from './telegram'

export function App() {
  const { data, error, loading, refresh } = useData()
  // Deep link startapp=<asset_id> открывает сразу экран бумаги.
  const [assetId, setAssetId] = useState<string | null>(() => startParam())

  // Адресную строку держим в синхроне, чтобы «назад» в браузере тоже работал.
  useEffect(() => {
    const url = new URL(window.location.href)
    if (assetId) url.searchParams.set('startapp', assetId)
    else url.searchParams.delete('startapp')
    window.history.replaceState(null, '', url)
  }, [assetId])

  if (error) {
    return (
      <div className="app">
        <div className="error-box">
          <b>Данные не загрузились.</b>
          <div className="small muted" style={{ marginTop: 6 }}>
            {error}
          </div>
          <div className="small muted" style={{ marginTop: 6 }}>
            Репозиторий данных: {config.owner}/{config.repo}@{config.branch}
            {config.proxy ? ' (через прокси)' : ''}
          </div>
          <div style={{ marginTop: 12 }}>
            <button type="button" className="ghost-button" onClick={refresh}>
              Повторить
            </button>
          </div>
        </div>
      </div>
    )
  }

  if (!data) {
    return (
      <div className="app">
        <div className="card small muted">Загружаю данные…</div>
      </div>
    )
  }

  if (assetId) {
    return <AssetScreen data={data} assetId={assetId} onBack={() => setAssetId(null)} />
  }

  return (
    <HomeScreen data={data} onOpenAsset={setAssetId} onRefresh={refresh} loading={loading} />
  )
}
