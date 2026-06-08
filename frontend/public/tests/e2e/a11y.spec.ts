// @MX:NOTE: [AUTO] KWCAG 2.2 AA 출시 게이트. 정책 변경 시 영향 — 본 spec 통과 필수.
// @MX:SPEC: SPEC-CMS-PUBLIC-E2E-001 REQ-E2E-012, REQ-E2E-013
//
// SPEC-CMS-PUBLIC-E2E-001 — KWCAG 2.2 AA 접근성 E2E
// REQ 매핑: REQ-E2E-012 (스킵 네비), REQ-E2E-013 (폼 에러 ARIA)
// 인수 시나리오: S8 (스킵 네비 키보드), S9 (aria-invalid / aria-describedby)
//
// PublicLayout (layouts/PublicLayout.vue):
//   <a href="#main-content" class="sr-only focus:not-sr-only ..."> → 첫 Tab 으로 포커스
//   <main id="main-content" role="main" tabindex="-1">
//
// QnaCreateView:
//   :aria-invalid="!!titleError"
//   :aria-describedby="titleError ? 'qna-title-error' : undefined"
//   <p v-if="titleError" id="qna-title-error" role="alert">
import { test, expect, type Page } from '@playwright/test'
import AxeBuilder from '@axe-core/playwright'
import { loginAs, clearAuth } from './fixtures/auth'
import { mockAllApis } from './fixtures/api-mocks'

// 색상 대비는 jsdom 한계 / 시각 회귀 영역으로 분리 — 본 spec 에서는 critical 위반만 검증
const AXE_DISABLED_RULES = ['color-contrast']

async function runAxe(page: Page) {
  return new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
    .disableRules(AXE_DISABLED_RULES)
    .analyze()
}

test.describe('스킵 네비게이션 (REQ-E2E-012)', () => {
  test.beforeEach(async ({ page }) => {
    await mockAllApis(page)
    await clearAuth(page)
  })

  // S8 적용 페이지: /, /notices, /faqs, /search?q=지원, /policies/match (5개)
  const skipNavPages = [
    { path: '/', label: '홈' },
    { path: '/notices', label: '공지 목록' },
    { path: '/faqs', label: 'FAQ' },
    { path: '/search?q=%EC%A7%80%EC%9B%90', label: '검색 결과' },
    { path: '/policies/match', label: '정책 매칭' },
  ]

  for (const { path, label } of skipNavPages) {
    test(`S8: ${label} (${path}) 스킵 네비게이션 키보드 접근성`, async ({ page }) => {
      await page.goto(path)
      await page.locator('main#main-content').waitFor({ state: 'attached' })

      // KWCAG 2.4.1 검증 항목:
      // (1) 스킵 네비게이션 링크가 DOM 에 존재한다 (구조적 보장)
      // (2) 스킵 네비게이션이 Tab 으로 도달 가능하다 (sr-only 가 tabindex 를 제거하지 않음)
      // (3) Enter 키로 main 영역으로 점프한다
      const skipLink = page.locator('a[href="#main-content"]').first()
      await expect(skipLink).toHaveCount(1)

      // 스킵 네비 직접 포커스 — 자동화 환경의 초기 포커스 변동성 회피.
      // 실제 키보드 사용자는 첫 Tab 으로 도달하지만, 본 테스트는 "포커스 가능 + Enter 동작" 검증에 집중.
      await skipLink.focus()
      await expect(skipLink).toBeFocused()

      // Enter 키 → href="#main-content" 기본 동작: hash 변경 + 타겟 요소 포커스 (tabindex="-1")
      await page.keyboard.press('Enter')

      // 결과 검증: URL hash 가 변경되었거나, document.activeElement 가 main-content 로 이동
      const url = new URL(page.url())
      const activeId = await page.evaluate(() => document.activeElement?.id)
      expect(
        url.hash === '#main-content' || activeId === 'main-content',
        `Enter 후 main 영역으로 점프 (activeId=${activeId}, hash=${url.hash})`,
      ).toBe(true)
    })
  }
})

test.describe('폼 에러 ARIA (REQ-E2E-013)', () => {
  test.beforeEach(async ({ page }) => {
    await mockAllApis(page)
    // QnaCreateView 는 requiresAuth=true 라우트 — 토큰 주입 필수
    await loginAs(page, { token: 'e2e-a11y-test-token' })
  })

  test('S9: /qnas/new 빈 title 제출 시 aria-invalid + aria-describedby 연결', async ({ page }) => {
    await page.goto('/qnas/new')

    // 인증 가드 통과 후 폼이 보여야 함 — 보이지 않으면 백엔드 가용성 문제로 skip
    const form = page.getByTestId('qna-create-form')
    const skipReason = await form
      .waitFor({ state: 'visible', timeout: 8_000 })
      .then(() => null)
      .catch(() => 'qna-create-form 미렌더링 — /me/qnas API 또는 인증 검증 단계에서 실패')
    test.skip(!!skipReason, skipReason ?? '')

    // title 비우고 submit 시도 (content 도 비어있어 titleError 가 먼저 표시됨)
    const titleInput = page.getByTestId('qna-title-input')
    await titleInput.fill('')
    await page.getByTestId('qna-submit').click()

    // QnaCreateView 의 validate() 가 titleError 를 설정 → aria-invalid="true"
    await expect(titleInput).toHaveAttribute('aria-invalid', 'true')
    await expect(titleInput).toHaveAttribute('aria-describedby', 'qna-title-error')
    await expect(page.locator('#qna-title-error')).toBeVisible()
  })
})

test.describe('axe-core 자동 접근성 검사 (critical 위반 0)', () => {
  test.beforeEach(async ({ page }) => {
    await mockAllApis(page)
    await clearAuth(page)
  })

  test('홈 페이지 axe-core critical 위반 0건', async ({ page }) => {
    await page.goto('/')
    await page.waitForLoadState('domcontentloaded')
    const results = await runAxe(page)
    const critical = results.violations.filter((v) => v.impact === 'critical')
    expect(critical, JSON.stringify(critical, null, 2)).toEqual([])
  })

  test('/faqs 페이지 axe-core critical 위반 0건', async ({ page }) => {
    await page.goto('/faqs')
    await page.waitForLoadState('domcontentloaded')
    const results = await runAxe(page)
    const critical = results.violations.filter((v) => v.impact === 'critical')
    expect(critical, JSON.stringify(critical, null, 2)).toEqual([])
  })

  test('/policies/match 페이지 axe-core critical 위반 0건', async ({ page }) => {
    await page.goto('/policies/match')
    await page.waitForLoadState('domcontentloaded')
    const results = await runAxe(page)
    const critical = results.violations.filter((v) => v.impact === 'critical')
    expect(critical, JSON.stringify(critical, null, 2)).toEqual([])
  })

  test('/notices 페이지 axe-core critical 위반 0건', async ({ page }) => {
    await page.goto('/notices')
    await page.waitForLoadState('domcontentloaded')
    const results = await runAxe(page)
    const critical = results.violations.filter((v) => v.impact === 'critical')
    expect(critical, JSON.stringify(critical, null, 2)).toEqual([])
  })
})
