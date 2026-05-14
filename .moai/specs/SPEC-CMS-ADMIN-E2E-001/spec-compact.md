# SPEC-CMS-ADMIN-E2E-001 — Compact

**Token-optimized summary for run phase.** Full text in `spec.md`, `plan.md`, `acceptance.md`.

---

## Goal

Admin SPA (`frontend/admin/`)에 Playwright 1.x E2E 테스트 도입. 11개 spec 파일, 12개 REQ, 19개 시나리오. Chromium only, port 5173, page.route() 모킹 기반 (backend 의존 0).

## Key Decision: Auth Mocking Strategy

Public SPA와 결정적 차이 → **Pinia 런타임 메모리 인증**:
- 토큰이 `useAuthStore().accessToken` ref에만 저장 → localStorage 주입 불가 → page reload 시 소실
- 전략: `page.route('/api/v1/auth/login')`로 mock JWT 반환 → 폼 fill → submit → /dashboard 도달
- Mock JWT는 `decodeJwt()`로 디코딩 가능해야 함:
  ```
  header:  eyJhbGciOiJIUzI1NiJ9
  payload: base64url({sub:'admin',uid:1,roles:['SUPER_ADMIN'],exp:9999999999,iat:1000000000})
  signature: mock-signature
  ```
- 매 테스트 `beforeEach`: cookies + storage clear + (필요 시) mock re-login

## Key Decision: data-testid Gap Resolution

76개 view 중 5개 파일만 data-testid 보유 (총 19개). 7개 핵심 view에 data-testid **비파괴적 추가**:

| View | data-testid |
|------|------------|
| AdminLayout.vue | `admin-layout` |
| DashboardMainView.vue | `dashboard-main` |
| UserListView.vue | `user-list-table` |
| RoleMatrixView.vue | `role-matrix` |
| NoticeListView.vue | `notice-list-table` |
| NotFoundView.vue | `not-found` |
| AdminHeader.vue (또는 동등) | `btn-logout` |

**규칙**: 속성 추가만 허용. 로직/스타일/props 변경 일절 금지.

## 12 Requirements (Compact)

| REQ-ID | Type | Summary |
|--------|------|---------|
| INFRA-001 | UBI | @playwright/test ^1.48.0 + @axe-core/playwright ^4.10.0 설치 + playwright.config.ts |
| INFRA-002 | UBI | data-testid/ARIA/semantic selector only. CSS 클래스 금지 |
| AUTH-001 | UBI | fixtures/auth.ts: mockAuthApi() + loginAsSuperAdmin() + 유효 mock JWT |
| AUTH-002 | STATE | 매 테스트 beforeEach에서 cookies/storage clear + mock re-login |
| LOGIN-001 | EVT | 로그인 성공: form fill → mock 200 → /dashboard |
| LOGIN-002 | EVT | 로그인 실패: mock 401 → data-testid="login-error" visible |
| GUARD-001 | STATE | 미인증 보호라우트 → /login?redirect={URLencoded(fullPath)} |
| DASHBOARD-001 | UBI | /dashboard: admin-layout + dashboard-main + nav role="navigation" |
| USERS-001 | UBI | /users: user-list-table + 3개 행 (mock GET /api/v1/users) |
| ROLES-001 | UBI | /roles: role-matrix (mock GET /api/v1/roles + /permissions) |
| NOTICES-001 | UBI | /notices: notice-list-table + 1개 이상 행 (mock GET /api/v1/notices) |
| PWCHANGE-001 | EVT | 비밀번호 불일치 → submit 차단 + error-alert + 네트워크 0건 |
| LOGOUT-001 | EVT | btn-logout 클릭 → store clear → /login (no redirect query) |
| ERROR-001 | UBI | /:pathMatch(.*) → data-testid="not-found" 또는 "404" 텍스트 |
| A11Y-001 | UBI/AA | /login axe-core critical/serious 0건 (color-contrast 제외) |
| A11Y-002 | UBI/AA | /dashboard axe-core critical/serious 0건 (color-contrast 제외) |
| SAFETY-001 | UNW | 백엔드 데이터 쓰기 사이드이펙트 금지 (모든 호출 mock) |
| CI-001 | UBI | frontend-e2e-admin job 추가 (needs: [frontend-test], no backend container) |

## 10 Tasks (TDD RED→GREEN→REFACTOR)

| # | Task | Files Created/Modified | REQ |
|---|------|------------------------|-----|
| 1 | Infra | playwright.config.ts, package.json | INFRA-001/002 |
| 2 | Auth helper | tests/e2e/fixtures/auth.ts (`@MX:ANCHOR` + `@MX:NOTE`) | AUTH-001/002 |
| 3 | Login | tests/e2e/login.spec.ts | LOGIN-001/002 |
| 4 | Auth guard | tests/e2e/auth-guard.spec.ts | GUARD-001 |
| 5 | Dashboard | dashboard.spec.ts + AdminLayout.vue + DashboardMainView.vue | DASHBOARD-001 |
| 6 | Users | users.spec.ts + UserListView.vue | USERS-001 |
| 7 | Roles | roles.spec.ts + RoleMatrixView.vue | ROLES-001 |
| 8 | Notices | notices.spec.ts + NoticeListView.vue | NOTICES-001 |
| 9 | Edge | password-change.spec.ts + logout.spec.ts + error-pages.spec.ts + NotFoundView.vue + AdminHeader.vue | PWCHANGE-001/LOGOUT-001/ERROR-001 |
| 10 | A11y+CI | a11y.spec.ts + .github/workflows/ci.yml | A11Y-001/002, CI-001 |

**Dependency**: Task 1 → 2 → {3, 4, 5, 6, 7, 8, 9} → 10. Tasks 3-9 are parallelizable after Task 2.

## File List

### Create (12 files)
- `frontend/admin/playwright.config.ts`
- `frontend/admin/tests/e2e/fixtures/auth.ts`
- `frontend/admin/tests/e2e/login.spec.ts`
- `frontend/admin/tests/e2e/auth-guard.spec.ts`
- `frontend/admin/tests/e2e/dashboard.spec.ts`
- `frontend/admin/tests/e2e/users.spec.ts`
- `frontend/admin/tests/e2e/roles.spec.ts`
- `frontend/admin/tests/e2e/notices.spec.ts`
- `frontend/admin/tests/e2e/password-change.spec.ts`
- `frontend/admin/tests/e2e/logout.spec.ts`
- `frontend/admin/tests/e2e/error-pages.spec.ts`
- `frontend/admin/tests/e2e/a11y.spec.ts`

### Modify — data-testid additions only (8 files)
- `frontend/admin/package.json` (devDep + scripts)
- `frontend/admin/src/layouts/AdminLayout.vue`
- `frontend/admin/src/views/dashboard/DashboardMainView.vue`
- `frontend/admin/src/views/users/UserListView.vue`
- `frontend/admin/src/views/roles/RoleMatrixView.vue`
- `frontend/admin/src/views/notices/NoticeListView.vue`
- `frontend/admin/src/views/error/NotFoundView.vue`
- `frontend/admin/src/components/layout/AdminHeader.vue` (or equivalent — verify with `grep -rn "logout"`)
- `.github/workflows/ci.yml` (frontend-e2e-admin job 추가)

## Playwright Config Essentials

```typescript
testDir: './tests/e2e'
baseURL: 'http://localhost:5173'
webServer: { command: 'pnpm run dev', url: 'http://localhost:5173', reuseExistingServer: !process.env.CI, timeout: 120_000 }
projects: [{ name: 'chromium', use: devices['Desktop Chrome'] }]
retries: process.env.CI ? 2 : 0
workers: process.env.CI ? 1 : undefined
forbidOnly: !!process.env.CI
reporter: [['html'], ['list']]
```

## Mock JWT Snippet (canonical reference)

```typescript
// fixtures/auth.ts
const mockPayload = btoa(JSON.stringify({
  sub: 'admin', uid: 1, roles: ['SUPER_ADMIN'],
  exp: 9999999999, iat: 1000000000
})).replace(/=+$/, '').replace(/\+/g, '-').replace(/\//g, '_')
const mockToken = `eyJhbGciOiJIUzI1NiJ9.${mockPayload}.mock-signature`

export async function mockAuthApi(page: Page): Promise<void> {
  await page.route('**/api/v1/auth/login', async route => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ accessToken: mockToken, expiresInSeconds: 3600 }),
    })
  })
}
```

## Existing data-testid Inventory (DO NOT recreate)

- LoginView: `login-notice`, `login-error`
- ForgotPasswordView: `global-error`, `expiry-countdown`, `otp-input`, `attempts-left`, `resend-button`
- PasswordChangeView: `success-alert`, `error-alert`, `input-current`, `input-new`, `input-confirm`, `btn-submit`, `btn-cancel`
- HealthView: `health-status`, `health-service`, `health-version`
- NotificationSettingsView: `switch-qna-answer-email`

## Login Form Selectors (existing — DO NOT add)

- `#username` (Element Plus `el-input` with `id="username"`)
- `#password` (Element Plus `el-input` with `id="password"`)
- `button[type="submit"]` or `button[native-type="submit"]`

## Constraints (HARD)

- **No new backend APIs**
- **No logic changes in frontend/admin/src/** — only `data-testid` attribute additions
- **All API calls mocked via `page.route()`** — `frontend-e2e-admin` CI job runs without backend container
- **CSS class selectors forbidden** — only data-testid / ARIA / semantic HTML
- **Chromium only** in Phase 1
- **color-contrast axe rule disabled** — visual regression covered by future SPEC
- **TDD mode** (per quality.yaml) — RED before GREEN, single test at a time
- **Pinia auth structure preserved** — no localStorage migration

## Non-Goals (Exclusions)

Public SPA E2E, new backend APIs, Firefox/Safari, perf/Lighthouse, visual regression, mobile viewport, i18n, forgot-password OTP, P1 routes (CRUD, /users/:id, RBAC restriction, /system/*, FAQ/QnA/Publications), backend integration E2E, forbidden(403)/server-error(500) (routes 부재).

## DoD Checklist (Critical Items)

- [ ] 19 scenarios pass on Chromium (0 failed)
- [ ] axe-core critical/serious 0 (login + dashboard)
- [ ] CSS-class selectors: 0 occurrences
- [ ] `git diff frontend/admin/src/` shows only `data-testid="..."` additions
- [ ] Backend not running → all tests still pass (mock validation)
- [ ] CI `frontend-e2e-admin` job green + report artifact uploaded (14-day retention)
- [ ] No regression in existing 53 Vitest files

## Quick Commands

```bash
# Local dev
pnpm --filter @iroum/admin run dev                    # start Vite on 5173
pnpm --filter @iroum/admin run test:e2e               # run all E2E
pnpm --filter @iroum/admin run test:e2e:ui            # Playwright UI mode
pnpm --filter @iroum/admin exec playwright test login # single spec
pnpm --filter @iroum/admin exec playwright show-report

# Selector validation
grep -rn "page.locator('\.\|page.locator(\"\." frontend/admin/tests/e2e/ # must return 0

# Diff scope validation
git diff --stat frontend/admin/src/ # must only show .vue files with data-testid additions
```
