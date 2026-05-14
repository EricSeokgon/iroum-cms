// @MX:NOTE: [AUTO] 에러 페이지 진입 검증 — REQ-ERROR-001
// @MX:SPEC: SPEC-CMS-ADMIN-E2E-001
//
// /:pathMatch(.*)* 가 NotFoundView.vue 로 매칭되는지 검증.
// NotFoundView 는 public 라우트가 아니지만 meta.requiresAuth 도 false (메타 미지정).

import { expect, test } from '@playwright/test'
import { clearSession } from './fixtures/auth'

test.describe('에러 페이지', () => {
  test.beforeEach(async ({ page }) => {
    await clearSession(page)
  })

  test('존재하지 않는 경로 접근 시 NotFound 가 렌더된다', async ({ page }) => {
    // NotFound 라우트는 requiresAuth 가 설정되지 않은 publicly accessible 라우트
    await page.goto('/this-route-does-not-exist-xyz')
    await expect(page.getByTestId('not-found')).toBeVisible()
  })

  test('미인증 상태에서 보호 라우트 접근 시 /login 으로 리다이렉트된다', async ({ page }) => {
    await page.goto('/dashboard')
    await page.waitForURL(/\/login/)
    expect(page.url()).toContain('/login')
  })
})
