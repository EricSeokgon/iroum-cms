// @MX:NOTE: [AUTO] 공지(FAQ) 목록 진입 검증 — REQ-NOTICES-001
// @MX:SPEC: SPEC-CMS-ADMIN-E2E-001
//
// Admin SPA 에는 별도 /notices 라우트가 존재하지 않으므로, 가장 가까운 공지 형태인
// /board/faqs (FAQ 관리) 를 검증 대상으로 사용한다.
// data-testid="notice-list-table" 은 FaqListView.vue 의 el-table 에 부착돼 있다.

import { expect, test } from '@playwright/test'
import { clearSession, loginAsSuperAdmin } from './fixtures/auth'

const MOCK_FAQS_PAGE = {
  content: [
    {
      id: 1,
      categoryCode: 'GENERAL',
      question: '서비스 이용 방법은?',
      answer: '안내 문서를 참고하세요.',
      sortOrder: 1,
      viewCount: 100,
      status: 'PUBLISHED',
      createdAt: '2026-01-01T00:00:00Z',
    },
  ],
  totalElements: 1,
  totalPages: 1,
  number: 0,
  size: 20,
}

test.describe('공지/FAQ 목록', () => {
  test.beforeEach(async ({ page }) => {
    await clearSession(page)

    await page.route('**/api/v1/faqs/categories**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([{ categoryCode: 'GENERAL', count: 1 }]),
      })
    })
    await page.route('**/api/v1/faqs**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(MOCK_FAQS_PAGE),
      })
    })

    await loginAsSuperAdmin(page)
  })

  test('/board/faqs 진입 시 notice-list-table 이 노출된다', async ({ page }) => {
    // SPA 내부 router push 로 이동 (page.goto 는 full reload → Pinia 상태 손실)
    // 콘텐츠 그룹 서브메뉴 → FAQ 메뉴 클릭
    await page.getByRole('menuitem', { name: /^콘텐츠$|^Content$|^게시판$|^Board$/ }).first().click()
    await page.getByRole('menuitem', { name: /^FAQ/ }).first().click()
    await page.waitForURL('**/board/faqs')
    await expect(page.getByTestId('notice-list-table')).toBeVisible()
  })

  test('페이지 타이틀이 "FAQ 관리 | iroum-cms 관리자" 로 설정된다', async ({ page }) => {
    await page.getByRole('menuitem', { name: /^콘텐츠$|^Content$|^게시판$|^Board$/ }).first().click()
    await page.getByRole('menuitem', { name: /^FAQ/ }).first().click()
    await page.waitForURL('**/board/faqs')
    await expect(page).toHaveTitle('FAQ 관리 | iroum-cms 관리자')
  })
})
