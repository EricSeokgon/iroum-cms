// SPEC-CMS-PUBLIC-E2E-001 — API 목 픽스처
// 백엔드 없이 CI E2E 실행을 위한 page.route() 기반 API 목
//
// 동작 원리:
//   - Vite dev server는 /api/* 를 localhost:8080으로 프록시한다.
//   - CI 환경에서 localhost:8080이 실행되지 않으면 프록시 오류(502 등)가 발생한다.
//   - axios 응답 인터셉터는 5xx 오류 시 /error/500으로 강제 리다이렉트한다.
//   - page.route()는 브라우저 레벨에서 요청을 차단하므로 프록시에 도달하지 않는다.
//
// 사용법:
//   beforeEach(async ({ page }) => {
//     await mockAllApis(page)  // 반드시 page.goto() 이전에 호출
//     await clearAuth(page)
//   })
//
// 우선순위 규칙 (Playwright):
//   나중에 등록된 page.route()가 우선 적용된다.
//   테스트 개별 route 등록은 mockAllApis 호출 후에 하면 캐치올보다 우선된다.

import type { Page } from '@playwright/test'

const EMPTY_PAGE = JSON.stringify({
  content: [],
  totalElements: 0,
  totalPages: 0,
  size: 10,
  number: 0,
})

// @MX:ANCHOR: [AUTO] E2E API 목 진입점 — 모든 공공 E2E spec의 beforeEach에서 호출
// @MX:REASON: 7개 spec 파일이 공유 (fan_in >= 3)
// @MX:SPEC: SPEC-CMS-PUBLIC-E2E-001
export async function mockAllApis(page: Page): Promise<void> {
  await page.route('**/api/v1/**', async (route) => {
    const url = route.request().url()

    // App.vue onMounted: 점검 모드 확인
    if (url.includes('/system/health')) {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ status: 'UP', maintenanceMode: false }),
      })
      return
    }

    // App.vue onMounted: 메뉴 트리 조회
    if (url.includes('/content/menus')) {
      await route.fulfill({ status: 200, contentType: 'application/json', body: '[]' })
      return
    }

    // noticeApi: 게시판 마스터 ID 조회 (NOTICE 코드)
    if (url.includes('/board/masters/code/')) {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ id: 1, code: 'NOTICE', name: '공지사항' }),
      })
      return
    }

    // faqApi: 카테고리 목록 (배열 반환)
    if (url.includes('/faqs/categories')) {
      await route.fulfill({ status: 200, contentType: 'application/json', body: '[]' })
      return
    }

    // statsApi: KPI 값, 대시보드 위젯 (배열 반환)
    if (url.includes('/kpi/values') || url.includes('/dashboard/widgets')) {
      await route.fulfill({ status: 200, contentType: 'application/json', body: '[]' })
      return
    }

    // 기본 캐치올: board/posts, faqs, policy/programs, search 등 페이지네이션 응답
    await route.fulfill({ status: 200, contentType: 'application/json', body: EMPTY_PAGE })
  })
}
