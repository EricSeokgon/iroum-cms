// @MX:ANCHOR: [AUTO] loginAsSuperAdmin — admin E2E의 모든 인증 테스트에서 공유하는 auth fixture
// @MX:REASON: fan_in >= 7 — dashboard/users/roles/notices/password/logout/a11y spec에서 사용
// @MX:SPEC: SPEC-CMS-ADMIN-E2E-001 REQ-AUTH-001, REQ-AUTH-002
//
// Admin SPA는 Pinia 런타임 메모리 인증 (Public SPA의 localStorage 와 다름).
// 토큰은 useAuthStore().accessToken ref에만 저장되고 페이지 reload 시 소실되므로
// localStorage 주입 전략을 사용할 수 없다.
//
// 대안: page.route()로 POST /api/v1/auth/login 인터셉트 → mock JWT 반환 →
//       /login 폼 fill & submit → 라우터가 /dashboard 로 이동.
//       useAuthStore.login() 내부의 decodeJwt() 가 페이로드를 파싱하여 user 상태를 설정한다.
//
// Mock JWT 구조:
//   header:    eyJhbGciOiJIUzI1NiJ9               (HS256, base64url)
//   payload:   { sub:'admin', uid:1, roles:['SUPER_ADMIN'],
//                exp:9999999999, iat:1000000000 } (base64url)
//   signature: 'mock-signature'                   (서명 검증은 서버 책임 — FE는 페이로드만 디코드)

import type { Page } from '@playwright/test'

// Node.js (Playwright 러너) 컨텍스트에서는 Buffer가 가용. atob/btoa 미사용.
const MOCK_PAYLOAD_OBJ = {
  sub: 'admin',
  uid: 1,
  roles: ['SUPER_ADMIN'],
  exp: 9999999999,
  iat: 1000000000,
}

const MOCK_PAYLOAD_B64URL = Buffer.from(JSON.stringify(MOCK_PAYLOAD_OBJ)).toString('base64url')

/** decodeJwt() 가 파싱 가능한 형태의 mock JWT (header.payload.signature) */
export const MOCK_JWT = `eyJhbGciOiJIUzI1NiJ9.${MOCK_PAYLOAD_B64URL}.mock-signature`

/** /api/v1/auth/login mock 응답 페이로드 (shared/types/api.ts LoginResponse 구조) */
export const MOCK_LOGIN_RESPONSE = {
  accessToken: MOCK_JWT,
  expiresInSeconds: 3600,
}

/** loginAsSuperAdmin 에서 사용하는 폼 입력값 (실제 인증은 mock 응답이 처리) */
export const MOCK_CREDENTIALS = {
  username: 'admin',
  password: 'test-password',
}

/**
 * POST /api/v1/auth/login 을 인터셉트하여 200 + mock JWT 응답을 반환한다.
 * loginAsSuperAdmin 시퀀스의 첫 단계로 사용.
 */
export async function mockLoginApi(page: Page): Promise<void> {
  await page.route('**/api/v1/auth/login', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(MOCK_LOGIN_RESPONSE),
    })
  })
}

/**
 * POST /api/v1/auth/login 을 인터셉트하여 401 응답을 반환한다.
 * 로그인 실패 시나리오(REQ-LOGIN-002)에서 사용.
 */
export async function mockLoginFailureApi(page: Page): Promise<void> {
  await page.route('**/api/v1/auth/login', async (route) => {
    await route.fulfill({
      status: 401,
      contentType: 'application/json',
      body: JSON.stringify({ code: 'AUTH_INVALID_CREDENTIALS', message: '아이디 또는 비밀번호가 올바르지 않습니다.' }),
    })
  })
}

/**
 * POST /api/v1/auth/logout 을 200으로 인터셉트한다.
 */
export async function mockLogoutApi(page: Page): Promise<void> {
  await page.route('**/api/v1/auth/logout', async (route) => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: '{}' })
  })
}

/**
 * SUPER_ADMIN 자격으로 로그인한다. 모든 인증 필수 시나리오의 진입점.
 *
 * 절차:
 *   1) /api/v1/auth/login mock 등록
 *   2) /login 으로 이동
 *   3) #username / #password 채우기
 *   4) button[type="submit"] 클릭
 *   5) 라우터가 /dashboard 로 이동할 때까지 대기
 *
 * 호출 후 useAuthStore.isAuthenticated == true, user.roleCodes == ['SUPER_ADMIN'] 보장.
 */
export async function loginAsSuperAdmin(page: Page): Promise<void> {
  await mockLoginApi(page)
  await page.goto('/login')
  await page.fill('#username', MOCK_CREDENTIALS.username)
  await page.fill('#password', MOCK_CREDENTIALS.password)
  await page.click('button[type="submit"]')
  await page.waitForURL('**/dashboard')
}

/**
 * 세션 격리: cookies + storage (localStorage / sessionStorage) 를 모두 제거한다.
 * 매 테스트 beforeEach 에서 호출하여 Pinia 인증 상태가 다음 reload 때 잔존하지 않음을 보장.
 */
export async function clearSession(page: Page): Promise<void> {
  await page.context().clearCookies()
  // storage clear는 페이지가 로드된 상태에서만 가능 — 호출 시점에 about:blank 등이면 무시.
  try {
    await page.evaluate(() => {
      try {
        localStorage.clear()
        sessionStorage.clear()
      } catch {
        // 보안 정책으로 접근 불가 시 안전하게 무시
      }
    })
  } catch {
    // page가 아직 어떤 origin도 로드하지 않은 경우 evaluate 가 실패할 수 있음 — 무시
  }
}
