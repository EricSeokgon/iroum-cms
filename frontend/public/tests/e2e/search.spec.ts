// SPEC-CMS-PUBLIC-E2E-001 — 통합 검색 6탭 E2E
// REQ 매핑: REQ-E2E-002 (셀렉터 규약), REQ-E2E-008 (탭 전환)
// 인수 시나리오: S4 (POST 탭 클릭), E2 (빈 쿼리), E6 (페이지 리셋)
//
// SearchResultView + SearchFilterTabs:
//   role="tablist", role="tab", aria-selected, data-testid="search-tab-{type}"
//   6탭: ALL / POST / FAQ / QNA / POLICY / SAFETY
//   결과: data-testid="search-result-list" (결과 있을 때) / search-empty-tip (빈 결과)
import { test, expect } from '@playwright/test'
import { clearAuth } from './fixtures/auth'

test.describe('통합 검색 (SearchResultView + SearchFilterTabs)', () => {
  test.beforeEach(async ({ page }) => {
    await clearAuth(page)
  })

  test('/search?q=지원 진입 시 ALL 탭이 활성 상태로 렌더링된다', async ({ page }) => {
    await page.goto('/search?q=%EC%A7%80%EC%9B%90')
    await expect(page.getByTestId('search-filter-tabs')).toBeVisible()
    await expect(page.getByTestId('search-tab-ALL')).toHaveAttribute('aria-selected', 'true')
  })

  test('S4: POST 탭 클릭 시 URL ?type=POST 갱신 + aria-selected 전환', async ({ page }) => {
    await page.goto('/search?q=%EC%A7%80%EC%9B%90')
    const allTab = page.getByTestId('search-tab-ALL')
    const postTab = page.getByTestId('search-tab-POST')

    await expect(allTab).toHaveAttribute('aria-selected', 'true')
    await expect(postTab).toBeVisible()
    await postTab.click()

    await expect(page).toHaveURL(/[?&]type=POST(&|$)/, { timeout: 5_000 })
    await expect(postTab).toHaveAttribute('aria-selected', 'true')
    await expect(allTab).toHaveAttribute('aria-selected', 'false')
  })

  test('FAQ 탭 클릭 시 URL 갱신 + aria-selected 전환', async ({ page }) => {
    await page.goto('/search?q=%EC%A7%80%EC%9B%90')
    const faqTab = page.getByTestId('search-tab-FAQ')
    await expect(faqTab).toBeVisible()
    await faqTab.click()
    await expect(page).toHaveURL(/[?&]type=FAQ(&|$)/, { timeout: 5_000 })
    await expect(faqTab).toHaveAttribute('aria-selected', 'true')
  })

  test('POLICY 탭 클릭 시 URL 갱신', async ({ page }) => {
    await page.goto('/search?q=%EC%A7%80%EC%9B%90')
    const policyTab = page.getByTestId('search-tab-POLICY')
    await expect(policyTab).toBeVisible()
    await policyTab.click()
    await expect(page).toHaveURL(/[?&]type=POLICY(&|$)/, { timeout: 5_000 })
  })

  test('6탭 모두 (ALL/POST/FAQ/QNA/POLICY/SAFETY) 렌더링된다', async ({ page }) => {
    await page.goto('/search?q=%EC%A7%80%EC%9B%90')
    const tabs = ['ALL', 'POST', 'FAQ', 'QNA', 'POLICY', 'SAFETY']
    for (const tab of tabs) {
      await expect(page.getByTestId(`search-tab-${tab}`)).toBeVisible()
    }
  })

  test('E2: 빈 쿼리(/search 직접 접근) 처리', async ({ page }) => {
    await page.goto('/search')
    // 검색 페이지가 렌더링되어야 함 (NotFoundView 가 아님)
    await expect(page.getByTestId('search-page-title')).toBeVisible()
    // 검색어 없으면 결과 목록 미표시 (search-summary 도 v-if="query")
    await expect(page.getByTestId('search-summary')).toHaveCount(0)
  })
})
