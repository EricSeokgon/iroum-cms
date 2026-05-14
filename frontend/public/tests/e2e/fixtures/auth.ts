// @MX:ANCHOR: [AUTO] 모든 E2E 인증 시나리오의 진입점 — localStorage public.accessToken 주입.
// @MX:REASON: 9개 spec 파일에서 import 예상 (fan_in >= 3). LoginView Phase 0 스텁 상태에서 인증 라우트 가드를 통과하기 위한 유일한 경로.
// @MX:SPEC: SPEC-CMS-PUBLIC-E2E-001 REQ-E2E-010, REQ-E2E-014
//
// 인증 헬퍼: Public SPA 의 axios 인터셉터는 localStorage.public.accessToken 을 읽어
// Authorization 헤더를 부착한다. LoginView 가 미구현된 Phase 0 에서 인증 라우트를 검증하려면
// 브라우저 컨텍스트에 토큰을 직접 주입해야 한다.
//
// REQ-E2E-014: 백엔드 쓰기 사이드이펙트 금지 — 토큰은 더미 값이며 실제 호출 시 401 응답을
// 받게 된다. 본 헬퍼는 라우트 가드 통과만을 목적으로 한다.

import type { Page } from '@playwright/test'

const ACCESS_TOKEN_KEY = 'public.accessToken'
const REFRESH_TOKEN_KEY = 'public.refreshToken'

export interface AuthTokens {
  token: string
  refreshToken?: string
}

/**
 * 브라우저 컨텍스트에 더미 인증 토큰을 주입한다.
 *
 * page.addInitScript 로 페이지 로드 이전에 localStorage 를 채워야 SPA 의
 * useAuthStore.initFromStorage() 가 토큰을 읽을 수 있다.
 */
export async function loginAs(
  page: Page,
  options: AuthTokens = { token: 'e2e-test-access-token' },
): Promise<void> {
  await page.addInitScript(
    ({ accessKey, refreshKey, token, refreshToken }) => {
      localStorage.setItem(accessKey, token)
      if (refreshToken) {
        localStorage.setItem(refreshKey, refreshToken)
      }
    },
    {
      accessKey: ACCESS_TOKEN_KEY,
      refreshKey: REFRESH_TOKEN_KEY,
      token: options.token,
      refreshToken: options.refreshToken,
    },
  )
}

/**
 * 모든 인증 토큰을 제거한다. 라우트 가드 검증 시나리오의 시작점.
 *
 * 페이지 로드 이전에 localStorage 를 비워 SPA 초기화 시 비인증 상태를 보장한다.
 * afterEach 에서 호출하여 테스트 격리도 유지한다 (REQ-E2E-014).
 */
export async function clearAuth(page: Page): Promise<void> {
  await page.addInitScript(
    ({ accessKey, refreshKey }) => {
      localStorage.removeItem(accessKey)
      localStorage.removeItem(refreshKey)
    },
    { accessKey: ACCESS_TOKEN_KEY, refreshKey: REFRESH_TOKEN_KEY },
  )
}

/**
 * 비인증 상태에서 requiresAuth 라우트 접근 시 /login?redirect=... 리다이렉트를 검증한다.
 *
 * Vue Router 는 슬래시(/) 를 URL-encode 하지 않고 그대로 전달한다. 따라서 originalPath 가
 * "/qnas/new" 이면 redirect 값은 "/qnas/new" 그대로이며 %2F 로 인코딩되지 않는다.
 * (router.beforeEach 에서 to.fullPath 사용 → query 직렬화 시 슬래시 유지)
 */
export function buildLoginRedirectUrl(originalPath: string): string {
  return `/login?redirect=${originalPath}`
}
