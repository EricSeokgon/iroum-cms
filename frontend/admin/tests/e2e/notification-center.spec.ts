// @MX:NOTE: [AUTO] 관리자 알림 센터 E2E 검증 — AC-NC-006/007/008
// @MX:SPEC: SPEC-CMS-NOTIFICATION-CENTER-001
//
// AC-NC-006: 헤더 배지 — 미읽음 수 표시 / 접근성
// AC-NC-007: 목록/필터 화면 — 진입/필터/모두읽음/빈상태
// AC-NC-008: 딥링크 — refType 기반 라우팅
//
// 모킹 전략(dashboard-preference.spec.ts 패턴):
//   - page.route() 로 모든 API 인터셉트 (실제 백엔드 없음)
//   - catch-all 모킹을 beforeEach 에서 먼저 등록(낮은 우선순위)
//   - 개별 테스트에서 구체 경로를 재등록(높은 우선순위, 마지막 등록 우선)
//
// 실제 구현 확인 결과(태스크 명세와 차이):
//   - 라우트에 /admin 접두사 없음 → /notifications, /board/posts/42/edit
//   - DTO 필드 camelCase: refType, refId, readAt, archivedAt, createdAt
//   - unread-count 응답: { unreadCount: N }
//   - 모두읽음 엔드포인트: PATCH /read-all → { updatedCount: N }
//   - 행 클릭(el-table @row-click) 으로 딥링크 이동

import { expect, test } from '@playwright/test'
import { clearSession, loginAsSuperAdmin } from './fixtures/auth'

// ── 알림 데이터 픽스처 ─────────────────────────────────────────────────────────
type Severity = 'INFO' | 'WARN' | 'ERROR'
type Status = 'UNREAD' | 'READ' | 'ARCHIVED'

interface AdminNotification {
  id: number
  type: string
  severity: Severity
  title: string
  body: string | null
  refType: string | null
  refId: number | null
  status: Status
  readAt: string | null
  archivedAt: string | null
  createdAt: string
}

function makeNotification(overrides: Partial<AdminNotification> = {}): AdminNotification {
  return {
    id: 1,
    type: 'POST_APPROVAL_REQUEST',
    severity: 'INFO',
    title: '게시글 승인 요청',
    body: '새로운 게시글이 승인을 기다리고 있습니다.',
    refType: 'POST',
    refId: 42,
    status: 'UNREAD',
    readAt: null,
    archivedAt: null,
    createdAt: '2026-06-01T00:00:00Z',
    ...overrides,
  }
}

function makePageResponse(items: AdminNotification[], total: number = items.length) {
  return {
    content: items,
    totalElements: total,
    totalPages: Math.max(1, Math.ceil(total / 20)),
    number: 0,
    size: 20,
  }
}

async function jsonRoute(route: import('@playwright/test').Route, body: unknown, status = 200) {
  await route.fulfill({
    status,
    contentType: 'application/json',
    body: JSON.stringify(body),
  })
}

test.describe('관리자 알림 센터', () => {
  test.beforeEach(async ({ page }) => {
    await clearSession(page)

    // 공통 catch-all 모킹 (먼저 등록 → 낮은 우선순위)
    await page.route('**/api/v1/dashboard/**', (route) =>
      jsonRoute(route, { content: [], totalElements: 0 }),
    )
    await page.route('**/api/v1/system/maintenance**', (route) =>
      jsonRoute(route, { active: false }),
    )
    await page.route('**/api/v1/me/**', (route) => jsonRoute(route, {}))

    // 미읽음 카운트 — 기본 0 (개별 테스트에서 재정의)
    await page.route('**/api/v1/admin/notifications/unread-count', (route) =>
      jsonRoute(route, { unreadCount: 0 }),
    )

    // 알림 목록 — 기본 빈 목록 (개별 테스트에서 재정의)
    await page.route('**/api/v1/admin/notifications?**', (route) =>
      jsonRoute(route, makePageResponse([])),
    )
  })

  // ════════════════════════════════════════════════════════════════════════════
  // AC-NC-006 — 헤더 배지
  // ════════════════════════════════════════════════════════════════════════════

  test('AC-NC-006-1: 미읽음 7건 로그인 시 종 아이콘에 빨간 배지 "7" 노출', async ({ page }) => {
    await page.route('**/api/v1/admin/notifications/unread-count', (route) =>
      jsonRoute(route, { unreadCount: 7 }),
    )

    await loginAsSuperAdmin(page)

    const badge = page.getByTestId('notification-badge')
    await expect(badge).toBeVisible()
    await expect(badge).toHaveText('7')
  })

  test('AC-NC-006-2: 미읽음 0건이면 빨간 배지 미노출 (종 아이콘만)', async ({ page }) => {
    await page.route('**/api/v1/admin/notifications/unread-count', (route) =>
      jsonRoute(route, { unreadCount: 0 }),
    )

    await loginAsSuperAdmin(page)

    // 종 버튼 자체는 노출
    await expect(page.getByTestId('notification-bell')).toBeVisible()
    // 빨간 배지는 미노출 (v-if unreadCount > 0)
    await expect(page.getByTestId('notification-badge')).toHaveCount(0)
  })

  test('AC-NC-006-3: 미읽음 150건이면 "99+" 로 표시', async ({ page }) => {
    await page.route('**/api/v1/admin/notifications/unread-count', (route) =>
      jsonRoute(route, { unreadCount: 150 }),
    )

    await loginAsSuperAdmin(page)

    const badge = page.getByTestId('notification-badge')
    await expect(badge).toBeVisible()
    await expect(badge).toHaveText('99+')
  })

  test('AC-NC-006-4: 종 버튼 aria-label 이 "미읽음 알림 7개" 와 일치 (접근성)', async ({
    page,
  }) => {
    await page.route('**/api/v1/admin/notifications/unread-count', (route) =>
      jsonRoute(route, { unreadCount: 7 }),
    )

    await loginAsSuperAdmin(page)

    await expect(page.getByTestId('notification-bell')).toHaveAttribute(
      'aria-label',
      '미읽음 알림 7개',
    )
  })

  // ════════════════════════════════════════════════════════════════════════════
  // AC-NC-007 — 목록/필터 화면
  // ════════════════════════════════════════════════════════════════════════════

  test('AC-NC-007-1: /notifications 진입 시 NotificationCenterView 렌더 + 기본 목록 표시', async ({
    page,
  }) => {
    const items = [
      makeNotification({ id: 1, severity: 'INFO', title: '정보 알림', status: 'UNREAD' }),
      makeNotification({ id: 2, severity: 'ERROR', title: '오류 알림', status: 'READ' }),
    ]
    await page.route('**/api/v1/admin/notifications?**', (route) =>
      jsonRoute(route, makePageResponse(items)),
    )

    await loginAsSuperAdmin(page)
    await page.goto('/notification-center')

    await expect(page.getByTestId('notification-center')).toBeVisible()
    await expect(page.getByTestId('notification-filter')).toBeVisible()

    const table = page.getByTestId('notification-table')
    await expect(table).toBeVisible()
    await expect(table.getByText('정보 알림')).toBeVisible()
    await expect(table.getByText('오류 알림')).toBeVisible()
  })

  test('AC-NC-007-2: ERROR 심각도만 체크 시 목록이 ERROR 항목만으로 갱신', async ({ page }) => {
    // 단일 핸들러로 severity 파라미터에 따라 분기 (마지막 등록 → beforeEach catch-all 보다 우선).
    // axios 기본 직렬화는 배열을 severity[]=ERROR 로 인코딩하므로, 대괄호/비대괄호 양쪽을 모두 검사한다.
    // severity=ERROR 포함 → ERROR 만, 그 외 → INFO + ERROR 혼합.
    await page.route('**/api/v1/admin/notifications?**', (route) => {
      const url = route.request().url()
      const hasError = /[?&]severity(?:%5B%5D|\[\])?=ERROR(?:&|$)/.test(url)
      if (hasError) {
        return jsonRoute(
          route,
          makePageResponse([makeNotification({ id: 2, severity: 'ERROR', title: '오류 알림' })]),
        )
      }
      return jsonRoute(
        route,
        makePageResponse([
          makeNotification({ id: 1, severity: 'INFO', title: '정보 알림' }),
          makeNotification({ id: 2, severity: 'ERROR', title: '오류 알림' }),
        ]),
      )
    })

    await loginAsSuperAdmin(page)
    await page.goto('/notification-center')

    const table = page.getByTestId('notification-table')
    await expect(table.getByText('정보 알림')).toBeVisible()
    await expect(table.getByText('오류 알림')).toBeVisible()

    // 심각도 select 열고 ERROR 선택 (el-select multiple)
    // 드롭다운 패널은 body 에 렌더되므로 getByTestId 범위 밖, .el-select-dropdown__item 으로 옵션을 찾는다.
    // 옵션 클릭 → @change → store.setFilter + reload(severity=ERROR) 재조회를 명시적으로 대기한다.
    const reload = page.waitForResponse(
      (res) =>
        res.request().method() === 'GET' &&
        /\/api\/v1\/admin\/notifications(\?|$)/.test(res.url()) &&
        res.url().includes('severity'),
    )
    await page.getByTestId('filter-severity').click()
    const dropdown = page.locator('.el-select-dropdown:visible')
    await expect(dropdown).toBeVisible()
    // el-select multiple 항목은 텍스트만 "ERROR" (INFO/WARN 과 부분일치 없음) — 안전하게 exact 매칭.
    await dropdown.getByText('ERROR', { exact: true }).click()
    await reload

    // 목록이 ERROR 만으로 갱신 (severity=ERROR 재조회 응답)
    await expect(table.getByText('오류 알림')).toBeVisible()
    await expect(table.getByText('정보 알림')).toHaveCount(0)
  })

  test('AC-NC-007-3: "모두 읽음" → 확인 다이얼로그 → read-all API 호출 → 성공 토스트', async ({
    page,
  }) => {
    await page.route('**/api/v1/admin/notifications?**', (route) =>
      jsonRoute(
        route,
        makePageResponse([
          makeNotification({ id: 1, status: 'UNREAD', title: '미읽음 알림' }),
        ]),
      ),
    )

    let readAllCalled = false
    await page.route('**/api/v1/admin/notifications/read-all', (route) => {
      readAllCalled = true
      return jsonRoute(route, { updatedCount: 3 })
    })

    await loginAsSuperAdmin(page)
    await page.goto('/notification-center')

    await page.getByTestId('mark-all-read-btn').click()

    // ElMessageBox.confirm 다이얼로그 노출 → 확인 버튼 클릭
    const dialog = page.locator('.el-message-box')
    await expect(dialog).toBeVisible()
    await dialog.locator('.el-button--primary').click()

    // read-all API 호출 확인
    await expect.poll(() => readAllCalled).toBe(true)

    // 성공 토스트: i18n "{count}개 알림을 읽음 처리했습니다" (마침표 없음) → "3개 알림을 읽음 처리했습니다"
    await expect(page.getByText('3개 알림을 읽음 처리했습니다')).toBeVisible()
  })

  test('AC-NC-007-4: 빈 결과 시 "받은 알림이 없습니다." 안내 노출', async ({ page }) => {
    await page.route('**/api/v1/admin/notifications?**', (route) =>
      jsonRoute(route, makePageResponse([])),
    )

    await loginAsSuperAdmin(page)
    await page.goto('/notification-center')

    await expect(page.getByText('받은 알림이 없습니다')).toBeVisible()
  })

  // ════════════════════════════════════════════════════════════════════════════
  // AC-NC-008 — 딥링크
  // ════════════════════════════════════════════════════════════════════════════

  test('AC-NC-008-1: refType=POST/refId=42 카드 클릭 → /board/posts/42/edit 이동 + 읽음 API 호출', async ({
    page,
  }) => {
    await page.route('**/api/v1/admin/notifications?**', (route) =>
      jsonRoute(
        route,
        makePageResponse([
          makeNotification({
            id: 42,
            refType: 'POST',
            refId: 42,
            status: 'UNREAD',
            title: '게시글 승인 요청',
          }),
        ]),
      ),
    )

    let readCalled = false
    await page.route('**/api/v1/admin/notifications/42/read', (route) => {
      readCalled = true
      return jsonRoute(route, {})
    })
    // 딥링크 대상 뷰(PostFormView 수정 모드)가 호출하는 boardApi.getPost 모킹
    await page.route('**/api/v1/**/posts/**', (route) =>
      jsonRoute(route, {
        title: '제목',
        content: '<p>내용</p>',
        pinned: false,
        status: 'PUBLISHED',
        attachments: [],
      }),
    )

    await loginAsSuperAdmin(page)
    await page.goto('/notification-center')

    // el-table 행 클릭 → @row-click → 딥링크
    await page.getByTestId('notification-table').getByText('게시글 승인 요청').click()

    await expect(page).toHaveURL(/\/board\/posts\/42\/edit/)
    await expect.poll(() => readCalled).toBe(true)
  })

  test('AC-NC-008-2: refType=NULL 카드 클릭 → 라우팅 없음 + "이동 가능한 리소스" 토스트', async ({
    page,
  }) => {
    await page.route('**/api/v1/admin/notifications?**', (route) =>
      jsonRoute(
        route,
        makePageResponse([
          makeNotification({
            id: 7,
            refType: null,
            refId: null,
            status: 'READ', // READ 이면 클릭 시 읽음 API 미호출 (라우팅/토스트만 검증)
            title: '리소스 없는 알림',
          }),
        ]),
      ),
    )

    await loginAsSuperAdmin(page)
    await page.goto('/notification-center')

    await page.getByTestId('notification-table').getByText('리소스 없는 알림').click()

    // 라우팅 미발생 — 여전히 /notification-center
    await expect(page).toHaveURL(/\/notification-center/)
    // 안내 토스트 — i18n noRefResource (마침표 없음)
    await expect(page.getByText('이동 가능한 리소스가 없습니다')).toBeVisible()
  })

  test('AC-NC-008-3: 알 수 없는 refType 카드 클릭 → 콘솔 경고 + 읽음 처리', async ({ page }) => {
    await page.route('**/api/v1/admin/notifications?**', (route) =>
      jsonRoute(
        route,
        makePageResponse([
          makeNotification({
            id: 99,
            refType: 'UNKNOWN_TYPE',
            refId: 5,
            status: 'UNREAD',
            title: '미지원 타입 알림',
          }),
        ]),
      ),
    )

    let readCalled = false
    await page.route('**/api/v1/admin/notifications/99/read', (route) => {
      readCalled = true
      return jsonRoute(route, {})
    })

    const consoleWarnings: string[] = []
    page.on('console', (msg) => {
      if (msg.type() === 'warning') consoleWarnings.push(msg.text())
    })

    await loginAsSuperAdmin(page)
    await page.goto('/notification-center')

    await page.getByTestId('notification-table').getByText('미지원 타입 알림').click()

    // 라우팅 미발생
    await expect(page).toHaveURL(/\/notification-center/)
    // 읽음 API 호출 (UNREAD 였으므로)
    await expect.poll(() => readCalled).toBe(true)
    // 콘솔 경고 발생 — "[NotificationCenter] Unknown refType: UNKNOWN_TYPE"
    await expect
      .poll(() => consoleWarnings.some((w) => w.includes('Unknown refType')))
      .toBe(true)
  })
})
