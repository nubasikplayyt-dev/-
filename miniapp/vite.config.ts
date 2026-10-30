import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'

// base нужен для GitHub Pages: сайт живёт в подпапке /<repo>/.
// Значение подставляет workflow через переменную окружения PAGES_BASE.
export default defineConfig({
  base: process.env.PAGES_BASE ?? '/',
  plugins: [react()],
  test: {
    environment: 'node',
    include: ['src/**/*.test.ts'],
  },
})
