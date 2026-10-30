import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { App } from './App'
import { setupTelegram } from './telegram'
import './styles.css'

setupTelegram()

const container = document.getElementById('root')
if (!container) throw new Error('Не найден контейнер #root')

createRoot(container).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
