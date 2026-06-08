// SPEC-CMS-PUBLIC-E2E-001 — 에러 페이지 E2E
// REQ 매핑: REQ-E2E-010 (인증 리다이렉트), REQ-E2E-011 (에러 라우팅)
// 인수 시나리오: S6 (인증 리다이렉트), S7 (404/403/500/Maintenance)
//
// 에러 라우트 (router/index.ts):
//   /maintenance → MaintenanceView (data-testid="maintenance-view"), noLayout: true
//   /error/403  → ForbiddenView   (data-testid="forbidden-view"),   noLayout: true
//   /error/500  → ServerErrorView (data-testid="server-error-view"), noLayout: true
//   /:pathMatch(.*)* → NotFoundView (data-testid="not-found-view")
import { test, expect } from '@playwright/test'
import { clearAuth, buildLoginRedirectUrl } from './fixtures/auth'
import { mockAllApis } from './fixtures/api-mocks'

test.describe('에러 페이지 (NotFoundView / ForbiddenView / ServerErrorView / MaintenanceView)', () => {
  test.beforeEach(async ({ page }) => {
    await mockAllApis(page)
    await clearAuth(page)
  })

  test('S7: 등록되지 않은 경로 접근 시 NotFoundView 렌더링', async ({ page }) => {
    await page.goto('/non-existent-path-xyz-12345')
    await expect(page.getByTestId('not-found-view')).toBeVisible()
    await expect(page.getByTestId('not-found-home')).toBeVisible()
    await expect(page.getByTestId('not-found-back')).toBeVisible()
    await expect(page.getByTestId('not-found-search')).toBeVisible()
  })

  test('/error/403 접근 시 ForbiddenView 렌더링', async ({ page }) => {
    await page.goto('/error/403')
    await expect(page.getByTestId('forbidden-view')).toBeVisible()
    await expect(page.getByTestId('forbidden-home')).toBeVisible()
  })

  test('/error/500 접근 시 ServerErrorView 렌더링', async ({ page }) => {
    await page.goto('/error/500')
    await expect(page.getByTestId('server-error-view')).toBeVisible()
    await expect(page.getByTestId('server-error-home')).toBeVisible()
    await expect(page.getByTestId('server-error-retry')).toBeVisible()
  })

  test('/maintenance 접근 시 MaintenanceView 렌더링', async ({ page }) => {
    await page.goto('/maintenance')
    await expect(page.getByTestId('maintenance-view')).toBeVisible()
  })

  test('S6 (REQ-E2E-010): 비인증 상태에서 /qnas/new 접근 시 /login?redirect= 리다이렉트', async ({
    page,
  }) => {
    // beforeEach 의 clearAuth 로 토큰 없음 보장
    await page.goto('/qnas/new')

    // 라우터 가드가 동기적으로 동작 — URL 매칭 대기
    await expect(page).toHaveURL(buildLoginRedirectUrl('/qnas/new'), { timeout: 5_000 })
  })
})
