// @MX:NOTE: [AUTO] 대시보드 진입 검증 — REQ-DASHBOARD-001
// @MX:SPEC: SPEC-CMS-ADMIN-E2E-001
//
// loginAsSuperAdmin → /dashboard 도달 후 admin-layout, dashboard-main, navigation 가 모두 렌더된다.
// 위젯/뷰/내보내기 API 는 빈 응답으로 mock 하여 백엔드 의존을 제거한다.

import { expect, test } from '@playwright/test'
import { clearSession, loginAsSuperAdmin } from './fixtures/auth'

test.describe('대시보드 진입', () => {
  test.beforeEach(async ({ page }) => {
    await clearSession(page)

    // 위젯/뷰/내보내기/유지보수 배너 등 대시보드가 호출하는 모든 GET 을 빈 응답으로 mock
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
  })

  test('admin-layout 과 dashboard-main 이 모두 렌더된다', async ({ page }) => {
    await expect(page.getByTestId('admin-layout')).toBeVisible()
    await expect(page.getByTestId('dashboard-main')).toBeVisible()
  })

  test('사이드바 navigation 영역이 노출된다', async ({ page }) => {
    // role="navigation" 속성을 가진 el-aside (AdminLayout)
    const nav = page.getByRole('navigation').first()
    await expect(nav).toBeVisible()
  })

  test('페이지 타이틀이 "대시보드 | iroum-cms 관리자" 로 설정된다', async ({ page }) => {
    await expect(page).toHaveTitle('대시보드 | iroum-cms 관리자')
  })
})
