// @MX:NOTE: [AUTO] 사용자 목록 진입 검증 — REQ-USERS-001
// @MX:SPEC: SPEC-CMS-ADMIN-E2E-001
//
// /users 진입 후 user-list-table 이 렌더되고 mock 데이터 행이 표시된다.
// GET /api/v1/users (목록), GET /api/v1/organizations (필터용) 을 mock.

import { expect, test } from '@playwright/test'
import { clearSession, loginAsSuperAdmin } from './fixtures/auth'

const MOCK_USERS_PAGE = {
  content: [
    {
      id: 1,
      username: 'admin',
      email: 'admin@example.com',
      name: '관리자',
      status: 'ACTIVE',
      orgName: '루트 조직',
      lastLoginAt: '2026-01-01T00:00:00Z',
      createdAt: '2026-01-01T00:00:00Z',
    },
    {
      id: 2,
      username: 'editor',
      email: 'editor@example.com',
      name: '편집자',
      status: 'ACTIVE',
      orgName: '루트 조직',
      lastLoginAt: null,
      createdAt: '2026-01-02T00:00:00Z',
    },
    {
      id: 3,
      username: 'viewer',
      email: 'viewer@example.com',
      name: '뷰어',
      status: 'INACTIVE',
      orgName: '루트 조직',
      lastLoginAt: null,
      createdAt: '2026-01-03T00:00:00Z',
    },
  ],
  totalElements: 3,
  totalPages: 1,
  number: 0,
  size: 20,
}

test.describe('사용자 관리', () => {
  test.beforeEach(async ({ page }) => {
    await clearSession(page)

    await page.route('**/api/v1/users**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(MOCK_USERS_PAGE),
      })
    })
    await page.route('**/api/v1/organizations**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([{ id: 1, name: '루트 조직', code: 'ROOT' }]),
      })
    })

    await loginAsSuperAdmin(page)
  })

  test('/users 진입 시 user-list-table 이 노출된다', async ({ page }) => {
    // SPA 내부 router push 로 이동 (page.goto 는 full reload → Pinia 상태 손실)
    await page.getByRole('menuitem', { name: /사용자|Users/ }).first().click()
    await page.waitForURL('**/users')
    await expect(page.getByTestId('user-list-table')).toBeVisible()
  })

  test('페이지 타이틀이 "사용자 관리 | iroum-cms 관리자" 로 설정된다', async ({ page }) => {
    await page.getByRole('menuitem', { name: /사용자|Users/ }).first().click()
    await page.waitForURL('**/users')
    await expect(page).toHaveTitle('사용자 관리 | iroum-cms 관리자')
  })
})
