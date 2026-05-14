# Plan: SPEC-CMS-PUBLIC-E2E-001 — Public SPA Playwright E2E 도입

**Version**: 0.1.0
**Status**: Draft
**Created**: 2026-05-14
**Updated**: 2026-05-14
**Development Mode**: TDD (per `.moai/config/sections/quality.yaml`)

---

## 1. 구현 개요 (Implementation Overview)

본 계획은 `frontend/public/` 공개 SPA에 Playwright 1.x E2E 테스트 인프라를 도입하고, 9개 spec 파일로 핵심 사용자 여정과 KWCAG 2.2 AA를 검증하며, CI 워크플로우에 `frontend-e2e` job을 통합한다.

**전체 단계 수**: 10개 우선순위 기반 태스크 (시간 추정 없음, 우선순위 라벨 사용)
**개발 방법론**: TDD RED → GREEN → REFACTOR
**병렬 실행 여부**: Task 3~9(spec 파일 작성)는 인프라(Task 1~2) 완료 후 병렬 가능

---

## 2. 태스크 분할 (Task Breakdown)

### Task 1 — Playwright 설치 및 설정 (Priority: High)

**목적**: Playwright 인프라 부트스트랩
**REQ 매핑**: REQ-E2E-001, REQ-E2E-003
**선행 조건**: 없음 (시작점)

**작업 내용**:
- `frontend/public/package.json`에 `@playwright/test` ^1.48.0 devDependency 추가
- `pnpm add -D @playwright/test --filter=@iroum-cms/public` 실행
- `pnpm exec playwright install chromium` 실행 (브라우저 바이너리 설치)
- `frontend/public/playwright.config.ts` 생성 (testDir, baseURL, webServer, projects, CI 설정)
- `frontend/public/package.json`에 npm scripts 추가:
  - `"test:e2e": "playwright test"`
  - `"test:e2e:ui": "playwright test --ui"`
  - `"test:e2e:report": "playwright show-report"`
- `frontend/public/.gitignore`에 `playwright-report/`, `test-results/` 추가
- 검증: `pnpm run test:e2e --list` 명령으로 설정 파싱 확인

**산출물**:
- `frontend/public/playwright.config.ts`
- `frontend/public/package.json` (수정)
- `frontend/public/.gitignore` (수정)

---

### Task 2 — 공통 픽스처 작성: 인증 헬퍼 (Priority: High)

**목적**: LoginView 미구현 상태에서 인증 상태 시뮬레이션 헬퍼 제공
**REQ 매핑**: REQ-E2E-010 (인증 라우트 검증의 토대)
**선행 조건**: Task 1

**작업 내용**:
- `frontend/public/tests/e2e/fixtures/auth.ts` 생성
- 함수 `loginAs(page, { token, refreshToken? })`: `page.addInitScript()`로 `public.accessToken` localStorage 주입
- 함수 `clearAuth(page)`: `localStorage.removeItem('public.accessToken')`, `localStorage.removeItem('public.refreshToken')`
- 함수 `expectRedirectedToLogin(page, originalPath)`: `/login?redirect={encodeURIComponent(originalPath)}` 매칭 단언
- 단위 테스트 또는 sanity check spec (`fixtures/auth.test.ts`) — 헬퍼 자체 검증
- 검증: 각 e2e spec의 `beforeEach`에서 `localStorage.clear()` 호출 패턴 확립

**산출물**:
- `frontend/public/tests/e2e/fixtures/auth.ts`

---

### Task 3 — 홈 E2E (Priority: High — P0)

**목적**: 홈 페이지 및 영웅 검색 핵심 여정 검증 (TDD)
**REQ 매핑**: REQ-E2E-004, REQ-E2E-002(셀렉터 규약)
**선행 조건**: Task 1, Task 2

**TDD 사이클**:
- **RED**: `home.spec.ts`에 인수 시나리오 1(홈 영웅 검색) 작성 → `pnpm run test:e2e home` 실패 확인
- **GREEN**: `HomeView.vue`가 이미 구현됨. 실제 셀렉터(`data-testid="home-search-input"`, `data-testid="home-search-form"`) 확인 후 통과 검증
- **REFACTOR**: 공통 라우트 진입 헬퍼 (`gotoAndWaitForLoad`) 추출

**작업 내용**:
- `frontend/public/tests/e2e/home.spec.ts` 생성
- 시나리오: 홈 진입 → 영웅 검색 폼 입력 → 제출 → `/search?q=...` 이동 → 결과 목록 표시
- 추가 검증: `home-notices-section`, `home-policies-section`, `home-quicklinks` 렌더링 확인 (smoke test)

**산출물**:
- `frontend/public/tests/e2e/home.spec.ts`

---

### Task 4 — 공지 E2E (Priority: High — P0)

**목적**: 공지 목록 필터/검색/페이지네이션 및 상세 진입 검증 (TDD)
**REQ 매핑**: REQ-E2E-005, REQ-E2E-006
**선행 조건**: Task 1, Task 2

**TDD 사이클**:
- **RED**: `notices.spec.ts`에 시나리오 2(카테고리 필터) 작성 → 실패 확인
- **GREEN**: `NoticeListView.vue`의 `select#notice-category`, `input#notice-keyword` 셀렉터 검증
- **REFACTOR**: URL 쿼리 파라미터 단언 헬퍼 추출

**작업 내용**:
- `frontend/public/tests/e2e/notices.spec.ts` 생성
- 시나리오: 목록 진입 → 카테고리 필터 → URL 변경 확인 → 키워드 검색 → 페이지네이션 → 상세 진입 → 목록 복귀
- 빈 결과 상태(EmptyState) 검증 포함

**산출물**:
- `frontend/public/tests/e2e/notices.spec.ts`

---

### Task 5 — FAQ 키보드 E2E (Priority: High — P0)

**목적**: FAQ 아코디언 키보드 접근성 검증 (TDD)
**REQ 매핑**: REQ-E2E-007, KWCAG 2.2 AA 일부
**선행 조건**: Task 1, Task 2

**TDD 사이클**:
- **RED**: `faq.spec.ts`에 시나리오 3(키보드 펼치기) 작성 → 실패 확인
- **GREEN**: `FaqView.vue`의 `button[aria-expanded]`, `panel[aria-controls]` 동작 확인
- **REFACTOR**: 키보드 이벤트 헬퍼 (`pressTabUntilFocus`) 추출

**작업 내용**:
- `frontend/public/tests/e2e/faq.spec.ts` 생성
- 시나리오: FAQ 진입 → Tab 키로 헤더 포커스 → Enter 키 → `aria-expanded="true"` → 패널 visible → Space 키 → `aria-expanded="false"`
- 다중 FAQ 항목 간 Tab 이동 순서 검증

**산출물**:
- `frontend/public/tests/e2e/faq.spec.ts`

---

### Task 6 — 검색 6탭 E2E (Priority: High — P0)

**목적**: 통합 검색 6탭 필터 검증 (TDD)
**REQ 매핑**: REQ-E2E-008
**선행 조건**: Task 1, Task 2

**TDD 사이클**:
- **RED**: `search.spec.ts`에 시나리오 4(탭 전환) 작성 → 실패 확인
- **GREEN**: `SearchResultView.vue`의 `role="tablist"`, `role="tab"`, `aria-selected` 동작 확인
- **REFACTOR**: 탭 전환 + URL 단언 헬퍼 추출

**작업 내용**:
- `frontend/public/tests/e2e/search.spec.ts` 생성
- 시나리오: `/search?q=지원` 진입 → ALL 활성 확인 → POST 클릭 → URL `?type=POST` → 결과 갱신 → FAQ → QNA → POLICY → SAFETY 순회
- 빈 쿼리 처리(`/search` 직접 접근) 검증

**산출물**:
- `frontend/public/tests/e2e/search.spec.ts`

---

### Task 7 — 정책 매칭 E2E (Priority: High — P0)

**목적**: 정책 매칭 폼 제출 및 결과 표시 검증 (TDD)
**REQ 매핑**: REQ-E2E-009
**선행 조건**: Task 1, Task 2

**TDD 사이클**:
- **RED**: `policy-match.spec.ts`에 시나리오 5(폼 제출) 작성 → 실패 확인
- **GREEN**: `PolicyMatchView.vue` + `PolicyMatchForm` 동작 확인 (industry, capitalAmount, revenueAmount, employeeCount, region)
- **REFACTOR**: 폼 채움 헬퍼 (`fillPolicyMatchForm`) 추출

**작업 내용**:
- `frontend/public/tests/e2e/policy-match.spec.ts` 생성
- 시나리오: 폼 진입 → 5개 필드 입력 → 제출 → `POST /policies/match` 호출(network listener) → 결과 카드 표시 → `match-score`, `match-reason` 렌더링 확인
- 401 응답 처리 (requiresAuth=false이므로 리다이렉트 없이 빈 결과) 검증

**산출물**:
- `frontend/public/tests/e2e/policy-match.spec.ts`

---

### Task 8 — 에러 페이지 E2E (Priority: High — P0)

**목적**: 404/403/500/유지보수 페이지 라우팅 검증 (TDD)
**REQ 매핑**: REQ-E2E-011
**선행 조건**: Task 1, Task 2

**TDD 사이클**:
- **RED**: `error-pages.spec.ts`에 시나리오 7(404) 작성 → 실패 확인
- **GREEN**: 4개 페이지 (`NotFoundView`, `ForbiddenView`, `ServerErrorView`, `MaintenanceView`) noLayout 동작 확인
- **REFACTOR**: 에러 페이지 진입 헬퍼 (`navigateToErrorPage`) 추출

**작업 내용**:
- `frontend/public/tests/e2e/error-pages.spec.ts` 생성
- 시나리오: `/non-existent-path` → NotFoundView, `/error/403` → ForbiddenView, `/error/500` → ServerErrorView, `/maintenance` → MaintenanceView
- 각 페이지에서 PublicLayout이 사용되지 않음(`noLayout: true`) 검증

**산출물**:
- `frontend/public/tests/e2e/error-pages.spec.ts`

---

### Task 9 — KWCAG 2.2 AA E2E (Priority: High — P0 Mandatory Gate)

**목적**: 접근성 자동 검증 (TDD)
**REQ 매핑**: REQ-E2E-012, REQ-E2E-013
**선행 조건**: Task 1, Task 2

**TDD 사이클**:
- **RED**: `a11y.spec.ts`에 시나리오 8(스킵네비) 작성 → 실패 확인
- **GREEN**: 모든 라우트 첫 Tab 키 → 스킵네비 포커스 → Enter → main 영역 포커스 점프 확인
- **REFACTOR**: 접근성 검증 헬퍼 (`expectSkipNavWorks`, `expectFormAria`) 추출

**작업 내용**:
- `frontend/public/tests/e2e/a11y.spec.ts` 생성
- 시나리오 8: 홈/공지/FAQ/검색/정책 매칭/QnA 페이지에서 스킵네비 동작 검증
- 시나리오 9: QnA 생성 폼에서 빈 제출 → `aria-invalid="true"` + `aria-describedby` 연결 검증 (인증 헬퍼 사용)
- (선택) `axe-playwright` 도입 검토 — Phase 2로 분리 가능

**산출물**:
- `frontend/public/tests/e2e/a11y.spec.ts`

---

### Task 10 — CI 통합 (Priority: High)

**목적**: GitHub Actions `frontend-e2e` job 추가
**REQ 매핑**: REQ-E2E-015
**선행 조건**: Task 1 ~ Task 9 (모든 spec 통과 후)

**작업 내용**:
- `.github/workflows/ci.yml`에 `frontend-e2e` job 추가:
  - `needs: [frontend-test]`
  - `runs-on: ubuntu-22.04`
  - `services`: `iroum-cms-backend` (Docker 서비스 컨테이너) — `image: iroum-cms-backend:latest`, `ports: 8080:8080`
  - Steps: checkout → setup-node 22 → setup-pnpm → `pnpm install --filter=@iroum-cms/public` → `pnpm exec playwright install chromium --with-deps` → `pnpm --filter=@iroum-cms/public run test:e2e`
  - Artifact upload: `actions/upload-artifact@v4` with `path: frontend/public/playwright-report/`, `retention-days: 14`, `if: always()`
- README 업데이트: E2E 실행 방법 문서화 (별도 SPEC인 sync 단계에서 처리)
- 검증: 로컬에서 `act` (선택) 또는 PR 푸시로 CI 실행 확인

**산출물**:
- `.github/workflows/ci.yml` (수정)

---

## 3. 기술 스택 및 의존성 (Technology & Dependencies)

### 3.1 신규 의존성

| 패키지              | 버전     | 용도                      |
|---------------------|----------|---------------------------|
| `@playwright/test`  | ^1.48.0  | E2E 테스트 러너 + 브라우저 자동화 |

설치 명령: `pnpm add -D @playwright/test --filter=@iroum-cms/public`

### 3.2 활용 도구

- **pnpm workspace**: `--filter=@iroum-cms/public` 스코프 격리
- **webServer (Playwright 내장)**: 개발 서버 자동 시작 (`pnpm run dev`)
- **Docker Compose**: 로컬 백엔드 기동 (또는 `./gradlew bootRun`)
- **GitHub Actions services**: CI에서 백엔드 서비스 컨테이너 제공

### 3.3 환경 변수

| 변수             | 로컬 기본값            | CI 기본값              | 용도                       |
|------------------|------------------------|------------------------|----------------------------|
| `BASE_URL`       | `http://localhost:5174` | `http://localhost:5174` | Playwright baseURL          |
| `API_BASE`       | `http://localhost:8080` | `http://localhost:8080` | 백엔드 (service container) |
| `CI`             | `false`                | `true`                 | retries/workers/forbidOnly  |

---

## 4. TDD 워크플로우 상세

본 SPEC은 `quality.yaml: development_mode: tdd`에 따라 RED → GREEN → REFACTOR 사이클을 적용한다.

### 4.1 RED 단계

각 spec 파일에 인수 시나리오를 먼저 작성하고 `pnpm run test:e2e {파일명}` 실행 시 **실패**를 확인한다.
- 실패 사유는 보통 셀렉터 누락 또는 셀렉터 명명 불일치
- `@MX:TODO` 태그를 spec 파일 상단에 부착하여 미완성 시나리오 표시

### 4.2 GREEN 단계

기존 SPA(`HomeView.vue`, `NoticeListView.vue`, `FaqView.vue` 등)는 이미 구현되어 있으므로:
- 실제 셀렉터(`data-testid`, ARIA 속성)를 spec과 일치시킴
- SPA 측 수정이 필요한 경우 최소한의 셀렉터 추가만 수행 (기능 변경 금지)
- 테스트 통과 확인

### 4.3 REFACTOR 단계

- 공통 헬퍼 추출: `gotoAndWaitForLoad`, `expectUrlMatches`, `fillFormFields`
- 셀렉터 상수화: `tests/e2e/selectors/*.ts`로 분리 검토
- @MX:TODO → @MX:NOTE 전환

---

## 5. 위험 관리 (Risk Management)

| 위험                            | 영향            | 완화 전략                                                          |
|---------------------------------|-----------------|---------------------------------------------------------------------|
| 백엔드 API 응답 불안정          | flaky test 증가 | CI에서 서비스 컨테이너 헬스체크 (`options: --health-cmd ...`)        |
| 시드 데이터 변동                | 셀렉터 깨짐     | 텍스트 매칭 대신 구조적 셀렉터(role/aria-label) 사용                |
| ECharts Canvas 렌더링 지연      | 통계 페이지 timeout | 통계 페이지는 본 SPEC 범위 외 (Phase 1 P0 아님)                |
| LoginView 미구현                | 인증 시나리오 한계 | localStorage 직접 주입으로 시뮬레이션, 폼 실제 검증은 Phase 1 이후 |
| 503 유지보수 모드 의도치 않은 활성화 | 모든 테스트 실패 | CI 환경 변수로 유지보수 모드 비활성화 강제                       |
| Playwright 버전 업데이트 호환성 | 회귀 가능성     | pinning(`^1.48.0` 캐럿), Renovate/Dependabot 검토 후 업데이트     |

---

## 6. MX Tag 계획 (MX Tag Targets)

본 SPEC에서 생성/수정될 파일의 MX 태그 부착 계획:

| 파일                                          | MX 태그       | 사유                                                       |
|-----------------------------------------------|---------------|------------------------------------------------------------|
| `frontend/public/playwright.config.ts`        | `@MX:NOTE`    | E2E 설정 진입점. 향후 브라우저 추가 시 수정 지점          |
| `frontend/public/tests/e2e/fixtures/auth.ts`  | `@MX:ANCHOR`  | 모든 e2e spec에서 import. fan_in >= 3 예상                |
| `frontend/public/tests/e2e/a11y.spec.ts`      | `@MX:NOTE`    | KWCAG 2.2 AA 출시 게이트. 정책 변경 시 영향               |
| `.github/workflows/ci.yml` (frontend-e2e job) | `@MX:NOTE`    | CI 통합 포인트. 백엔드 컨테이너 의존                      |
| RED 단계 미완성 시나리오                      | `@MX:TODO`    | GREEN 단계에서 제거                                       |

태그 부착 시점: 각 Task의 GREEN 또는 REFACTOR 단계 종료 시.

---

## 7. 검증 및 완료 기준 (Verification & Completion)

### 7.1 단위 검증 (Per-Task)

- Task 1: `pnpm run test:e2e --list` 명령이 0 spec 출력해도 성공 (설정 파싱 OK)
- Task 2: `auth.ts` import 후 `loginAs(page)` 호출 시 page.localStorage에 `public.accessToken` 존재
- Task 3 ~ 9: 각 spec 파일이 Chromium에서 0 failed
- Task 10: PR 머지 후 GitHub Actions `frontend-e2e` job 그린

### 7.2 통합 검증 (전체)

- 9개 spec 파일 통합 실행 (`pnpm run test:e2e`)에서 0 failed
- 실행 시간 합리적 범위 내 (Phase 1 기준 측정 후 baseline 수립)
- `playwright-report/` HTML 리포트 생성 확인
- CI `frontend-e2e` job artifact 다운로드 후 리포트 열람 가능

### 7.3 TRUST 5 게이트

- **Tested**: 9개 spec, 핵심 사용자 여정 100% 커버
- **Readable**: 헬퍼/셀렉터 명명 명확, 한국어 주석
- **Unified**: 모든 spec 동일한 구조(beforeEach → 시나리오 → afterEach)
- **Secured**: REQ-E2E-014 준수 (백엔드 쓰기 사이드이펙트 0건)
- **Trackable**: 각 spec 상단에 SPEC-CMS-PUBLIC-E2E-001 + REQ-ID 주석

---

## 8. 후속 작업 (Follow-up)

본 SPEC 완료 후 sync 단계에서 처리:

- `frontend/public/README.md`에 E2E 실행 방법 섹션 추가
- `CHANGELOG.md`에 E2E 도입 항목 추가
- `.moai/project/tech.md`에서 Playwright "계획됨" → "도입 완료" 상태 갱신
- 별도 SPEC 후보:
  - SPEC-CMS-ADMIN-E2E-001: 관리자 SPA E2E
  - SPEC-CMS-PERF-001: Lighthouse/Web Vitals
  - SPEC-CMS-VR-001: 시각 회귀 테스트
  - SPEC-CMS-MOBILE-E2E-001: 모바일 뷰포트
