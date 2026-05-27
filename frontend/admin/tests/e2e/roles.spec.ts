// @MX:NOTE: [AUTO] 역할/권한 매트릭스 진입 검증 — REQ-ROLES-001
// @MX:SPEC: SPEC-CMS-ADMIN-E2E-001
//
// /roles 진입 후 role-matrix 가 렌더된다.
// GET /api/v1/roles + GET /api/v1/permissions mock.

import { expect, test } from '@playwright/test'
import { clearSession, loginAsSuperAdmin } from './fixtures/auth'

const MOCK_ROLES = [
  { code: 'SUPER_ADMIN', name: '최고 관리자', description: '모든 권한', isSystem: true, userCount: 1 },
  { code: 'DEPT_ADMIN', name: '부서 관리자', description: '부서 단위 관리', isSystem: false, userCount: 3 },
]

const MOCK_PERMISSIONS = [
  { code: 'USER:READ', name: '사용자 조회', category: 'USER' },
  { code: 'USER:WRITE', name: '사용자 수정', category: 'USER' },
  { code: 'ROLE:READ', name: '역할 조회', category: 'ROLE' },
]

test.describe('역할/권한 매트릭스', () => {
  test.beforeEach(async ({ page }) => {
    await clearSession(page)

    await page.route('**/api/v1/roles', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(MOCK_ROLES),
      })
    })
    await page.route('**/api/v1/roles/*', async (route) => {
      // 상세 / 권한 PUT 등은 단순 200 응답
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ code: 'SUPER_ADMIN', name: '최고 관리자', permissions: [] }),
      })
    })
    await page.route('**/api/v1/permissions', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(MOCK_PERMISSIONS),
      })
    })

    await loginAsSuperAdmin(page)
  })

  test('/roles 진입 시 role-matrix 가 노출된다', async ({ page }) => {
    // SPA 내부 router push 로 이동 (page.goto 는 full reload → Pinia 상태 손실)
    await page.getByRole('menuitem', { name: /역할|Roles/ }).first().click()
    // URL 함수로 pathname 만 검사 — glob/정규식은 ?redirect=/roles 쿼리 스트링도 매칭하므로 사용 금지
    await page.waitForURL((url) => new URL(url).pathname === '/roles')
    await expect(page.getByTestId('role-matrix')).toBeVisible()
  })

  test('페이지 타이틀이 "역할/권한 관리 | iroum-cms 관리자" 로 설정된다', async ({ page }) => {
    await page.getByRole('menuitem', { name: /역할|Roles/ }).first().click()
    await page.waitForURL((url) => new URL(url).pathname === '/roles')
    await expect(page).toHaveTitle('역할/권한 관리 | iroum-cms 관리자')
  })
})
