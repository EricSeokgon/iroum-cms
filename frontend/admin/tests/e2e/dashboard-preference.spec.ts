// @MX:NOTE: [AUTO] 대시보드 개인화 설정 E2E 검증 — REQ-DP-001/002/003
// @MX:SPEC: SPEC-CMS-DASHBOARD-PERSONALIZE-001
//
// AC-DP-001: 테마 변경 시 PATCH 전송 + html[data-theme] 갱신
// AC-DP-002: 숨김 위젯 목록 표시 + show-all API 호출
// AC-DP-003: 기본값 초기화 버튼 → reset API 호출

import { expect, test } from '@playwright/test'
import { clearSession, loginAsSuperAdmin } from './fixtures/auth'

const DEFAULT_PREF = {
  theme: 'SYSTEM',
  density: 'STANDARD',
  font_scale: 1.0,
  color_palette: 'DEFAULT',
  sidebar_collapsed: false,
  hidden_widget_instance_ids: {},
  updated_at: '2026-01-01T00:00:00Z',
}

function makePref(overrides: Partial<typeof DEFAULT_PREF>) {
  return { ...DEFAULT_PREF, ...overrides }
}

test.describe('대시보드 개인화 설정', () => {
  test.beforeEach(async ({ page }) => {
    await clearSession(page)

    // 일반 dashboard API — catch-all (먼저 등록, 낮은 우선순위)
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

    // preference GET — 기본값 반환 (나중에 등록, 높은 우선순위)
    await page.route('**/api/v1/dashboard/preference', async (route) => {
      if (route.request().method() === 'GET') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify(DEFAULT_PREF),
        })
      } else {
        // PATCH — 기본 응답 (개별 테스트에서 재정의 가능)
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify(DEFAULT_PREF),
        })
      }
    })

    await loginAsSuperAdmin(page)
  })

  // ──────────────────────────────────────────────────────────────
  // AC-DP-001: 테마 변경 → PATCH 전송 + html[data-theme] 갱신
  // ──────────────────────────────────────────────────────────────
  test('AC-DP-001: 다크 테마 선택 시 PATCH(theme=DARK) 전송 및 html data-theme 갱신', async ({
    page,
  }) => {
    // PATCH 요청 캡처용 오버라이드
    let capturedBody: Record<string, unknown> | null = null
    await page.route('**/api/v1/dashboard/preference', async (route) => {
      if (route.request().method() === 'PATCH') {
        capturedBody = JSON.parse(route.request().postData() ?? '{}')
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify(makePref({ theme: 'DARK' })),
        })
      } else {
        await route.continue()
      }
    })

    // 개인화 설정 패널 열기
    await page.getByRole('button', { name: '개인화 설정' }).click()
    await expect(page.getByTestId('dashboard-preference-panel')).toBeVisible()

    // 다크 테마 클릭
    await page.getByTestId('theme-radio').getByText('다크').click()

    // debounce 완료 대기 (300ms + 여유)
    await page.waitForTimeout(450)

    // PATCH 요청에 theme: DARK 포함 확인
    expect(capturedBody).not.toBeNull()
    expect((capturedBody as Record<string, unknown>).theme).toBe('DARK')

    // html[data-theme="dark"] 적용 확인
    await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark')
  })

  // ──────────────────────────────────────────────────────────────
  // AC-DP-002: 숨김 위젯 목록 표시 + "모든 위젯 표시" API 호출
  // ──────────────────────────────────────────────────────────────
  test('AC-DP-002: 숨김 위젯 목록이 표시되고 전체 표시 버튼 클릭 시 API 호출', async ({
    page,
  }) => {
    // hidden 위젯이 포함된 preference 반환
    await page.route('**/api/v1/dashboard/preference', async (route) => {
      if (route.request().method() === 'GET') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify(
            makePref({
              hidden_widget_instance_ids: { '1': ['inst-aaa', 'inst-bbb'] },
            }),
          ),
        })
      } else {
        await route.continue()
      }
    })

    // show-all API mock
    let showAllCalled = false
    await page.route('**/api/v1/dashboard/preference/widgets/*/show-all', async (route) => {
      showAllCalled = true
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(DEFAULT_PREF),
      })
    })

    // 개인화 설정 패널 열기
    await page.getByRole('button', { name: '개인화 설정' }).click()
    await expect(page.getByTestId('dashboard-preference-panel')).toBeVisible()

    // 숨김 위젯 목록 노출 확인
    await expect(page.getByTestId('hidden-widget-list')).toBeVisible()

    // "모든 위젯 표시" 버튼 클릭
    await page.getByTestId('show-all-button').click()

    // show-all API 호출 확인
    expect(showAllCalled).toBe(true)
  })

  // ──────────────────────────────────────────────────────────────
  // AC-DP-003: 기본값으로 초기화 → reset API 호출 + 성공 메시지
  // ──────────────────────────────────────────────────────────────
  test('AC-DP-003: 기본값으로 초기화 클릭 시 reset API 호출', async ({ page }) => {
    let resetCalled = false
    await page.route('**/api/v1/dashboard/preference/reset', async (route) => {
      resetCalled = true
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(DEFAULT_PREF),
      })
    })

    // 개인화 설정 패널 열기
    await page.getByRole('button', { name: '개인화 설정' }).click()
    await expect(page.getByTestId('dashboard-preference-panel')).toBeVisible()

    // 기본값으로 초기화 클릭
    await page.getByTestId('reset-button').click()

    // reset API 호출 확인
    expect(resetCalled).toBe(true)
  })
})
