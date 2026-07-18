import { defineConfig, devices } from '@playwright/test'

process.env.NO_PROXY = ['127.0.0.1', 'localhost', process.env.NO_PROXY]
  .filter(Boolean)
  .join(',')
process.env.no_proxy = ['127.0.0.1', 'localhost', process.env.no_proxy]
  .filter(Boolean)
  .join(',')

export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  forbidOnly: Boolean(process.env.CI),
  retries: process.env.CI ? 1 : 0,
  workers: 1,
  reporter: 'list',

  use: {
    baseURL: 'http://127.0.0.1:5173',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
  },

  projects: [
    {
      name: 'chromium',
      use: {
        ...devices['Desktop Chrome'],
        launchOptions: {
          args: ['--allow-insecure-localhost'],
        },
      },
      testMatch: '**/*.spec.ts',
    },
    {
      name: 'firefox',
      use: {
        ...devices['Desktop Firefox'],
        launchOptions: {
          firefoxUserPrefs: {
            'network.proxy.type': 0,
          },
        },
      },
      testMatch: [
        '**/create-and-clarify.spec.ts',
        '**/generate-prd.spec.ts',
        '**/local-projects.spec.ts',
      ],
    },
  ],

  webServer: {
    command: 'npx vite --host 127.0.0.1 --port 5173',
    port: 5173,
    reuseExistingServer: false,
    cwd: '.',
  },
})
