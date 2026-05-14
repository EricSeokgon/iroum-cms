---
id: SPEC-CMS-PUBLIC-E2E-001
version: 0.2.0
status: Implemented
created: 2026-05-14
updated: 2026-05-14
author: ircp
priority: High
issue_number: 0
---

# SPEC-CMS-PUBLIC-E2E-001 — Public SPA Playwright E2E 테스트 도입

## HISTORY

| Version | Date       | Author | Change                                                                                                  |
|---------|------------|--------|---------------------------------------------------------------------------------------------------------|
| 0.1.0   | 2026-05-14 | ircp   | 초기 Draft 작성. Public SPA(`frontend/public/`)에 Playwright 1.x E2E 테스트 도입 — 15개 REQ, 9개 인수 시나리오. |
| 0.2.0   | 2026-05-14 | ircp   | Playwright E2E 39개 테스트 구현 완료 (36 pass, 3 skip-백엔드 미기동). 상태 Draft → Implemented. |

---

## 1. 개요 (Overview)

### 1.1 배경

`iroum-cms`는 공공기관 CMS 플랫폼으로, 시민 대상 공개 SPA(`frontend/public/`)와 관리자 SPA(`frontend/admin/`)로 구성된다. 현재 공개 SPA는 **Vitest 2.1.8 + jsdom** 기반 단위/컴포넌트 테스트(30개 파일, 3,216 라인)만 보유하며, **E2E 테스트는 0개**이다.

본 SPEC은 공개 SPA에 **Playwright 1.x 기반 E2E 테스트**를 도입하여 핵심 사용자 여정(홈/공지/FAQ/검색/정책 매칭/에러 페이지)을 검증하고, **KWCAG 2.2 AA 접근성**을 자동 검증한다.

### 1.2 목적

- 실제 브라우저(Chromium)에서 공개 SPA의 사용자 여정 검증
- 기존 jsdom 단위 테스트로 검증 불가한 라우팅, 키보드 탐색, ARIA 동작 검증
- KWCAG 2.2 AA 접근성을 출시 게이트(P0 mandatory)로 자동 검증
- CI 워크플로우에 `frontend-e2e` job 통합으로 회귀 방지

### 1.3 범위

- **포함**: 공개 SPA(`frontend/public/`)의 Phase 1 P0 사용자 여정 + KWCAG 2.2 AA 자동 검증 + CI 통합
- **제외**: Admin SPA, 신규 백엔드 API 개발, LoginView 실제 구현, Firefox/Safari, 성능 측정, 백엔드 전체 스택 통합

---

## 2. EARS 요구사항 (Requirements)

### 2.1 인프라 및 설정 (Infrastructure)

**REQ-E2E-001** [UBIQUITOUS]
The system shall add `@playwright/test` ^1.48.0 as a `devDependency` to `frontend/public/package.json` and create a `frontend/public/playwright.config.ts` configuration file.

**REQ-E2E-002** [UBIQUITOUS]
모든 E2E 테스트는 `data-testid`, ARIA 속성(`role`, `aria-label`, `aria-controls`), 의미론적 HTML 셀렉터를 **우선** 사용하고, **CSS 클래스 기반 셀렉터(`.btn-primary` 등)는 금지**한다.

**REQ-E2E-003** [UBIQUITOUS]
The system shall execute E2E tests on **Chromium browser only** (Phase 1) and in CI environment shall use `retries: 2`, `workers: 1`, `forbidOnly: true` settings to ensure deterministic test execution.

---

### 2.2 핵심 사용자 여정 (Core User Journeys)

**REQ-E2E-004** [UBIQUITOUS] — 홈 영웅 검색
The system shall verify the core user journey on the home page (`/`): when the user enters a keyword into `data-testid="home-search-input"` within `data-testid="home-search-form"` and submits the form, the SPA shall navigate to `/search?q={keyword}` and render `data-testid="search-result-list"`.

**REQ-E2E-005** [UBIQUITOUS] — 공지 목록 필터/검색
The system shall verify the notice list page (`/notices`): category filter (`select#notice-category`) selection, keyword search (`input#notice-keyword`) submission, and pagination navigation must each update the URL query parameters and refresh `data-testid="notice-list"`.

**REQ-E2E-006** [UBIQUITOUS] — 공지 상세 및 복귀
The system shall verify the notice detail page (`/notices/:id`): direct navigation from the notice list must render the detail view, and "목록으로 돌아가기" interaction must return the user to `/notices` while preserving filter/page state.

**REQ-E2E-007** [UBIQUITOUS] — FAQ 아코디언 키보드 접근성
The system shall verify FAQ accordion (`/faqs`) keyboard accessibility: `Tab` key focuses the next FAQ header button, `Enter` or `Space` toggles the expanded state, and `aria-expanded` attribute must transition correctly between `"false"` and `"true"`.

**REQ-E2E-008** [UBIQUITOUS] — 검색 6탭 필터
The system shall verify the search page (`/search`) 6-tab filter: tab switching across `ALL → POST → FAQ → QNA → POLICY → SAFETY` must update the URL `?type=` parameter, set `aria-selected="true"` on the active tab (`role="tab"`), and refresh `data-testid="search-result-list"`.

**REQ-E2E-009** [UBIQUITOUS] — 정책 매칭 폼 제출
The system shall verify the policy match form (`/policies/match`): after the user fills required fields (`industry`, `capitalAmount`, `revenueAmount`, `employeeCount`, `region`) and submits the form, the SPA shall issue a `POST /policies/match` request and render result cards in `data-testid="policy-match-results"`.

---

### 2.3 인증 및 라우팅 (Authentication & Routing)

**REQ-E2E-010** [STATE-DRIVEN] — 인증 필요 라우트 리다이렉트
**While** the route requires authentication (`requiresAuth: true`), **when** there is no `public.accessToken` value in `localStorage`, **then** the router shall redirect to `/login?redirect={originalPath}` with the original target path URL-encoded as the `redirect` query parameter.

**REQ-E2E-011** [UBIQUITOUS] — 에러 페이지 라우팅
The system shall verify error page routing: `/error/403` renders `ForbiddenView`, `/error/500` renders `ServerErrorView`, unknown paths (`/:pathMatch(.*)`) render `NotFoundView`, and `/maintenance` renders `MaintenanceView`. All four pages must use `noLayout: true` (PublicLayout 미사용).

---

### 2.4 접근성 (KWCAG 2.2 AA — P0 Mandatory Gate)

**REQ-E2E-012** [UBIQUITOUS — KWCAG 2.2 AA]
The system shall verify skip navigation: on every page, the **first `Tab` key press** shall move focus to the skip navigation link (visible state), and **activation** (`Enter` key) shall move focus to the `<main>` content area (`role="main"` or `id="main-content"`).

**REQ-E2E-013** [UBIQUITOUS — KWCAG 2.2 AA]
The system shall verify form accessibility: every form input field must have an associated `<label for="...">` element **OR** `aria-label` attribute. On validation error, the input must transition to `aria-invalid="true"` and `aria-describedby` must reference the visible error message element ID.

---

### 2.5 안전성 (Safety / Negative Requirements)

**REQ-E2E-014** [UNWANTED]
E2E 테스트는 백엔드 데이터를 직접 수정하거나 삭제하는 사이드이펙트를 **발생시켜서는 안 된다**. `POST /qnas` 등 데이터 쓰기 API를 호출하는 테스트는 **테스트 전용 계정 격리** 또는 **트랜잭션 롤백 전략** 또는 **AfterEach 정리 hook**을 반드시 사용해야 한다.

---

### 2.6 CI 통합 (CI Integration)

**REQ-E2E-015** [UBIQUITOUS] — CI 워크플로우 추가
The system shall add a `frontend-e2e` job to `.github/workflows/ci.yml`. The job shall:
- Execute after `frontend-test` completes successfully (`needs: [frontend-test]`)
- Provide backend API on `http://localhost:8080` via Docker service container or mock server
- Run on Ubuntu 22.04 with Node 22 and pnpm
- Upload `frontend/public/playwright-report/` as an artifact with **14-day retention**
- Fail the workflow on any test failure (no `continue-on-error`)

---

## 3. 비기능 요구사항 (Non-Functional Requirements)

| 항목                | 요구사항                                                                            |
|---------------------|-------------------------------------------------------------------------------------|
| 결정성 (Determinism) | CI 환경 `retries: 2`, `workers: 1`로 flaky test 0% 목표                              |
| 셀렉터 안정성        | `data-testid` / ARIA / 의미론적 HTML 우선. CSS 클래스 금지.                          |
| 접근성 (P0)         | KWCAG 2.2 AA 자동 검증 통과가 출시 게이트                                            |
| 격리성              | 테스트 간 localStorage/sessionStorage/cookie 초기화 (`storageState` 사용 안 함)       |
| 환경 분리           | 개발: `localhost:5174` (Vite) + `localhost:8080` (Spring). CI: Docker 서비스 컨테이너 |
| 보안                | 백엔드 데이터 쓰기 사이드이펙트 금지 (REQ-E2E-014)                                  |

---

## 4. 제약 사항 (Constraints)

- **신규 백엔드 API 개발 금지**: 기존 SPEC-CMS-002~010, MEDIA-001 API만 사용
- **LoginView 미구현 상태 유지**: Phase 0 스텁 — 인증 E2E는 `localStorage` 직접 주입으로 시뮬레이션
- **`public.` localStorage 키 prefix 유지**: `public.accessToken`, `public.refreshToken`
- **Vue Router noLayout 라우트 보존**: `/login`, `/maintenance`, `/error/*`, `/:pathMatch(.*)`는 `noLayout: true`
- **개발 모드 TDD 준수**: `.moai/config/sections/quality.yaml`의 `development_mode: tdd`에 따라 RED → GREEN → REFACTOR 사이클 실행

---

## 5. 구현 대상 파일 (Files in Scope)

### 5.1 신규 생성 (Create)

| 파일 경로                                              | 목적                                |
|--------------------------------------------------------|-------------------------------------|
| `frontend/public/playwright.config.ts`                 | Playwright 기본 설정 (Chromium, CI) |
| `frontend/public/tests/e2e/fixtures/auth.ts`           | localStorage 인증 헬퍼              |
| `frontend/public/tests/e2e/home.spec.ts`               | 홈 + 영웅 검색 (REQ-E2E-004)        |
| `frontend/public/tests/e2e/notices.spec.ts`            | 공지 목록/상세 (REQ-E2E-005, 006)   |
| `frontend/public/tests/e2e/faq.spec.ts`                | FAQ 키보드 (REQ-E2E-007)            |
| `frontend/public/tests/e2e/search.spec.ts`             | 검색 6탭 (REQ-E2E-008)              |
| `frontend/public/tests/e2e/policy-match.spec.ts`       | 정책 매칭 (REQ-E2E-009)             |
| `frontend/public/tests/e2e/error-pages.spec.ts`        | 에러 페이지 (REQ-E2E-011)           |
| `frontend/public/tests/e2e/a11y.spec.ts`               | KWCAG 2.2 AA (REQ-E2E-012, 013)     |

### 5.2 수정 (Modify)

| 파일 경로                          | 변경 내용                                                       |
|------------------------------------|-----------------------------------------------------------------|
| `frontend/public/package.json`     | `@playwright/test` devDependency 추가 + npm scripts (`test:e2e`) |
| `.github/workflows/ci.yml`         | `frontend-e2e` job 추가 (REQ-E2E-015)                          |

---

## 6. 기술 접근법 (Technical Approach)

### 6.1 Playwright 설정 핵심 항목

| 설정 키          | 값                                                  |
|------------------|-----------------------------------------------------|
| `testDir`        | `'./tests/e2e'`                                     |
| `baseURL`        | `'http://localhost:5174'`                           |
| `webServer`      | `{ command: 'pnpm run dev', url: 'http://localhost:5174', reuseExistingServer: !process.env.CI }` |
| `projects`       | `[{ name: 'chromium', use: devices['Desktop Chrome'] }]` (Phase 1 단일 브라우저) |
| `retries`        | CI: `2`, Local: `0`                                  |
| `workers`        | CI: `1`, Local: `undefined` (auto)                  |
| `forbidOnly`     | `!!process.env.CI`                                  |
| `reporter`       | `[['html'], ['list']]`                              |

### 6.2 인증 헬퍼 패턴 (`fixtures/auth.ts`)

LoginView가 Phase 0 스텁 상태이므로, 인증 상태는 `localStorage` 직접 주입으로 시뮬레이션:

- `loginAs(page, { token })`: `page.addInitScript()`로 `public.accessToken` 주입
- `clearAuth(page)`: 모든 `public.*` 키 제거
- 각 테스트 `beforeEach`에서 `localStorage.clear()` 호출로 격리 보장

### 6.3 CI 백엔드 의존성

- **로컬**: 개발자가 `backend/`에서 `./gradlew bootRun`으로 `localhost:8080` 기동
- **CI**: GitHub Actions `services` 블록에 `iroum-cms-backend:latest` Docker 이미지를 서비스 컨테이너로 등록 → `localhost:8080`에서 응답
- **대안 (Phase 2)**: MSW(Mock Service Worker)로 백엔드 mock 응답 — 별도 SPEC

### 6.4 TDD 사이클 (REQ-E2E-004 예시)

1. **RED**: `home.spec.ts`에 시나리오 작성 → `pnpm run test:e2e home` 실행 → 실패 확인
2. **GREEN**: SPA `HomeView.vue`가 이미 구현되어 있으므로 셀렉터/동작 확인 → 통과 검증
3. **REFACTOR**: 공통 헬퍼 (`navigateAndWaitForRoute`) 추출 → 재실행

---

## 7. 의존성 (Dependencies)

| 의존 항목                     | 관계                                                       |
|-------------------------------|------------------------------------------------------------|
| SPEC-CMS-002 (공지)           | 공지 목록/상세 E2E는 공지 API 응답 필요                    |
| SPEC-CMS-003 (FAQ)            | FAQ 아코디언 E2E는 FAQ API 응답 필요                       |
| SPEC-CMS-004 (검색)           | 검색 6탭 E2E는 통합 검색 API 필요                          |
| SPEC-CMS-005 (정책)           | 정책 매칭 E2E는 `POST /policies/match` API 필요            |
| SPEC-CMS-006 ~ 010, MEDIA-001 | 기존 백엔드 API 응답 안정성에 의존                         |

---

## 8. Exclusions (What NOT to Build)

본 SPEC은 다음 범위를 명시적으로 **포함하지 않는다**:

- **Admin SPA(`frontend/admin/`) E2E 테스트** — 별도 SPEC(SPEC-CMS-ADMIN-E2E-001 예정)으로 관리
- **신규 백엔드 API 개발** — 본 SPEC은 기존 SPEC-CMS-002~010, MEDIA-001 API만 소비. 신규 엔드포인트 추가/변경 금지
- **LoginView 실제 구현** — Phase 0 스텁 상태 유지. 인증 E2E는 localStorage 직접 주입으로 시뮬레이션(인증 폼 자체 검증은 Phase 1 이후 별도 SPEC)
- **Firefox/Safari/WebKit 브라우저 테스트** — Chromium 단일 브라우저로 시작. 크로스 브라우저는 Phase 1 이후 별도 SPEC
- **성능 측정 테스트** (Lighthouse, Web Vitals, Core Vitals) — 별도 SPEC(SPEC-CMS-PERF-001 예정)으로 관리
- **백엔드 통합 E2E** (Spring Boot ↔ Vue 전체 스택 시나리오 테스트) — 별도 인프라(Docker Compose 전체 스택) 필요. 본 SPEC은 SPA E2E만 다룸
- **시각 회귀 테스트** (Visual Regression / Screenshot diff) — Phase 1 이후 별도 SPEC
- **모바일 뷰포트 E2E** — 데스크톱(Desktop Chrome) 기본. 반응형 뷰포트는 Phase 2
- **i18n 언어 전환 E2E** — 한국어 기본. 다국어 전환은 별도 SPEC

---

## 9. 수용 기준 요약 (Acceptance Summary)

상세 시나리오는 `acceptance.md` 참조. 핵심 게이트:

- [ ] Chromium에서 9개 spec 파일 모두 통과 (0 failed)
- [ ] KWCAG 2.2 AA 자동 검증 통과 (스킵네비, 폼 ARIA)
- [ ] CI `frontend-e2e` job 통과 + `playwright-report` 아티팩트 업로드
- [ ] 백엔드 데이터 쓰기 사이드이펙트 0건 (REQ-E2E-014)
- [ ] 셀렉터 검증: CSS 클래스 기반 셀렉터 0건 (REQ-E2E-002)

---

## 10. 참고 (References)

- `research.md` (동일 디렉토리) — 라우트 맵, API 엔드포인트, data-testid 인벤토리
- `.moai/project/tech.md` — 기술 스택 및 Playwright 도입 계획
- `.moai/config/sections/quality.yaml` — `development_mode: tdd`
- KWCAG 2.2 AA: https://www.wah.or.kr/Participation/KWCAG.asp
- Playwright 공식 문서: https://playwright.dev/docs/intro
