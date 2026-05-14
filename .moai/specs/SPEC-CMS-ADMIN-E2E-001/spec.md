---
id: SPEC-CMS-ADMIN-E2E-001
version: 0.2.0
status: Implemented
created: 2026-05-14
updated: 2026-05-14
author: ircp
priority: High
issue_number: 0
parent: SPEC-CMS-001
---

# SPEC-CMS-ADMIN-E2E-001 — Admin SPA Playwright E2E 테스트 도입

## HISTORY

| Version | Date       | Author | Change                                                                                                  |
|---------|------------|--------|---------------------------------------------------------------------------------------------------------|
| 0.2.0   | 2026-05-14 | ircp   | Implemented: Playwright 1.48 E2E 20/21 통과, CI 통합 완료                                               |
| 0.1.0   | 2026-05-14 | ircp   | 초기 Draft 작성. Admin SPA(`frontend/admin/`)에 Playwright 1.x E2E 테스트 도입 — 12개 REQ, 10개 인수 시나리오. Pinia 런타임 메모리 기반 인증 특성을 반영한 `page.route()` 모킹 전략 채택. |

---

## 1. 개요 (Overview)

### 1.1 배경

`iroum-cms`는 공공기관 CMS 플랫폼으로, 시민 대상 공개 SPA(`frontend/public/`)와 관리자 SPA(`frontend/admin/`)로 구성된다. SPEC-CMS-PUBLIC-E2E-001로 공개 SPA E2E 테스트가 도입되었으며, 본 SPEC은 그 자매 SPEC으로 **관리자 SPA**에 동일한 E2E 인프라를 도입한다.

현재 Admin SPA는 **Vitest 2.1.8 + jsdom** 기반 단위/컴포넌트 테스트(53개 파일, 949 라인)만 보유하며, **E2E 테스트는 0개**이다. 또한 76개 view 중 단 5개 파일만 `data-testid`를 보유(총 19개)하여 E2E 테스트 셀렉터 안정성 확보를 위한 추가 작업이 필요하다.

본 SPEC은 Admin SPA에 **Playwright 1.x 기반 E2E 테스트**를 도입하여 핵심 관리자 여정(로그인/인증가드/대시보드/사용자/역할/공지/비밀번호 변경/에러 페이지)을 검증하고, **KWCAG 2.2 AA 접근성**을 자동 검증한다.

### 1.2 목적

- 실제 브라우저(Chromium)에서 관리자 SPA의 사용자 여정 검증
- Pinia 런타임 메모리 기반 인증(공개 SPA의 localStorage 방식과 다름) 특성에 맞는 E2E 인증 전략 확립
- 기존 jsdom 단위 테스트로 검증 불가한 라우터 가드, 키보드 탐색, ARIA 동작 검증
- KWCAG 2.2 AA 접근성을 출시 게이트(P0 mandatory)로 자동 검증
- CI 워크플로우에 `frontend-e2e-admin` job 통합으로 회귀 방지
- 76개 view 중 핵심 뷰(LoginView, DashboardMainView, UserListView, RoleMatrixView, NoticeListView)에 `data-testid` 추가하여 셀렉터 안정성 확보

### 1.3 범위

- **포함**: Admin SPA(`frontend/admin/`)의 Phase 1 P0 사용자 여정 + KWCAG 2.2 AA 자동 검증 + CI 통합
- **제외**: Public SPA, 신규 백엔드 API 개발, P1 워크플로우(공지 CRUD, RBAC 권한 제한, 사용자 상세), Firefox/Safari, 성능 측정, 백엔드 전체 스택 통합

---

## 2. EARS 요구사항 (Requirements)

### 2.1 인프라 및 설정 (Infrastructure)

**REQ-ADMIN-E2E-INFRA-001** [UBIQUITOUS]
The system shall add `@playwright/test` ^1.48.0 and `@axe-core/playwright` ^4.10.0 as `devDependency` entries to `frontend/admin/package.json` and create a `frontend/admin/playwright.config.ts` configuration file with `baseURL: 'http://localhost:5173'` and `webServer.command: 'pnpm run dev'`.

**REQ-ADMIN-E2E-INFRA-002** [UBIQUITOUS]
모든 E2E 테스트는 `data-testid`, ARIA 속성(`role`, `aria-label`, `aria-controls`, `aria-expanded`, `aria-invalid`), 의미론적 HTML 셀렉터(`label[for]`, `<main>`, `<nav>`), 그리고 LoginView의 기존 `id="username"`/`id="password"` 셀렉터를 우선 사용하고, **CSS 클래스 기반 셀렉터(`.btn-primary`, `.el-button--primary` 등)는 금지**한다.

---

### 2.2 인증 모킹 (Authentication Mocking)

**REQ-ADMIN-E2E-AUTH-001** [UBIQUITOUS] — Mock JWT 인증 헬퍼
The system shall provide an `tests/e2e/fixtures/auth.ts` helper module that uses Playwright `page.route()` to intercept `POST /api/v1/auth/login` and return a structurally valid mock JWT decodable by the admin SPA's `decodeJwt()` function. The mock JWT payload shall contain `{ sub: 'admin', uid: 1, roles: ['SUPER_ADMIN'], exp: 9999999999, iat: 1000000000 }` so that `useAuthStore().user` is correctly populated after login.

**REQ-ADMIN-E2E-AUTH-002** [STATE-DRIVEN] — Pinia 런타임 메모리 인증 격리
**While** the admin SPA uses Pinia runtime memory (NOT localStorage) for authentication state, **when** each test starts via `beforeEach`, **then** the test shall clear cookies and storage and (when authentication is required) perform a fresh mocked login via the `loginAsSuperAdmin(page)` helper to guarantee a clean authenticated state per test.

---

### 2.3 핵심 사용자 여정 (Core User Journeys)

**REQ-ADMIN-E2E-LOGIN-001** [EVENT-DRIVEN] — 로그인 성공 플로우
The system shall verify the login success journey on `/login`: **when** the user fills `#username` with `"admin"`, `#password` with `"any-password"`, and submits the form **while** the mocked `POST /api/v1/auth/login` returns a valid mock JWT, **then** the SPA shall navigate to `/dashboard` and render the dashboard layout (visible `data-testid="admin-layout"` or `<main role="main">`).

**REQ-ADMIN-E2E-LOGIN-002** [EVENT-DRIVEN] — 로그인 실패 처리
The system shall verify the login failure journey: **when** the mocked `POST /api/v1/auth/login` returns HTTP 401 with `{ error: 'INVALID_CREDENTIALS' }`, **then** the SPA shall remain on `/login` and `data-testid="login-error"` shall become visible with `role="alert"` and aria-live region announcing the error.

**REQ-ADMIN-E2E-GUARD-001** [STATE-DRIVEN] — 미인증 보호 라우트 리다이렉트
**While** the user is unauthenticated (no token in Pinia store), **when** the user navigates to any protected route (e.g., `/dashboard`, `/users`, `/roles`), **then** the router shall redirect to `/login?redirect={originalFullPath}` with the original path URL-encoded as the `redirect` query parameter.

**REQ-ADMIN-E2E-DASHBOARD-001** [UBIQUITOUS] — 대시보드 렌더링
The system shall verify the dashboard journey (`/dashboard`): after successful mocked login, the page shall render `data-testid="dashboard-main"` containing summary widgets, and the global navigation (`<nav role="navigation">`) shall expose the main menu items (users, roles, notices) as accessible links.

**REQ-ADMIN-E2E-USERS-001** [UBIQUITOUS] — 사용자 목록 조회
The system shall verify the user list page (`/users`): after authenticated navigation with mocked `GET /api/v1/users` returning at least 3 user records, the page shall render `data-testid="user-list-table"` (an Element Plus table or `role="table"`) with each user row exposing the username as a column value.

**REQ-ADMIN-E2E-ROLES-001** [UBIQUITOUS] — 역할/권한 매트릭스 조회
The system shall verify the role matrix page (`/roles`): after authenticated navigation with mocked `GET /api/v1/roles` and `GET /api/v1/permissions`, the page shall render `data-testid="role-matrix"` containing role rows and permission columns in a `role="grid"` or `role="table"` structure.

**REQ-ADMIN-E2E-NOTICES-001** [UBIQUITOUS] — 공지사항 목록 조회
The system shall verify the notice list page (`/notices`): after authenticated navigation with mocked `GET /api/v1/notices` returning at least 1 notice record, the page shall render `data-testid="notice-list-table"` exposing each notice row with title and category visible to the user.

**REQ-ADMIN-E2E-PWCHANGE-001** [EVENT-DRIVEN] — 비밀번호 변경 폼 유효성 검사
The system shall verify the password change form (`/account/password`): **when** the user submits the form with `data-testid="input-new"` and `data-testid="input-confirm"` containing different values, **then** the form shall block submission, `data-testid="error-alert"` shall become visible, and `data-testid="btn-submit"` shall remain enabled for retry without triggering a network request.

**REQ-ADMIN-E2E-LOGOUT-001** [EVENT-DRIVEN] — 로그아웃 플로우
The system shall verify the logout journey: **when** the authenticated user activates the logout control (`data-testid="btn-logout"` or `aria-label="로그아웃"`), **then** the Pinia auth store shall clear the token and the router shall redirect to `/login` without a `redirect` query parameter.

**REQ-ADMIN-E2E-ERROR-001** [UBIQUITOUS] — 404 NotFound 페이지
The system shall verify error page routing: unknown paths (`/:pathMatch(.*)`) shall render the NotFound view (`data-testid="not-found"` or visible heading containing "404" / "찾을 수 없"). The page must not require authentication.

---

### 2.4 접근성 (KWCAG 2.2 AA — P0 Mandatory Gate)

**REQ-ADMIN-E2E-A11Y-001** [UBIQUITOUS — KWCAG 2.2 AA] — 로그인 페이지 axe-core 위반 0건
The system shall verify the login page accessibility: running `@axe-core/playwright` analysis on `/login` shall report **zero critical or serious violations** for WCAG 2.2 AA tags. The color-contrast rule is **excluded** from this gate (deferred to a dedicated visual review SPEC).

**REQ-ADMIN-E2E-A11Y-002** [UBIQUITOUS — KWCAG 2.2 AA] — 대시보드 axe-core 위반 0건
The system shall verify the dashboard accessibility: after authenticated navigation to `/dashboard`, running `@axe-core/playwright` analysis shall report **zero critical or serious violations** for WCAG 2.2 AA tags, excluding the color-contrast rule.

---

### 2.5 안전성 (Safety / Negative Requirements)

**REQ-ADMIN-E2E-SAFETY-001** [UNWANTED]
E2E 테스트는 실제 백엔드 데이터를 직접 수정하거나 삭제하는 사이드이펙트를 **발생시켜서는 안 된다**. 모든 API 호출은 `page.route()` 모킹으로 처리되어야 하며, 실제 `http://localhost:8080` 백엔드 서비스가 기동되지 않은 상태에서도 모든 P0 테스트가 통과해야 한다.

---

### 2.6 CI 통합 (CI Integration)

**REQ-ADMIN-E2E-CI-001** [UBIQUITOUS] — CI 워크플로우 추가
The system shall add a `frontend-e2e-admin` job to `.github/workflows/ci.yml`. The job shall:
- Execute after `frontend-test` completes successfully (`needs: [frontend-test]`)
- Run on Ubuntu 22.04 with Node 22 and pnpm
- Install Playwright browsers (Chromium only) via `pnpm exec playwright install chromium --with-deps`
- Execute `pnpm --filter @iroum/admin run test:e2e`
- Use `page.route()` mocking — **no backend service container required** (REQ-ADMIN-E2E-SAFETY-001)
- Upload `frontend/admin/playwright-report/` as an artifact with **14-day retention**
- Fail the workflow on any test failure (no `continue-on-error`)

---

## 3. 비기능 요구사항 (Non-Functional Requirements)

| 항목                | 요구사항                                                                            |
|---------------------|-------------------------------------------------------------------------------------|
| 결정성 (Determinism) | CI 환경 `retries: 2`, `workers: 1`로 flaky test 0% 목표                              |
| 셀렉터 안정성        | `data-testid` / ARIA / 의미론적 HTML 우선. CSS 클래스 금지. (REQ-ADMIN-E2E-INFRA-002) |
| 접근성 (P0)         | KWCAG 2.2 AA axe-core 자동 검증 통과가 출시 게이트 (color-contrast 제외)             |
| 인증 격리            | Pinia 런타임 메모리 — 매 테스트 `beforeEach`에서 cookies/storage clear + mocked re-login |
| 환경 분리            | 로컬: `localhost:5173` (Vite). CI: page.route() 모킹으로 백엔드 의존성 제거          |
| 보안                | 백엔드 데이터 쓰기 사이드이펙트 금지 (REQ-ADMIN-E2E-SAFETY-001)                      |
| data-testid 보강     | 최소 5개 핵심 view에 data-testid 추가 (DashboardMainView, UserListView, RoleMatrixView, NoticeListView, NotFoundView) |

---

## 4. 제약 사항 (Constraints)

- **신규 백엔드 API 개발 금지**: 본 SPEC은 기존 백엔드 API 계약(SPEC-CMS-AUTH, SPEC-CMS-USER, SPEC-CMS-002 등)을 소비하기만 한다
- **frontend/admin/src/ 로직 변경 금지**: `data-testid` 속성 추가만 허용. 컴포넌트 로직(스크립트, 스타일, 라우터, 스토어) 변경 금지
- **Pinia 런타임 메모리 인증 보존**: `useAuthStore` 구조와 `accessToken` ref 메모리 저장 방식 유지. localStorage 마이그레이션 금지
- **개발 모드 TDD 준수**: `.moai/config/sections/quality.yaml`의 `development_mode: tdd`에 따라 RED → GREEN → REFACTOR 사이클 실행
- **참조 일관성**: SPEC-CMS-PUBLIC-E2E-001의 `fixtures/auth.ts` 패턴 및 Playwright 설정 구조를 참조하여 두 SPA의 E2E 인프라가 동형(同形)이 되도록 유지
- **포트 분리**: Admin 5173, Public 5174 — 동시 실행 시 충돌 방지

---

## 5. 구현 대상 파일 (Files in Scope)

### 5.1 신규 생성 (Create)

| 파일 경로                                              | 목적                                |
|--------------------------------------------------------|-------------------------------------|
| `frontend/admin/playwright.config.ts`                  | Playwright 기본 설정 (Chromium, port 5173) |
| `frontend/admin/tests/e2e/fixtures/auth.ts`            | page.route() mock JWT 인증 헬퍼     |
| `frontend/admin/tests/e2e/login.spec.ts`               | 로그인 성공/실패 (REQ-ADMIN-E2E-LOGIN-001, 002) |
| `frontend/admin/tests/e2e/auth-guard.spec.ts`          | 인증 가드 리다이렉트 (REQ-ADMIN-E2E-GUARD-001) |
| `frontend/admin/tests/e2e/dashboard.spec.ts`           | 대시보드 렌더링 (REQ-ADMIN-E2E-DASHBOARD-001) |
| `frontend/admin/tests/e2e/users.spec.ts`               | 사용자 목록 (REQ-ADMIN-E2E-USERS-001) |
| `frontend/admin/tests/e2e/roles.spec.ts`               | 역할 매트릭스 (REQ-ADMIN-E2E-ROLES-001) |
| `frontend/admin/tests/e2e/notices.spec.ts`             | 공지 목록 (REQ-ADMIN-E2E-NOTICES-001) |
| `frontend/admin/tests/e2e/password-change.spec.ts`     | 비밀번호 변경 폼 (REQ-ADMIN-E2E-PWCHANGE-001) |
| `frontend/admin/tests/e2e/logout.spec.ts`              | 로그아웃 (REQ-ADMIN-E2E-LOGOUT-001) |
| `frontend/admin/tests/e2e/error-pages.spec.ts`         | 404 NotFound (REQ-ADMIN-E2E-ERROR-001) |
| `frontend/admin/tests/e2e/a11y.spec.ts`                | KWCAG 2.2 AA (REQ-ADMIN-E2E-A11Y-001, 002) |

### 5.2 수정 (Modify) — data-testid 추가만 허용

| 파일 경로                                                       | 변경 내용                                       |
|-----------------------------------------------------------------|-------------------------------------------------|
| `frontend/admin/package.json`                                   | `@playwright/test`, `@axe-core/playwright` devDependency 추가 + npm scripts (`test:e2e`, `test:e2e:ui`, `test:e2e:report`) |
| `frontend/admin/src/layouts/AdminLayout.vue`                    | `data-testid="admin-layout"` + `<main role="main">` 보강 |
| `frontend/admin/src/views/dashboard/DashboardMainView.vue`      | `data-testid="dashboard-main"` 추가              |
| `frontend/admin/src/views/users/UserListView.vue`               | `data-testid="user-list-table"` 추가             |
| `frontend/admin/src/views/roles/RoleMatrixView.vue`             | `data-testid="role-matrix"` 추가                 |
| `frontend/admin/src/views/notices/NoticeListView.vue`           | `data-testid="notice-list-table"` 추가           |
| `frontend/admin/src/views/error/NotFoundView.vue`               | `data-testid="not-found"` 추가                   |
| `frontend/admin/src/components/layout/AdminHeader.vue` (또는 유사) | `data-testid="btn-logout"` 추가 (기존 로그아웃 버튼) |
| `.github/workflows/ci.yml`                                      | `frontend-e2e-admin` job 추가 (REQ-ADMIN-E2E-CI-001) |

---

## 6. 기술 접근법 (Technical Approach)

### 6.1 Playwright 설정 핵심 항목

| 설정 키          | 값                                                  |
|------------------|-----------------------------------------------------|
| `testDir`        | `'./tests/e2e'`                                     |
| `baseURL`        | `'http://localhost:5173'`                           |
| `webServer`      | `{ command: 'pnpm run dev', url: 'http://localhost:5173', reuseExistingServer: !process.env.CI }` |
| `projects`       | `[{ name: 'chromium', use: devices['Desktop Chrome'] }]` (Phase 1 단일 브라우저) |
| `retries`        | CI: `2`, Local: `0`                                  |
| `workers`        | CI: `1`, Local: `undefined` (auto)                  |
| `forbidOnly`     | `!!process.env.CI`                                  |
| `reporter`       | `[['html'], ['list']]`                              |

### 6.2 Mock JWT 인증 헬퍼 패턴 (`fixtures/auth.ts`) — Public SPA와의 핵심 차이

Public SPA는 `localStorage.setItem('public.accessToken', ...)`로 인증 상태를 주입할 수 있지만, Admin SPA는 Pinia 런타임 메모리에만 토큰을 저장하므로 **반드시 폼 제출 또는 API 모킹 경유 실제 login flow를 거쳐야 한다**.

본 SPEC은 **`page.route()` 기반 mock login** 전략을 채택한다 (백엔드 의존성 0):

- `mockAuthApi(page)`: `POST /api/v1/auth/login` 인터셉트 → 유효한 mock JWT 반환
- Mock JWT 구조: `header(eyJhbGciOiJIUzI1NiJ9).payload(base64url({sub:'admin',uid:1,roles:['SUPER_ADMIN'],exp:9999999999,iat:1000000000})).mock-signature`
- `loginAsSuperAdmin(page)`: `mockAuthApi` 등록 + `/login` 방문 + `#username`/`#password` 채우기 + submit + `/dashboard` 도달 대기
- `loginViaApiOnly(page)`: 폼 우회 — `mockAuthApi` 등록 후 `page.evaluate()`로 store 직접 호출(고급 시나리오용, P1)
- 각 테스트 `beforeEach`에서 `context.clearCookies()` + `localStorage.clear()` 호출로 격리 보장

**근거**: Pinia store는 reactive ref이므로 page reload 시 항상 소실 → storageState 재사용 불가 → 모든 테스트는 매번 mock login 수행이 가장 단순하고 결정적임.

### 6.3 data-testid 격차 해소 전략

76개 view 중 단 5개 파일만 data-testid 보유 → Phase 1 E2E는 다음 5개 핵심 view에 data-testid 추가:

| View | 추가할 data-testid | 위치 |
|------|-------------------|------|
| AdminLayout.vue | `admin-layout` | 최상위 wrapper |
| DashboardMainView.vue | `dashboard-main` | 컨테이너 root |
| UserListView.vue | `user-list-table` | 테이블 또는 list root |
| RoleMatrixView.vue | `role-matrix` | grid/table root |
| NoticeListView.vue | `notice-list-table` | 테이블 root |
| NotFoundView.vue | `not-found` | 페이지 root |
| AdminHeader.vue (또는 동등) | `btn-logout` | 로그아웃 버튼 |

**원칙**: data-testid는 비파괴적 추가 (`data-testid="..."`만 삽입). 로직, 스타일, props 변경 일절 없음.

### 6.4 axe-core 통합 (`a11y.spec.ts`)

`@axe-core/playwright`의 `AxeBuilder` 활용:
- `withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa'])` — KWCAG 2.2 AA 매핑
- `disableRules(['color-contrast'])` — 색대비는 별도 시각 회귀 SPEC으로 분리 (research.md 권고)
- 위반 보고 시: critical/serious만 fail 트리거, moderate/minor는 warning으로 console.log

### 6.5 TDD 사이클 (REQ-ADMIN-E2E-LOGIN-001 예시)

1. **RED**: `login.spec.ts`에 로그인 성공 시나리오 작성 → `pnpm run test:e2e login` 실행 → `data-testid="admin-layout"` 부재로 실패
2. **GREEN**: `AdminLayout.vue`에 `data-testid="admin-layout"` 추가 → 테스트 통과
3. **REFACTOR**: `fixtures/auth.ts`의 `loginAsSuperAdmin` 헬퍼로 공통 로직 추출 → 재실행 통과

---

## 7. 의존성 (Dependencies)

| 의존 항목                      | 관계                                                       |
|--------------------------------|------------------------------------------------------------|
| SPEC-CMS-PUBLIC-E2E-001        | 자매 SPEC — fixtures/Playwright 설정 패턴 참조             |
| SPEC-CMS-001 (Umbrella)        | 상위 SPEC (parent metadata에 명시)                          |
| frontend/admin/src/stores/auth.ts | useAuthStore 인터페이스 안정성에 의존 (login, logout, isAuthenticated) |
| frontend/admin/src/router/index.ts | 라우터 가드 동작에 의존 (requiresAuth meta + redirect query) |
| frontend/admin/src/views/auth/LoginView.vue | 기존 `id="username"`, `id="password"` 셀렉터 유지 |

---

## 8. MX Tag Plan

| 파일 | Tag Type | 사유 |
|------|---------|------|
| `tests/e2e/fixtures/auth.ts` | `@MX:ANCHOR` | 모든 인증 E2E 테스트가 의존하는 핵심 헬퍼 (fan_in >= 7) |
| `tests/e2e/fixtures/auth.ts` (mockAuthApi 함수) | `@MX:NOTE` | Pinia 런타임 메모리 인증 특성으로 인한 mock JWT 구조 요구사항 명시 |
| `tests/e2e/a11y.spec.ts` | `@MX:NOTE` | color-contrast 제외 결정 근거 + KWCAG 2.2 AA 매핑 태그 명시 |
| `playwright.config.ts` | `@MX:NOTE` | CI 환경 retries/workers 결정 근거 (deterministic test execution) |
| `.github/workflows/ci.yml` (frontend-e2e-admin job) | `@MX:NOTE` | backend service container 미사용 결정 근거 (page.route() 모킹) |

---

## 9. Exclusions (What NOT to Build)

본 SPEC은 다음 범위를 명시적으로 **포함하지 않는다**:

- **Public SPA E2E 테스트** — SPEC-CMS-PUBLIC-E2E-001로 별도 관리
- **신규 백엔드 API 개발** — 기존 백엔드 API 계약(SPEC-CMS-AUTH 등)을 소비만. 신규 엔드포인트 추가/변경 금지
- **Pinia 인증 구조 변경** — `useAuthStore`의 ref 기반 메모리 저장 방식 보존. localStorage 마이그레이션 별도 SPEC에서 다룸
- **P1 시나리오** — 공지 CRUD(생성/수정/삭제), 사용자 상세(`/users/:id`), RBAC 권한 제한 검증(SUPER_ADMIN vs ADMIN), FAQ/QnA/Publications 관리 — 별도 후속 SPEC
- **Firefox/Safari/WebKit 브라우저 테스트** — Chromium 단일 브라우저로 시작
- **성능 측정 테스트** (Lighthouse, Web Vitals) — 별도 SPEC
- **시각 회귀 테스트** (Visual Regression / Screenshot diff) — 색대비 검증 포함 별도 SPEC
- **모바일 뷰포트 E2E** — 데스크톱(Desktop Chrome) 기본
- **i18n 언어 전환 E2E** — 한국어 기본
- **OTP 비밀번호 찾기 흐름** (`/forgot-password`) — P1 후속 SPEC
- **백엔드 통합 E2E** (Spring Boot ↔ Vue 전체 스택) — 별도 인프라 필요
- **forbidden(403)/server-error(500) 페이지 E2E** — Admin SPA에서 해당 라우트가 명시적으로 존재하지 않으므로 본 SPEC에서 제외

---

## 10. 수용 기준 요약 (Acceptance Summary)

상세 시나리오는 `acceptance.md` 참조. 핵심 게이트:

- [ ] Chromium에서 11개 spec 파일 모두 통과 (0 failed)
- [ ] KWCAG 2.2 AA 자동 검증 통과 — 로그인 + 대시보드 (color-contrast 제외)
- [ ] CI `frontend-e2e-admin` job 통과 + `playwright-report` 아티팩트 업로드 (14일 보존)
- [ ] 백엔드 데이터 쓰기 사이드이펙트 0건 (REQ-ADMIN-E2E-SAFETY-001)
- [ ] 셀렉터 검증: CSS 클래스 기반 셀렉터 0건 (REQ-ADMIN-E2E-INFRA-002)
- [ ] 5개 핵심 view에 data-testid 추가 완료 (DashboardMainView, UserListView, RoleMatrixView, NoticeListView, NotFoundView)
- [ ] 인증 모킹 안정성: 매 테스트 격리된 mock login으로 flaky 0%

---

## 11. 참고 (References)

- `research.md` (동일 디렉토리) — 라우트 맵 56개, RBAC 역할, data-testid 인벤토리, 위험 요소 분석
- `.moai/specs/SPEC-CMS-PUBLIC-E2E-001/spec.md` — 자매 SPEC, fixtures 패턴 참조 원본
- `.moai/project/tech.md` — 기술 스택 및 Playwright 도입 계획
- `.moai/config/sections/quality.yaml` — `development_mode: tdd`
- KWCAG 2.2 AA: https://www.wah.or.kr/Participation/KWCAG.asp
- Playwright 공식 문서: https://playwright.dev/docs/intro
- @axe-core/playwright: https://github.com/dequelabs/axe-core-npm/tree/develop/packages/playwright
