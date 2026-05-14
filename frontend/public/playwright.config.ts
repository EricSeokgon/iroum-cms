// @MX:NOTE: [AUTO] E2E 테스트 설정 진입점 — webServer 포트 5174, chromium 단일 브라우저 (Phase 1).
// @MX:SPEC: SPEC-CMS-PUBLIC-E2E-001 REQ-E2E-001, REQ-E2E-003
//
// Playwright 1.48 설정.
// - testDir: tests/e2e (Vitest의 tests/* 와 격리)
// - baseURL: dev 서버(5174)
// - webServer: 로컬에서 pnpm run dev 자동 기동, CI에서는 기존 서버 재사용
// - CI에서만 retries 2 / workers 1 / forbidOnly 적용 (REQ-E2E-003)
// - reporter: html(아티팩트) + list(콘솔 가독성) + json(파싱용)
import { defineConfig, devices } from '@playwright/test'

export default defineConfig({
  testDir: './tests/e2e',
  // 백엔드 의존성 (localhost:8080) 안정화를 위해 순차 실행
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 1 : undefined,
  reporter: [
    ['html', { outputFolder: 'playwright-report', open: 'never' }],
    ['list'],
    ['json', { outputFile: 'test-results/results.json' }],
  ],
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:5174',
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
    url: 'http://localhost:5174',
    reuseExistingServer: !process.env.CI,
    timeout: 120_000,
  },
})
