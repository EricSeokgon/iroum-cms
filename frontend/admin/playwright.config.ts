// @MX:NOTE: [AUTO] Admin SPA E2E 설정 — port 5173, Pinia 런타임 인증 (Public SPA 5174와 구분)
// @MX:SPEC: SPEC-CMS-ADMIN-E2E-001 REQ-INFRA-001, REQ-INFRA-002
//
// Playwright 1.48 설정.
// - testDir: tests/e2e (Vitest의 tests/* 와 격리)
// - baseURL: dev 서버(5173)
// - webServer: 로컬에서 pnpm run dev 자동 기동, CI에서는 기존 서버 재사용
// - CI에서만 retries 2 / workers 1 / forbidOnly 적용
// - reporter: html(아티팩트) + list(콘솔 가독성) + json(파싱용)
import { defineConfig, devices } from '@playwright/test'

export default defineConfig({
  testDir: './tests/e2e',
  // page.route() 모킹 기반이지만 동일 webServer를 공유하므로 순차 실행
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 1 : undefined,
  reporter: [
    ['html', { outputFolder: 'playwright-report', open: 'never' }],
    ['list'],
    ['json', { outputFile: 'playwright-report/results.json' }],
  ],
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:5173',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
    locale: 'ko-KR',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
  webServer: {
    command: 'pnpm run dev',
    url: 'http://localhost:5173',
    reuseExistingServer: !process.env.CI,
    timeout: 120_000,
  },
})
