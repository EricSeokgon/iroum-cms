---
id: SPEC-CMS-PUBLIC-E2E-001
version: 0.1.0
status: Draft
priority: High
---

# SPEC-CMS-PUBLIC-E2E-001 (Compact) — Public SPA Playwright E2E

목적: `frontend/public/` Vue 3 SPA에 Playwright 1.x E2E 도입. KWCAG 2.2 AA P0 게이트.

---

## EARS 요구사항 (15)

**REQ-E2E-001** [UBIQUITOUS] `@playwright/test` ^1.48.0 추가 + `playwright.config.ts` 생성.

**REQ-E2E-002** [UBIQUITOUS] `data-testid`/ARIA/의미론적 HTML 우선. CSS 클래스 셀렉터 금지.

**REQ-E2E-003** [UBIQUITOUS] Chromium 단일. CI `retries: 2`, `workers: 1`, `forbidOnly: true`.

**REQ-E2E-004** [UBIQUITOUS] 홈(`/`) → `data-testid="home-search-form"` 제출 → `/search?q=...` 이동 + `search-result-list` 표시.

**REQ-E2E-005** [UBIQUITOUS] `/notices` → `select#notice-category` + `input#notice-keyword` + 페이지네이션 동작 검증.

**REQ-E2E-006** [UBIQUITOUS] `/notices/:id` 진입 + 목록 복귀.

**REQ-E2E-007** [UBIQUITOUS] `/faqs` Tab 포커스 + Enter/Space 토글 + `aria-expanded` 전환.

**REQ-E2E-008** [UBIQUITOUS] `/search` 6탭 (ALL/POST/FAQ/QNA/POLICY/SAFETY) URL `?type=` + `aria-selected` 갱신.

**REQ-E2E-009** [UBIQUITOUS] `/policies/match` 5필드 폼 제출 → `POST /policies/match` + `policy-match-results` 카드.

**REQ-E2E-010** [STATE-DRIVEN] `requiresAuth: true` 라우트에서 `public.accessToken` 없으면 `/login?redirect={path}` 리다이렉트.

**REQ-E2E-011** [UBIQUITOUS] `/error/403`, `/error/500`, `/:unknown`, `/maintenance` 라우팅. 모두 `noLayout: true`.

**REQ-E2E-012** [UBIQUITOUS — KWCAG 2.2 AA] 첫 Tab → 스킵네비 포커스. Enter → `role="main"` 점프.

**REQ-E2E-013** [UBIQUITOUS — KWCAG 2.2 AA] 모든 input은 `<label>` 또는 `aria-label`. 에러 시 `aria-invalid="true"` + `aria-describedby` 연결.

**REQ-E2E-014** [UNWANTED] 백엔드 데이터 쓰기 사이드이펙트 금지. 테스트 계정 격리 또는 롤백 전략 필수.

**REQ-E2E-015** [UBIQUITOUS] CI `frontend-e2e` job 추가. `needs: [frontend-test]`. `playwright-report/` 14일 아티팩트.

---

## 인수 시나리오 (9 + 6 엣지)

### S1: 홈 영웅 검색 (REQ-E2E-004)
- Given: `/` 접속, `home-hero` visible
- When: `home-search-input`에 "청년 창업" 입력 + form submit
- Then: URL `/search?q=청년+창업` + `search-result-list` 표시

### S2: 공지 카테고리 필터 (REQ-E2E-005)
- Given: `/notices` 접속, `notice-list` 렌더링
- When: `select#notice-category`에서 "이벤트"(EVENT) 선택
- Then: URL에 `?categoryCode=EVENT` + 목록 갱신

### S3: FAQ 키보드 펼치기 (REQ-E2E-007)
- Given: `/faqs`, 첫 FAQ 헤더 Tab 포커스 (`aria-expanded="false"`)
- When: Enter 키
- Then: `aria-expanded="true"` + 패널 visible. Space → 복귀

### S4: 검색 탭 전환 (REQ-E2E-008)
- Given: `/search?q=지원` (ALL 활성)
- When: POST 탭(`role="tab"`) 클릭
- Then: URL `?type=POST` + `aria-selected="true"` 전환 + 결과 갱신

### S5: 정책 매칭 제출 (REQ-E2E-009)
- Given: `/policies/match` 폼 렌더링
- When: 5필드 (industry/capitalAmount/revenueAmount/employeeCount/region) 입력 + 제출
- Then: `POST /policies/match` 호출 + `policy-match-results`에 결과 카드 (match-score, match-reason)

### S6: 인증 리다이렉트 (REQ-E2E-010)
- Given: `localStorage.public.accessToken` 부재
- When: `/qnas/new` 직접 접근
- Then: `/login?redirect=%2Fqnas%2Fnew`

### S7: 404 페이지 (REQ-E2E-011)
- Given: `/non-existent-path-xyz` 접근
- Then: `NotFoundView` 렌더링 + "페이지를 찾을 수 없습니다" + PublicLayout 부재

### S8: 스킵 네비게이션 (REQ-E2E-012)
- Given: `/` 접속
- When: 첫 Tab
- Then: 스킵네비 링크 포커스 + visible
- When: Enter
- Then: `role="main"` 또는 `id="main-content"` 포커스 이동
- 적용: `/`, `/notices`, `/faqs`, `/search?q=지원`, `/policies/match` (5+ 페이지)

### S9: 폼 에러 ARIA (REQ-E2E-013)
- Given: `loginAs(page, {token})` 후 `/qnas/new` 접속
- When: title 비우고 제출
- Then: `aria-invalid="true"` + `aria-describedby`로 에러 메시지 연결

### 엣지 케이스
- E1: 공지 빈 결과 → EmptyState 표시
- E2: 검색 빈 쿼리(`/search`) → 검색 input 자동 포커스
- E3: 정책 401 → 리다이렉트 없음 (requiresAuth=false)
- E4: 공지 503/네트워크 오류 → ErrorState + 재시도 버튼
- E5: FAQ 다중 항목 Tab 순서 보존
- E6: 검색 페이지네이션 + 탭 전환 시 page 리셋

---

## 파일 (생성 9 + 수정 2)

생성:
- `frontend/public/playwright.config.ts` (@MX:NOTE)
- `frontend/public/tests/e2e/fixtures/auth.ts` (@MX:ANCHOR)
- `frontend/public/tests/e2e/home.spec.ts`
- `frontend/public/tests/e2e/notices.spec.ts`
- `frontend/public/tests/e2e/faq.spec.ts`
- `frontend/public/tests/e2e/search.spec.ts`
- `frontend/public/tests/e2e/policy-match.spec.ts`
- `frontend/public/tests/e2e/error-pages.spec.ts`
- `frontend/public/tests/e2e/a11y.spec.ts` (@MX:NOTE)

수정:
- `frontend/public/package.json` (devDep + scripts)
- `.github/workflows/ci.yml` (frontend-e2e job @MX:NOTE)

---

## 핵심 설정 (Playwright)

- `testDir: './tests/e2e'`
- `baseURL: 'http://localhost:5174'`
- `webServer: { command: 'pnpm run dev', url: 'http://localhost:5174', reuseExistingServer: !process.env.CI }`
- `projects: [{ name: 'chromium', use: devices['Desktop Chrome'] }]`
- CI: `retries: 2`, `workers: 1`, `forbidOnly: true`
- `reporter: [['html'], ['list']]`

---

## TDD 사이클 (per spec)

1. RED: spec 작성 → `pnpm run test:e2e {file}` 실패 확인 (@MX:TODO)
2. GREEN: 셀렉터 매칭 / 최소 SPA 수정 → 통과
3. REFACTOR: 헬퍼 추출 (gotoAndWaitForLoad, fillFormFields), @MX:TODO → @MX:NOTE

---

## Exclusions

- Admin SPA E2E (별도 SPEC)
- 신규 백엔드 API
- LoginView 실제 구현
- Firefox/Safari/WebKit
- Lighthouse/Web Vitals 성능
- 시각 회귀
- 모바일 뷰포트
- i18n 언어 전환
- 백엔드 전체 스택 통합 E2E

---

## CI 게이트

1. 9 specs 모두 Chromium 0 failed (retries 2 환경)
2. `playwright-report/` 14일 아티팩트 (`if: always()`)
3. `needs: [frontend-test]`
4. 백엔드 서비스 컨테이너 헬스체크 통과
5. CSS 클래스 셀렉터 0건 (REQ-E2E-002)
6. 백엔드 쓰기 사이드이펙트 0건 (REQ-E2E-014)

---

## 의존성

- SPEC-CMS-002 (공지), SPEC-CMS-003 (FAQ), SPEC-CMS-004 (검색), SPEC-CMS-005 (정책)
- SPEC-CMS-006~010, SPEC-MEDIA-001
- 백엔드 `localhost:8080` 가용성 (CI: Docker service container)
