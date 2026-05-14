// @MX:NOTE: [AUTO] KWCAG 2.2 AA axe-core 스캔 — critical/serious violations 0건
// @MX:REASON: 색상 대비는 별도 시각 회귀 SPEC 예정. false-positive 방지를 위해
//             color-contrast 룰은 비활성화하고 critical/serious 등급만 검사한다.
// @MX:SPEC: SPEC-CMS-ADMIN-E2E-001 REQ-A11Y-001, REQ-A11Y-002

import AxeBuilder from '@axe-core/playwright'
import { expect, test } from '@playwright/test'
import { clearSession, loginAsSuperAdmin } from './fixtures/auth'

test.describe('접근성 (axe-core)', () => {
  test.beforeEach(async ({ page }) => {
    await clearSession(page)
  })

  test('/login 페이지에 critical/serious 위반이 없다', async ({ page }) => {
    await page.goto('/login')
    // 로그인 폼이 렌더될 때까지 대기
    await page.waitForSelector('#username')

    const results = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa', 'wcag21aa'])
      .disableRules(['color-contrast'])
      .analyze()

    const blocking = results.violations.filter(
      (v) => v.impact === 'critical' || v.impact === 'serious',
    )
    expect(blocking, JSON.stringify(blocking, null, 2)).toEqual([])
  })

  test('/dashboard 페이지에 critical/serious 위반이 없다', async ({ page }) => {
    // 대시보드 API 빈 응답 mock
    await page.route('**/api/v1/dashboard/**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ content: [], totalElements: 0 }),
      })
    })
    await page.route('**/api/v1/system/maintenance**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ active: false }),
      })
    })
    await page.route('**/api/v1/me/**', async (route) => {
      await route.fulfill({ status: 200, contentType: 'application/json', body: '{}' })
    })

    await loginAsSuperAdmin(page)
    await page.waitForSelector('[data-testid="dashboard-main"]')

    const results = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa', 'wcag21aa'])
      .disableRules(['color-contrast'])
      .analyze()

    const blocking = results.violations.filter(
      (v) => v.impact === 'critical' || v.impact === 'serious',
    )
    expect(blocking, JSON.stringify(blocking, null, 2)).toEqual([])
  })
})
