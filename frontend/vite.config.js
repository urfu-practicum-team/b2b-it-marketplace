import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

export default defineConfig({
  plugins: [react()],
  test: {
    coverage: {
      provider: 'v8',
      include: ['src/**/*.{js,jsx}'],
      exclude: ['**/*.{test,spec}.{js,jsx}', '**/__tests__/**'],
      reporter: ['json-summary'],
    },
  },
  server: {
    proxy: {
      '/api': 'http://127.0.0.1:18080',
    },
  },
})
