// @MX:NOTE: [AUTO] 라우터 가드 검증 — REQ-GUARD-001
// @MX:SPEC: SPEC-CMS-ADMIN-E2E-001
//
// router.beforeEach: requiresAuth 라우트에 미인증으로 접근 시
// /login?redirect={to.fullPath} 로 리다이렉트되어야 한다.
// fullPath는 URL-encode 되지 않은 원본 경로(슬래시 유지).

import { expect, test } from '@playwright/test'
import { clearSession, mockLoginApi, MOCK_CREDENTIALS } from './fixtures/auth'

test.describe('인증 라우트 가드', () => {
  test.beforeEach(async ({ page }) => {
    await clearSession(page)
  })

  test('미인증 상태에서 /dashboard 접근 → /login?redirect=/dashboard', async ({ page }) => {
    await page.goto('/dashboard')
    await page.waitForURL(/\/login\?redirect=/)
    expect(page.url()).toMatch(/\/login\?redirect=\/dashboard/)
  })

  test('미인증 상태에서 /users 접근 → /login?redirect=/users', async ({ page }) => {
    await page.goto('/users')
    await page.waitForURL(/\/login\?redirect=/)
    expect(page.url()).toMatch(/\/login\?redirect=\/users/)
  })

  test('미인증 상태에서 /roles 접근 → /login?redirect=/roles', async ({ page }) => {
    await page.goto('/roles')
    await page.waitForURL(/\/login\?redirect=/)
    expect(page.url()).toMatch(/\/login\?redirect=\/roles/)
  })

  test('redirect query 가 있는 경우 로그인 성공 후 원래 경로로 이동한다', async ({ page }) => {
    await mockLoginApi(page)
    // /users 보호 라우트에 진입 → /login?redirect=/users 도달
    await page.goto('/users')
    await page.waitForURL(/\/login\?redirect=\/users/)

    // 로그인 폼 제출
    await page.fill('#username', MOCK_CREDENTIALS.username)
    await page.fill('#password', MOCK_CREDENTIALS.password)
    await page.click('button[type="submit"]')

    // redirect query 우선 → /users 로 이동
    await page.waitForURL('**/users')
    expect(page.url()).toContain('/users')
  })
})
