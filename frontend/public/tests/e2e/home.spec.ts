// SPEC-CMS-PUBLIC-E2E-001 — 홈 E2E
// REQ 매핑: REQ-E2E-002 (셀렉터 규약), REQ-E2E-004 (영웅 검색)
// 인수 시나리오: S1 (홈 영웅 검색)
//
// 본 spec 은 HomeView.vue (기존 구현) 의 동작을 검증한다.
// 셀렉터: home-hero / home-search-form / home-search-input / home-notices-section / home-quicklinks
import { test, expect } from '@playwright/test'
import { clearAuth } from './fixtures/auth'

test.describe('홈 페이지 (HomeView)', () => {
  test.beforeEach(async ({ page }) => {
    await clearAuth(page)
  })

  test('홈 진입 시 영웅 섹션과 검색 폼이 렌더링된다', async ({ page }) => {
    await page.goto('/')
    await expect(page.getByTestId('home-hero')).toBeVisible()
    await expect(page.getByTestId('home-search-form')).toBeVisible()
    await expect(page.getByTestId('home-search-input')).toBeVisible()
  })

  test('S1: 영웅 검색 폼 제출 시 /search?q=... 로 이동한다', async ({ page }) => {
    await page.goto('/')
    await expect(page.getByTestId('home-search-input')).toBeVisible()

    const input = page.getByTestId('home-search-input')
    await input.fill('청년 창업')
    // form @submit.prevent → router.push({ name: 'search', query: { q } })
    await input.press('Enter')

    // URL 이 /search 로 변경되고 q 쿼리 파라미터를 포함해야 한다
    await page.waitForURL(/\/search\?q=/)
    const url = new URL(page.url())
    expect(url.pathname).toBe('/search')
    // Vue Router 는 공백을 + 또는 %20 으로 인코딩
    expect(decodeURIComponent(url.searchParams.get('q') ?? '')).toContain('청년 창업')
  })

  test('홈에 공지 섹션이 렌더링된다 (로딩/성공/에러 중 하나)', async ({ page }) => {
    await page.goto('/')
    await expect(page.getByTestId('home-notices-section')).toBeVisible()
    // 백엔드 가용 시 home-notices-list, 실패 시 ErrorState — 본 SPEC 의 검증 목표는 섹션 자체의
    // 렌더링이므로 내부 상태에 관계없이 섹션이 visible 하면 GREEN.
    // 백엔드 통합 후 list/error 분기는 별도 SPEC 에서 정밀화.
  })

  test('홈에 정책 섹션이 렌더링된다 (로딩/성공/에러 중 하나)', async ({ page }) => {
    await page.goto('/')
    await expect(page.getByTestId('home-policies-section')).toBeVisible()
  })

  test('홈에 퀵링크 섹션이 렌더링된다', async ({ page }) => {
    await page.goto('/')
    await expect(page.getByTestId('home-quicklinks-section')).toBeVisible()
    await expect(page.getByTestId('home-quicklinks')).toBeVisible()
  })
})
