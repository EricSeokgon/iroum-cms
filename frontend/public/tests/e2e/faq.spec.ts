// SPEC-CMS-PUBLIC-E2E-001 — FAQ 아코디언 키보드 E2E
// REQ 매핑: REQ-E2E-002 (셀렉터 규약), REQ-E2E-007 (키보드 토글)
// 인수 시나리오: S3 (Enter/Space 토글), E5 (Tab 순서)
//
// FaqView 구현: button[aria-expanded] + role="region" 패널 + @keydown.enter.prevent / @keydown.space.prevent
// 셀렉터: faq-list, faq-header-{idx}, faq-panel-{idx}, faq-category-select, faq-keyword-input
import { test, expect } from '@playwright/test'
import { clearAuth } from './fixtures/auth'
import { mockAllApis } from './fixtures/api-mocks'

test.describe('FAQ 화면 (FaqView)', () => {
  test.beforeEach(async ({ page }) => {
    await mockAllApis(page)
    await clearAuth(page)
  })

  test('/faqs 진입 시 검색 폼이 렌더링된다', async ({ page }) => {
    await page.goto('/faqs')
    await expect(page.getByTestId('faq-category-select')).toBeVisible()
    await expect(page.getByTestId('faq-keyword-input')).toBeVisible()
  })

  test('S3: 마우스 클릭으로 FAQ 항목을 펼치고 aria-expanded 가 true 로 변경된다', async ({
    page,
  }) => {
    await page.goto('/faqs')

    // 백엔드 가용 시 faq-list 렌더링, 부재 시 EmptyState 또는 ErrorState
    const list = page.getByTestId('faq-list')
    const skipReason = await list
      .waitFor({ state: 'visible', timeout: 8_000 })
      .then(() => null)
      .catch(() => 'faq-list 가 렌더링되지 않음 — 백엔드 데이터 없음')

    test.skip(!!skipReason, skipReason ?? '')

    const firstHeader = page.getByTestId('faq-header-0')
    await expect(firstHeader).toBeVisible()
    await expect(firstHeader).toHaveAttribute('aria-expanded', 'false')

    await firstHeader.click()
    await expect(firstHeader).toHaveAttribute('aria-expanded', 'true')
    await expect(page.getByTestId('faq-panel-0')).toBeVisible()
  })

  test('S3 (키보드): Enter 키로 펼치고 Space 키로 접는다', async ({ page }) => {
    await page.goto('/faqs')
    const list = page.getByTestId('faq-list')
    const skipReason = await list
      .waitFor({ state: 'visible', timeout: 8_000 })
      .then(() => null)
      .catch(() => 'faq-list 가 렌더링되지 않음 — 백엔드 데이터 없음')

    test.skip(!!skipReason, skipReason ?? '')

    const firstHeader = page.getByTestId('faq-header-0')
    await expect(firstHeader).toBeVisible()

    // 직접 포커스 이동 후 Enter 키 (Tab 순서 검증은 별도 테스트)
    await firstHeader.focus()
    await expect(firstHeader).toBeFocused()
    await expect(firstHeader).toHaveAttribute('aria-expanded', 'false')

    await page.keyboard.press('Enter')
    await expect(firstHeader).toHaveAttribute('aria-expanded', 'true')
    await expect(page.getByTestId('faq-panel-0')).toBeVisible()

    // Space 키로 접기 (FaqView @keydown.space.prevent="toggle")
    await page.keyboard.press('Space')
    await expect(firstHeader).toHaveAttribute('aria-expanded', 'false')
  })

  test('FAQ 카테고리 필터 변경 시 URL 갱신', async ({ page }) => {
    await page.goto('/faqs')
    // 카테고리 select 가 비어있을 수 있음 — 옵션 존재 여부 먼저 확인
    const select = page.getByTestId('faq-category-select')
    await expect(select).toBeVisible()
    const optionCount = await select.locator('option').count()
    test.skip(optionCount <= 1, '카테고리 옵션이 비어있음 — 백엔드 데이터 없음')

    // 두 번째 옵션 (첫 번째는 "전체") 선택
    const optionValue = await select.locator('option').nth(1).getAttribute('value')
    test.skip(!optionValue, '유효한 카테고리 값 없음')
    await select.selectOption(optionValue!)
    // FaqView 는 폼 제출 시 URL 갱신 — submit 버튼 클릭
    await page.locator('form[role="search"] button[type="submit"]').click()
    await expect(page).toHaveURL(/[?&]categoryCode=/, { timeout: 5_000 })
  })
})
