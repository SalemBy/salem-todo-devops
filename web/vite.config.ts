/// <reference types="vitest/config" />
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'
import { apiProxy } from './src/proxy.ts'

// One origin for the browser: the session cookie and CSRF work without CORS.
const server = {
  host: '127.0.0.1',
  port: 3000,
  strictPort: true,
  proxy: apiProxy(process.env.API_URL ?? 'http://127.0.0.1:8080'),
}

export default defineConfig({
  plugins: [react()],
  server,
  preview: server,
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    include: ['src/**/*.test.{ts,tsx}'],
  },
})
