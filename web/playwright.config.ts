import { defineConfig, devices } from '@playwright/test'

const baseURL = 'http://127.0.0.1:3000'

/**
 * End-to-end tests against the real API: it must already run on 8080 (with Mailpit for verification
 * emails). Locally they use the dev server, in CI the production build through `vite preview`.
 */
export default defineConfig({
  testDir: './e2e',
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : 'list',
  use: { baseURL, trace: 'retain-on-failure' },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: {
    command: process.env.CI ? 'npm run preview' : 'npm run dev',
    url: baseURL,
    reuseExistingServer: !process.env.CI,
    timeout: 60_000,
  },
})
