import {defineConfig} from '@playwright/test'

export default defineConfig({
    testDir: './tests/smoke',
    timeout: 60_000,
    fullyParallel: false,
    retries: 0,
    reporter: 'line',
    outputDir: './.playwright/test-results',
    use: {
        baseURL: process.env.SMOKE_BASE_URL || 'http://localhost:5173',
        trace: 'retain-on-failure',
        screenshot: 'only-on-failure',
        video: 'retain-on-failure',
    },
})
