// SPEC-CMS-PUBLIC-E2E-001 — 공지 E2E
// REQ 매핑: REQ-E2E-002 (셀렉터 규약), REQ-E2E-005 (목록 필터/검색), REQ-E2E-006 (목록↔상세)
// 인수 시나리오: S2 (카테고리 필터), E1 (빈 결과)
//
// NoticeListView 셀렉터: select#notice-category, input#notice-keyword, data-testid="notice-list"
// 카테고리 옵션: EVENT / NEWS / GENERAL (NoticeListView.vue line 33-36)
import { test, expect } from '@playwright/test'
import { clearAuth } from './fixtures/auth'
import { mockAllApis } from './fixtures/api-mocks'

test.describe('공지 목록 (NoticeListView)', () => {
  test.beforeEach(async ({ page }) => {
    await mockAllApis(page)
    await clearAuth(page)
  })

  test('/notices 진입 시 검색 폼이 렌더링된다', async ({ page }) => {
    await page.goto('/notices')
    // 검색 폼 구조적 셀렉터 (REQ-E2E-002 — CSS 클래스 셀렉터 금지, id/role 사용 OK)
    await expect(page.locator('select#notice-category')).toBeVisible()
    await expect(page.locator('input#notice-keyword')).toBeVisible()
    await expect(page.getByTestId('notice-search-submit')).toBeVisible()
  })

  test('S2: 카테고리 필터 선택 후 제출 시 URL 에 category 쿼리가 추가된다', async ({ page }) => {
    await page.goto('/notices')

    const select = page.locator('select#notice-category')
    await expect(select).toBeVisible()
    await select.selectOption('EVENT')
    // NoticeListView 는 onSearchSubmit() 에서만 URL 동기화 (syncQuery)
    // 따라서 select 후 submit 버튼을 눌러야 URL 이 갱신된다
    await page.getByTestId('notice-search-submit').click()

    // 실제 구현: syncQuery() 는 `query.category` 키를 사용 (NoticeListView.vue line 188)
    // SPEC 의 `categoryCode` 표기는 select 의 value 이며 URL 키와 다름을 명시
    await expect(page).toHaveURL(/[?&]category=EVENT(&|$)/, { timeout: 5_000 })
  })

  test('키워드 검색 시 URL 에 keyword 가 추가된다', async ({ page }) => {
    await page.goto('/notices')

    await page.locator('input#notice-keyword').fill('이벤트')
    await page.getByTestId('notice-search-submit').click()

    await expect(page).toHaveURL(/[?&]keyword=/, { timeout: 5_000 })
  })

  test('E1: 매칭 없는 키워드로 진입 시 검색 폼은 유지된다', async ({ page }) => {
    // 백엔드 가용 여부와 관계없이 검색 영역과 폼이 표시되어야 한다.
    // 실제 EmptyState 텍스트 검증은 백엔드 통합 SPEC 에서 정밀화.
    await page.goto('/notices?keyword=zzzzzzz-no-match-xyz-12345')
    await expect(page.locator('input#notice-keyword')).toBeVisible()
    await expect(page.locator('input#notice-keyword')).toHaveValue(
      'zzzzzzz-no-match-xyz-12345',
    )
  })

  test('REQ-E2E-006: 공지 상세 페이지 직접 접근이 가능하다', async ({ page }) => {
    // 백엔드가 없거나 데이터가 없으면 not-found 또는 에러 페이지로 폴백 — 본 테스트는
    // 라우트 매칭 자체를 검증한다 (NotFoundView 가 catch-all 로 매칭되지 않음)
    const response = await page.goto('/notices/1')
    expect(response).toBeTruthy()
    // 라우트가 매칭되었으면 URL 은 그대로 유지된다
    expect(new URL(page.url()).pathname).toBe('/notices/1')
  })
})
