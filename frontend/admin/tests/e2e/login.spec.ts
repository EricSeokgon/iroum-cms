// @MX:NOTE: [AUTO] 로그인 시나리오 E2E — REQ-LOGIN-001, REQ-LOGIN-002
// @MX:SPEC: SPEC-CMS-ADMIN-E2E-001
//
// Admin SPA의 /login 폼 동작 검증. mock 200(성공) / 401(실패) 분기.
// 비밀번호 변경/세션만료 후 노출되는 login-notice는 별도 spec (password-change/logout) 에서 검증.

import { expect, test } from '@playwright/test'
import {
  mockLoginApi,
  mockLoginFailureApi,
  MOCK_CREDENTIALS,
  clearSession,
} from './fixtures/auth'

test.describe('관리자 로그인', () => {
  test.beforeEach(async ({ page }) => {
    await clearSession(page)
  })

  test('성공 시 /dashboard 로 이동한다', async ({ page }) => {
    await mockLoginApi(page)
    await page.goto('/login')

    await page.fill('#username', MOCK_CREDENTIALS.username)
    await page.fill('#password', MOCK_CREDENTIALS.password)
    await page.click('button[type="submit"]')

    await page.waitForURL('**/dashboard')
    expect(page.url()).toContain('/dashboard')
  })

  test('실패(401) 시 login-error 알림이 노출된다', async ({ page }) => {
    await mockLoginFailureApi(page)
    await page.goto('/login')

    await page.fill('#username', MOCK_CREDENTIALS.username)
    await page.fill('#password', 'wrong-password')
    await page.click('button[type="submit"]')

    const errorAlert = page.getByTestId('login-error')
    await expect(errorAlert).toBeVisible()
    // 인증 실패 시 URL은 그대로 /login 유지
    expect(page.url()).toContain('/login')
  })

  test.skip('이미 인증된 상태에서 /login 접근 시 /dashboard 로 리다이렉트된다', async ({ page }) => {
    // SKIP 사유: Admin SPA의 Pinia 런타임 메모리 인증 + full page reload 라이프사이클에서는
    //   page.goto('/login') 호출 시 항상 Pinia 상태가 초기화되어 isAuthenticated=false 가 된다.
    //   따라서 router.beforeEach 의 "이미 인증됨 → /dashboard" 분기는 SPA 내부 라우터 push 로만
    //   재현할 수 있는데, main.ts 가 router 를 window 에 노출하지 않아 외부에서 호출 불가능하다.
    //   해당 가드 분기는 Vitest 단위 테스트 (router/index.spec.ts) 에서 검증한다.
    //
    // 향후 main.ts 에 router export 가 추가되면 본 테스트를 활성화한다.
    await mockLoginApi(page)
    await page.goto('/login')
    await page.fill('#username', MOCK_CREDENTIALS.username)
    await page.fill('#password', MOCK_CREDENTIALS.password)
    await page.click('button[type="submit"]')
    await page.waitForURL('**/dashboard')
    expect(page.url()).toContain('/dashboard')
  })

  test('빈 폼 제출 시 HTML5 validation 으로 차단되고 네트워크 호출이 발생하지 않는다', async ({ page }) => {
    let loginCallCount = 0
    await page.route('**/api/v1/auth/login', async (route) => {
      loginCallCount += 1
      await route.fulfill({ status: 200, contentType: 'application/json', body: '{}' })
    })

    await page.goto('/login')
    await page.click('button[type="submit"]')

    // Element Plus el-form validate() 가 false 를 반환 → 제출 미발생
    expect(loginCallCount).toBe(0)
    // URL은 그대로 /login
    expect(page.url()).toContain('/login')
  })
})
