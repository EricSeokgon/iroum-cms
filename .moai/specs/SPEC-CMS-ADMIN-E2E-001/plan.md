# Implementation Plan: SPEC-CMS-ADMIN-E2E-001

**Version**: 0.1.0
**Status**: Draft
**Created**: 2026-05-14
**Updated**: 2026-05-14
**Mode**: TDD (RED → GREEN → REFACTOR)

본 문서는 SPEC-CMS-ADMIN-E2E-001의 구현 계획을 정의한다. 10개 TDD 태스크로 분해되며, 각 태스크는 파일 소유권과 의존성 그래프를 명시한다.

---

## 1. 마일스톤 (Priority-Based)

| Milestone | Priority | 포함 태스크 | 산출물 |
|-----------|----------|-------------|--------|
| M1: Infrastructure | High | Task 1, 2 | playwright.config.ts + fixtures/auth.ts |
| M2: Auth E2E | High | Task 3, 4 | login.spec.ts, auth-guard.spec.ts |
| M3: Core Views E2E | High | Task 5, 6, 7, 8 | dashboard / users / roles / notices spec |
| M4: Edge Cases | Medium | Task 9 | password-change, logout, error-pages |
| M5: A11y + CI | High | Task 10 | a11y.spec.ts + ci.yml job |

---

## 2. 태스크 분해 (10 Tasks)

### Task 1: Playwright 인프라 셋업 (M1)

**Owner**: Implementer
**Files**:
- `frontend/admin/playwright.config.ts` (CREATE)
- `frontend/admin/package.json` (MODIFY — devDependency + scripts)

**TDD Cycle**:
- RED: `pnpm exec playwright --version` 실행 → 미설치 확인
- GREEN: `@playwright/test@^1.48.0`, `@axe-core/playwright@^4.10.0` devDependency 추가 + `playwright.config.ts` 생성
- REFACTOR: SPEC-CMS-PUBLIC-E2E-001의 config 구조와 동형 유지하되 baseURL/port만 변경

**핵심 설정**:
- `testDir: './tests/e2e'`
- `baseURL: 'http://localhost:5173'` (Admin SPA dev port)
- `webServer.command: 'pnpm run dev'`
- `projects: [{ name: 'chromium', use: devices['Desktop Chrome'] }]`
- CI: `retries: 2`, `workers: 1`, `forbidOnly: true`
- `reporter: [['html'], ['list']]`

**MX Tags**: `@MX:NOTE` — CI retries/workers 결정 근거 (deterministic execution)

**REQ 매핑**: REQ-ADMIN-E2E-INFRA-001, REQ-ADMIN-E2E-INFRA-002

**완료 조건**:
- [ ] `pnpm exec playwright --version` 정상 출력
- [ ] `pnpm run test:e2e --list` 실행 시 0 tests found (테스트 파일 부재) 메시지

---

### Task 2: Mock JWT 인증 헬퍼 구현 (M1)

**Owner**: Implementer
**Files**:
- `frontend/admin/tests/e2e/fixtures/auth.ts` (CREATE)

**TDD Cycle**:
- RED: `tests/e2e/fixtures/auth.spec.ts` (헬퍼 자체 검증용 임시 스펙)에서 `loginAsSuperAdmin(page)` 호출 후 `/dashboard` 도달 검증 → 헬퍼 미구현으로 실패
- GREEN: `mockAuthApi(page)`, `loginAsSuperAdmin(page)`, `clearAuth(page)` 헬퍼 작성
- REFACTOR: 임시 스펙 삭제 후 `login.spec.ts`에서 헬퍼 재사용 검증

**핵심 API**:
```typescript
export async function mockAuthApi(page: Page): Promise<void> { /* page.route('/api/v1/auth/login', ...) */ }
export async function loginAsSuperAdmin(page: Page): Promise<void> { /* mock + form fill + submit + wait */ }
export async function clearAuth(page: Page): Promise<void> { /* clearCookies + clear localStorage */ }
```

**Mock JWT 구조** (decodeJwt 호환):
- Header: `eyJhbGciOiJIUzI1NiJ9` (base64url of `{"alg":"HS256"}`)
- Payload: base64url of `{ sub: 'admin', uid: 1, roles: ['SUPER_ADMIN'], exp: 9999999999, iat: 1000000000 }`
- Signature: `mock-signature` (서명 검증 안 함)

**MX Tags**:
- `@MX:ANCHOR` — 모든 인증 E2E 테스트의 진입점 (fan_in ≥ 7)
- `@MX:NOTE` — Pinia 런타임 메모리 인증 특성으로 인한 mock JWT 구조 요구사항 설명

**REQ 매핑**: REQ-ADMIN-E2E-AUTH-001, REQ-ADMIN-E2E-AUTH-002

**완료 조건**:
- [ ] `loginAsSuperAdmin(page)` 호출 후 `/dashboard` 도달
- [ ] `useAuthStore().user`가 `{ id: 1, username: 'admin', roleCodes: ['SUPER_ADMIN'] }`로 채워짐 (브라우저 console.log 확인)
- [ ] `clearAuth(page)` 후 `isAuthenticated === false`

---

### Task 3: 로그인 E2E (M2)

**Owner**: Implementer
**Files**:
- `frontend/admin/tests/e2e/login.spec.ts` (CREATE)

**TDD Cycle**:
- RED: 로그인 성공/실패 시나리오 작성 → 실행 → 통과 검증 (LoginView 기존 구현됨)
- GREEN: 모든 시나리오 통과 확인
- REFACTOR: 공통 setup (mockAuthApi 호출) → `beforeEach`로 추출

**테스트 시나리오**:
1. 성공 플로우: form fill → mock 200 → `/dashboard` 리다이렉트
2. 실패 플로우: mock 401 → `data-testid="login-error"` visible
3. 빈 폼 제출 차단: `aria-invalid="true"` 확인

**REQ 매핑**: REQ-ADMIN-E2E-LOGIN-001, REQ-ADMIN-E2E-LOGIN-002

**의존성**: Task 1, Task 2

**완료 조건**:
- [ ] 3개 테스트 케이스 통과
- [ ] `id="username"`, `id="password"` 셀렉터 사용 (CSS 클래스 금지)

---

### Task 4: 인증 가드 E2E (M2)

**Owner**: Implementer
**Files**:
- `frontend/admin/tests/e2e/auth-guard.spec.ts` (CREATE)

**TDD Cycle**:
- RED: 미인증 상태에서 `/dashboard` 직접 방문 → `/login?redirect=%2Fdashboard` 리다이렉트 검증
- GREEN: router.beforeEach 로직이 기존 구현되어 있으므로 통과 확인
- REFACTOR: 보호 라우트 배열을 fixture로 추출

**테스트 시나리오**:
1. `/dashboard` 직접 방문 → `/login?redirect=%2Fdashboard`
2. `/users` 직접 방문 → `/login?redirect=%2Fusers`
3. `/roles?page=2` 방문 → `/login?redirect=%2Froles%3Fpage%3D2` (쿼리 보존)
4. 로그인 페이지 자체는 미인증으로도 접근 가능

**REQ 매핑**: REQ-ADMIN-E2E-GUARD-001

**의존성**: Task 1

**완료 조건**:
- [ ] 4개 테스트 케이스 통과
- [ ] 원본 경로가 URL-encoded redirect query로 보존됨

---

### Task 5: 대시보드 E2E + data-testid 추가 (M3)

**Owner**: Implementer
**Files**:
- `frontend/admin/src/layouts/AdminLayout.vue` (MODIFY — `data-testid="admin-layout"` 추가)
- `frontend/admin/src/views/dashboard/DashboardMainView.vue` (MODIFY — `data-testid="dashboard-main"` 추가)
- `frontend/admin/tests/e2e/dashboard.spec.ts` (CREATE)

**TDD Cycle**:
- RED: `dashboard.spec.ts`에서 `data-testid="dashboard-main"` 셀렉터 사용 → 실행 → 셀렉터 부재로 실패
- GREEN: DashboardMainView.vue, AdminLayout.vue에 data-testid 비파괴적 추가 → 통과
- REFACTOR: 공통 mockDashboardApi 헬퍼 추출

**테스트 시나리오**:
1. 로그인 후 자동으로 `/dashboard` 도달
2. `data-testid="admin-layout"` + `data-testid="dashboard-main"` visible
3. `<nav role="navigation">` 내부에 users / roles / notices 링크 존재

**API 모킹**:
- `GET /api/v1/dashboard/summary` → 200 + 빈 데이터로 충분 (P0)

**REQ 매핑**: REQ-ADMIN-E2E-DASHBOARD-001

**의존성**: Task 2

**완료 조건**:
- [ ] data-testid 2개 추가 (로직/스타일 변경 없음)
- [ ] 3개 테스트 케이스 통과

---

### Task 6: 사용자 목록 E2E + data-testid 추가 (M3)

**Owner**: Implementer
**Files**:
- `frontend/admin/src/views/users/UserListView.vue` (MODIFY — `data-testid="user-list-table"` 추가)
- `frontend/admin/tests/e2e/users.spec.ts` (CREATE)

**TDD Cycle**:
- RED: users.spec.ts → 셀렉터 부재로 실패
- GREEN: UserListView.vue에 data-testid 추가
- REFACTOR: API mock fixture 추출

**테스트 시나리오**:
1. 로그인 후 `/users` 방문
2. mock `GET /api/v1/users` 응답: `{ items: [{id:1,username:'admin'},{id:2,username:'tester'},{id:3,username:'auditor'}], total: 3 }`
3. `data-testid="user-list-table"` visible + 3개 행 노출

**REQ 매핑**: REQ-ADMIN-E2E-USERS-001

**의존성**: Task 2

**완료 조건**:
- [ ] data-testid 1개 추가
- [ ] 2개 테스트 케이스 통과

---

### Task 7: 역할 매트릭스 E2E + data-testid 추가 (M3)

**Owner**: Implementer
**Files**:
- `frontend/admin/src/views/roles/RoleMatrixView.vue` (MODIFY — `data-testid="role-matrix"` 추가)
- `frontend/admin/tests/e2e/roles.spec.ts` (CREATE)

**TDD Cycle**:
- RED: roles.spec.ts → 셀렉터 부재로 실패
- GREEN: RoleMatrixView.vue에 data-testid 추가
- REFACTOR: 권한 매트릭스 mock 데이터 fixture화

**테스트 시나리오**:
1. 로그인 후 `/roles` 방문
2. mock `GET /api/v1/roles`, `GET /api/v1/permissions` 응답
3. `data-testid="role-matrix"` visible (role="grid" 또는 role="table")

**REQ 매핑**: REQ-ADMIN-E2E-ROLES-001

**의존성**: Task 2

**완료 조건**:
- [ ] data-testid 1개 추가
- [ ] 2개 테스트 케이스 통과

---

### Task 8: 공지 목록 E2E + data-testid 추가 (M3)

**Owner**: Implementer
**Files**:
- `frontend/admin/src/views/notices/NoticeListView.vue` (MODIFY — `data-testid="notice-list-table"` 추가)
- `frontend/admin/tests/e2e/notices.spec.ts` (CREATE)

**TDD Cycle**:
- RED: notices.spec.ts → 셀렉터 부재로 실패
- GREEN: NoticeListView.vue에 data-testid 추가
- REFACTOR: 공지 mock 데이터 추출

**테스트 시나리오**:
1. 로그인 후 `/notices` 방문
2. mock `GET /api/v1/notices` 응답 (1개 이상 공지)
3. `data-testid="notice-list-table"` visible + 1개 이상 행 노출

**REQ 매핑**: REQ-ADMIN-E2E-NOTICES-001

**의존성**: Task 2

**완료 조건**:
- [ ] data-testid 1개 추가
- [ ] 1개 테스트 케이스 통과

---

### Task 9: Edge Cases — 비밀번호 변경 / 로그아웃 / 404 (M4)

**Owner**: Implementer
**Files**:
- `frontend/admin/src/views/error/NotFoundView.vue` (MODIFY — `data-testid="not-found"` 추가)
- `frontend/admin/src/components/layout/AdminHeader.vue` (또는 동등) (MODIFY — `data-testid="btn-logout"` 추가)
- `frontend/admin/tests/e2e/password-change.spec.ts` (CREATE)
- `frontend/admin/tests/e2e/logout.spec.ts` (CREATE)
- `frontend/admin/tests/e2e/error-pages.spec.ts` (CREATE)

**TDD Cycle**:
- RED: 3개 spec 작성 → not-found, btn-logout 셀렉터 부재로 부분 실패
- GREEN: 2개 view에 data-testid 추가 → 통과
- REFACTOR: 공통 mock 셋업 추출

**테스트 시나리오**:

**password-change.spec.ts** (기존 data-testid 활용):
1. 새 비밀번호와 확인 비밀번호 불일치 → submit 차단 + `data-testid="error-alert"` visible
2. 동일 비밀번호 입력 → submit 정상 (mock 200) → `data-testid="success-alert"` visible
3. 네트워크 요청 발생 여부 검증 (불일치 시 0건, 일치 시 1건)

**logout.spec.ts**:
1. 로그인 후 `data-testid="btn-logout"` 클릭
2. `/login` 도달 (redirect query 없음)
3. 다시 `/dashboard` 방문 시도 → `/login?redirect=%2Fdashboard` (가드 동작)

**error-pages.spec.ts**:
1. `/no-such-path` 방문 → `data-testid="not-found"` 또는 "404"/"찾을 수 없" 텍스트
2. 미인증 상태에서도 NotFound 페이지 자체는 접근 가능 (또는 라우터 동작에 따른 검증)

**REQ 매핑**: REQ-ADMIN-E2E-PWCHANGE-001, REQ-ADMIN-E2E-LOGOUT-001, REQ-ADMIN-E2E-ERROR-001

**의존성**: Task 2

**완료 조건**:
- [ ] data-testid 2개 추가 (not-found, btn-logout)
- [ ] 3개 spec 파일 총 7개 테스트 케이스 통과

---

### Task 10: KWCAG 2.2 AA + CI 통합 (M5)

**Owner**: Implementer
**Files**:
- `frontend/admin/tests/e2e/a11y.spec.ts` (CREATE)
- `.github/workflows/ci.yml` (MODIFY — `frontend-e2e-admin` job 추가)

**TDD Cycle**:
- RED: a11y.spec.ts 작성 + CI 실행 → axe-core 위반 발견 시 실패
- GREEN: 발견된 critical/serious 위반에 대해 해당 view 수정 (data-testid와 함께 ARIA 속성 보강 가능)
- REFACTOR: axe-core 설정을 fixture로 추출

**테스트 시나리오**:

**a11y.spec.ts** (`@axe-core/playwright`):
1. `/login` 페이지 axe scan (WCAG 2.2 AA tags, color-contrast 제외) → critical/serious 0건
2. `/dashboard` 페이지 axe scan (로그인 후) → critical/serious 0건
3. moderate/minor 위반은 console.log로만 출력 (fail 트리거 안 함)

**CI Job 구조** (`frontend-e2e-admin`):
- `needs: [frontend-test]`
- Ubuntu 22.04 + Node 22 + pnpm
- `pnpm install --frozen-lockfile`
- `pnpm --filter @iroum/admin exec playwright install chromium --with-deps`
- `pnpm --filter @iroum/admin run test:e2e`
- `if: always()` 단계로 `frontend/admin/playwright-report/` artifact 업로드 (retention 14)
- `continue-on-error: false`

**MX Tags**:
- `@MX:NOTE` (a11y.spec.ts) — color-contrast 제외 결정 근거
- `@MX:NOTE` (ci.yml) — backend service container 미사용 결정 근거 (page.route() 모킹)

**REQ 매핑**: REQ-ADMIN-E2E-A11Y-001, REQ-ADMIN-E2E-A11Y-002, REQ-ADMIN-E2E-CI-001

**의존성**: Task 2, Task 5

**완료 조건**:
- [ ] axe-core 위반 0건 (critical/serious, color-contrast 제외)
- [ ] CI에서 frontend-e2e-admin job 통과
- [ ] playwright-report artifact 업로드 확인

---

## 3. 의존성 그래프 (Dependency Graph)

```
Task 1 (Infrastructure)
  └─> Task 2 (Auth Helper)
        ├─> Task 3 (Login E2E)
        ├─> Task 4 (Auth Guard E2E) ←── Task 1 (직접 의존)
        ├─> Task 5 (Dashboard E2E)
        │     └─> Task 10 (A11y + CI)
        ├─> Task 6 (Users E2E)
        ├─> Task 7 (Roles E2E)
        ├─> Task 8 (Notices E2E)
        └─> Task 9 (Edge Cases)
```

**병렬 실행 가능**:
- Task 3, 4, 5, 6, 7, 8, 9 — Task 2 완료 후 독립적
- Task 10은 Task 5 완료 후

---

## 4. 파일 소유권 매트릭스 (File Ownership)

| 파일 | Owner Task | 변경 유형 |
|------|-----------|----------|
| `playwright.config.ts` | Task 1 | CREATE |
| `package.json` | Task 1 | MODIFY (devDep + scripts) |
| `tests/e2e/fixtures/auth.ts` | Task 2 | CREATE |
| `tests/e2e/login.spec.ts` | Task 3 | CREATE |
| `tests/e2e/auth-guard.spec.ts` | Task 4 | CREATE |
| `tests/e2e/dashboard.spec.ts` | Task 5 | CREATE |
| `src/layouts/AdminLayout.vue` | Task 5 | MODIFY (data-testid only) |
| `src/views/dashboard/DashboardMainView.vue` | Task 5 | MODIFY (data-testid only) |
| `tests/e2e/users.spec.ts` | Task 6 | CREATE |
| `src/views/users/UserListView.vue` | Task 6 | MODIFY (data-testid only) |
| `tests/e2e/roles.spec.ts` | Task 7 | CREATE |
| `src/views/roles/RoleMatrixView.vue` | Task 7 | MODIFY (data-testid only) |
| `tests/e2e/notices.spec.ts` | Task 8 | CREATE |
| `src/views/notices/NoticeListView.vue` | Task 8 | MODIFY (data-testid only) |
| `tests/e2e/password-change.spec.ts` | Task 9 | CREATE |
| `tests/e2e/logout.spec.ts` | Task 9 | CREATE |
| `tests/e2e/error-pages.spec.ts` | Task 9 | CREATE |
| `src/views/error/NotFoundView.vue` | Task 9 | MODIFY (data-testid only) |
| `src/components/layout/AdminHeader.vue` (or equivalent) | Task 9 | MODIFY (data-testid only) |
| `tests/e2e/a11y.spec.ts` | Task 10 | CREATE |
| `.github/workflows/ci.yml` | Task 10 | MODIFY (job 추가) |

---

## 5. 기술적 위험 및 완화 방안 (Risks & Mitigations)

| 위험 | 영향 | 완화 방안 |
|------|------|----------|
| Mock JWT가 `decodeJwt()` 호출 실패 → `user.value = null` → 일부 뷰 미렌더 | High | Task 2에서 base64url로 정확한 JWT 페이로드 구성. `pnpm test:e2e --debug`로 store 상태 확인 |
| Pinia 상태가 페이지 새로고침으로 소실 → 일부 테스트 비결정성 | Medium | 매 테스트 `beforeEach`에서 mock + 로그인 재실행 (REQ-ADMIN-E2E-AUTH-002) |
| AdminHeader.vue 로그아웃 버튼 위치 불확실 | Low | Task 9에서 `grep -rn "logout"` 으로 정확한 위치 식별 후 data-testid 추가 |
| Element Plus 내부 DOM이 ARIA 속성 자동 부여 안 함 → axe-core 위반 | Medium | a11y.spec.ts에서 위반 식별 후 해당 view에 명시적 ARIA 속성 보강 |
| CI Vite dev server 기동 시간 초과 | Medium | Playwright config의 `webServer.timeout: 120_000` 설정 |
| 4개 시스템 권한 라우트(`SYSTEM:*`)가 SUPER_ADMIN으로도 접근 차단 가능성 | Low | Phase 1 범위에서 제외 (research.md §11 P1로 분류) |
| `notice-list-table` 컴포넌트가 동적 lazy-load일 경우 | Low | `await page.waitForResponse(/\/api\/v1\/notices/)` 패턴으로 안정화 |

---

## 6. 검증 체크리스트 (Verification Checklist)

태스크 완료 시 다음 검증을 수행:

- [ ] 모든 spec 파일이 `data-testid` 또는 ARIA 셀렉터만 사용 (CSS 클래스 0건)
- [ ] `grep -rn "page.locator('.\|page.locator(\"\."` 결과 0건
- [ ] frontend/admin/src/ 변경 사항이 data-testid 추가에 국한됨 (`git diff` 검토)
- [ ] mock JWT 페이로드가 `decodeJwt()`로 디코딩 가능 (브라우저 console 확인)
- [ ] Playwright HTML 리포트에서 모든 테스트 통과 (0 failed)
- [ ] axe-core 위반 critical/serious 0건
- [ ] CI `frontend-e2e-admin` job 통과 + artifact 업로드 확인
- [ ] `pnpm --filter @iroum/admin run test` (기존 Vitest 53개) 회귀 없음
- [ ] `.moai/config/sections/quality.yaml` TRUST 5 게이트 통과

---

## 7. 참고 (References)

- `spec.md` (동일 디렉토리) — EARS 요구사항 정의
- `research.md` (동일 디렉토리) — 코드베이스 분석 및 위험 평가
- `.moai/specs/SPEC-CMS-PUBLIC-E2E-001/plan.md` — 자매 SPEC 구현 계획 (패턴 참조)
