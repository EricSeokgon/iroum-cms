# Acceptance Criteria: SPEC-CMS-ADMIN-E2E-001

**Version**: 0.1.0
**Status**: Draft
**Created**: 2026-05-14
**Updated**: 2026-05-14
**Format**: Given / When / Then

본 문서는 SPEC-CMS-ADMIN-E2E-001의 인수 시나리오를 정의한다. 각 시나리오는 해당 REQ-ID에 매핑되며, Playwright spec 파일의 테스트 케이스로 1:1 또는 1:N 변환된다.

---

## 1. 인증 시나리오 (Authentication)

### Scenario 1: 로그인 성공 — `/login` → `/dashboard`

**REQ 매핑**: REQ-ADMIN-E2E-LOGIN-001, REQ-ADMIN-E2E-AUTH-001, REQ-ADMIN-E2E-INFRA-002
**Spec 파일**: `tests/e2e/login.spec.ts`

**Given**:
- 사용자가 `/login`에 접속함
- `mockAuthApi(page)`가 `POST /api/v1/auth/login` 응답을 mock JWT (`{ sub: 'admin', uid: 1, roles: ['SUPER_ADMIN'], exp: 9999999999, iat: 1000000000 }` 페이로드)로 인터셉트하도록 설정됨
- `#username`, `#password` 입력 필드가 visible 상태이며 비어있음
- Pinia auth store의 `accessToken === null`

**When**:
- `#username` 셀렉터의 input에 `"admin"` 입력
- `#password` 셀렉터의 input에 `"any-password"` 입력
- submit 버튼(`button[type="submit"]`) 클릭 또는 Enter 키 입력

**Then**:
- Mock된 `POST /api/v1/auth/login` 요청이 1회 발생함
- 페이지 URL이 `/dashboard`로 변경됨
- `data-testid="admin-layout"`이 visible 상태로 렌더링됨
- `data-testid="dashboard-main"`이 visible 상태로 렌더링됨
- Pinia auth store의 `isAuthenticated === true`이며 `user.username === 'admin'`

---

### Scenario 2: 로그인 실패 — 401 INVALID_CREDENTIALS

**REQ 매핑**: REQ-ADMIN-E2E-LOGIN-002
**Spec 파일**: `tests/e2e/login.spec.ts`

**Given**:
- 사용자가 `/login`에 접속함
- `page.route('/api/v1/auth/login', route => route.fulfill({ status: 401, json: { error: 'INVALID_CREDENTIALS' } }))` 설정됨
- 로그인 폼이 visible 상태

**When**:
- `#username`에 `"wrong-user"`, `#password`에 `"wrong-pass"` 입력
- 폼 submit

**Then**:
- 페이지 URL이 여전히 `/login`이며 `/dashboard`로 이동하지 않음
- `data-testid="login-error"`가 visible 상태가 됨
- `data-testid="login-error"`의 `role="alert"` 속성이 존재하며 `aria-live="polite"` 영역에서 오류 메시지가 발표됨
- Pinia auth store의 `isAuthenticated === false`

---

### Scenario 3: 빈 필드로 로그인 시도 (Edge Case)

**REQ 매핑**: REQ-ADMIN-E2E-LOGIN-002, KWCAG 2.2 AA
**Spec 파일**: `tests/e2e/login.spec.ts`

**Given**:
- 사용자가 `/login`에 접속함
- `#username`, `#password` 입력 필드가 비어있음

**When**:
- submit 버튼 클릭 또는 Enter 키 입력

**Then**:
- `POST /api/v1/auth/login` 요청이 발생하지 않음 (네트워크 0건)
- 빈 필드에 `aria-invalid="true"` 속성이 설정됨 또는 HTML5 required 검증이 트리거됨
- 페이지 URL이 여전히 `/login`

---

## 2. 인증 가드 시나리오 (Auth Guard)

### Scenario 4: 미인증 상태에서 보호 라우트 접근 — `/dashboard` → `/login?redirect=`

**REQ 매핑**: REQ-ADMIN-E2E-GUARD-001
**Spec 파일**: `tests/e2e/auth-guard.spec.ts`

**Given**:
- 새로운 브라우저 컨텍스트 (`beforeEach`에서 `context.clearCookies()` + storage clear 완료)
- Pinia auth store는 초기 상태 (`accessToken === null`)
- 사용자가 `/dashboard`를 직접 URL로 입력하여 접속 시도

**When**:
- 라우터의 `beforeEach` 가드가 실행됨

**Then**:
- 페이지 URL이 `/login?redirect=%2Fdashboard` 로 리다이렉트됨
- 로그인 폼이 렌더링됨 (`#username`, `#password` 셀렉터 visible)
- `data-testid="login-notice"` 또는 동등한 안내 영역에 "로그인이 필요합니다" 류 메시지 표시 (있을 경우)

---

### Scenario 5: 쿼리 파라미터 보존 — `/roles?page=2` → `/login?redirect=...`

**REQ 매핑**: REQ-ADMIN-E2E-GUARD-001
**Spec 파일**: `tests/e2e/auth-guard.spec.ts`

**Given**:
- 미인증 상태
- 사용자가 `/roles?page=2`를 직접 방문

**When**:
- 라우터 가드 실행

**Then**:
- 페이지 URL이 `/login?redirect=%2Froles%3Fpage%3D2` 로 리다이렉트됨 (쿼리 문자열이 URL-encoded되어 redirect 값에 보존됨)

---

## 3. 핵심 사용자 여정 시나리오 (Core User Journeys)

### Scenario 6: 대시보드 렌더링 — `/dashboard`

**REQ 매핑**: REQ-ADMIN-E2E-DASHBOARD-001, REQ-ADMIN-E2E-AUTH-002
**Spec 파일**: `tests/e2e/dashboard.spec.ts`

**Given**:
- `loginAsSuperAdmin(page)` 헬퍼를 통해 mock 로그인 완료 (`/dashboard` 도달)
- `GET /api/v1/dashboard/summary` 응답이 mock으로 200 + 빈 데이터 반환

**When**:
- 페이지가 완전히 로드됨 (`page.waitForLoadState('networkidle')`)

**Then**:
- `data-testid="admin-layout"` visible
- `data-testid="dashboard-main"` visible
- `<nav role="navigation">` 컨테이너 내부에 사용자/역할/공지 메뉴 링크(`<a>` 또는 `role="link"`)가 최소 3개 존재
- `<main role="main">` 영역이 single landmark로 존재

---

### Scenario 7: 사용자 목록 조회 — `/users`

**REQ 매핑**: REQ-ADMIN-E2E-USERS-001
**Spec 파일**: `tests/e2e/users.spec.ts`

**Given**:
- `loginAsSuperAdmin(page)` 완료
- `GET /api/v1/users` 응답이 mock으로 다음 데이터 반환:
  ```json
  { "items": [
      {"id":1,"username":"admin","email":"admin@example.com"},
      {"id":2,"username":"tester","email":"tester@example.com"},
      {"id":3,"username":"auditor","email":"auditor@example.com"}
    ],
    "total": 3
  }
  ```

**When**:
- 사용자가 `/users`에 navigate

**Then**:
- `data-testid="user-list-table"` visible
- 테이블 내부에 username `"admin"`, `"tester"`, `"auditor"`가 각각 텍스트로 노출됨 (`getByText` 검증)
- `GET /api/v1/users` 요청이 정확히 1회 발생함

---

### Scenario 8: 역할 매트릭스 조회 — `/roles`

**REQ 매핑**: REQ-ADMIN-E2E-ROLES-001
**Spec 파일**: `tests/e2e/roles.spec.ts`

**Given**:
- `loginAsSuperAdmin(page)` 완료
- `GET /api/v1/roles` 응답 mock: `[{code:'SUPER_ADMIN',name:'전체관리자'},{code:'ADMIN',name:'관리자'}]`
- `GET /api/v1/permissions` 응답 mock: `[{code:'USER:READ'},{code:'NOTICE:WRITE'}]`

**When**:
- 사용자가 `/roles`에 navigate

**Then**:
- `data-testid="role-matrix"` visible
- 매트릭스가 `role="grid"` 또는 `role="table"` 구조로 노출됨
- "SUPER_ADMIN", "ADMIN" 텍스트가 행 헤더에 노출됨

---

### Scenario 9: 공지 목록 조회 — `/notices`

**REQ 매핑**: REQ-ADMIN-E2E-NOTICES-001
**Spec 파일**: `tests/e2e/notices.spec.ts`

**Given**:
- `loginAsSuperAdmin(page)` 완료
- `GET /api/v1/notices` 응답 mock: `{ items: [{id:1,title:"시스템 점검 안내",categoryCode:"NOTICE"}], total: 1 }`

**When**:
- 사용자가 `/notices`에 navigate

**Then**:
- `data-testid="notice-list-table"` visible
- 공지 목록에 `"시스템 점검 안내"` 텍스트가 노출됨

---

## 4. 폼 유효성 검사 시나리오 (Form Validation)

### Scenario 10: 비밀번호 변경 — 새 비밀번호와 확인 불일치

**REQ 매핑**: REQ-ADMIN-E2E-PWCHANGE-001
**Spec 파일**: `tests/e2e/password-change.spec.ts`

**Given**:
- `loginAsSuperAdmin(page)` 완료
- 사용자가 `/account/password`에 접속
- `data-testid="input-current"`, `data-testid="input-new"`, `data-testid="input-confirm"` 입력 필드 visible

**When**:
- `data-testid="input-current"`에 `"current-pw"` 입력
- `data-testid="input-new"`에 `"new-password-A"` 입력
- `data-testid="input-confirm"`에 `"new-password-B"` 입력 (불일치)
- `data-testid="btn-submit"` 클릭

**Then**:
- `POST /api/v1/users/me/password` 또는 동등 엔드포인트로 네트워크 요청이 **발생하지 않음**
- `data-testid="error-alert"`가 visible 상태가 됨
- `data-testid="btn-submit"`이 여전히 enabled 상태 (재시도 가능)
- 페이지가 여전히 `/account/password`

---

### Scenario 11: 비밀번호 변경 — 정상 일치 케이스

**REQ 매핑**: REQ-ADMIN-E2E-PWCHANGE-001
**Spec 파일**: `tests/e2e/password-change.spec.ts`

**Given**:
- `loginAsSuperAdmin(page)` 완료
- 사용자가 `/account/password`에 접속
- 비밀번호 변경 API mock: 200 OK

**When**:
- `data-testid="input-new"`와 `data-testid="input-confirm"`에 동일한 값 입력
- `data-testid="btn-submit"` 클릭

**Then**:
- 비밀번호 변경 API 요청이 1회 발생함
- `data-testid="success-alert"`가 visible 상태가 됨

---

## 5. 로그아웃 시나리오 (Logout)

### Scenario 12: 로그아웃 — `/login` 리다이렉트

**REQ 매핑**: REQ-ADMIN-E2E-LOGOUT-001
**Spec 파일**: `tests/e2e/logout.spec.ts`

**Given**:
- `loginAsSuperAdmin(page)` 완료 (`/dashboard` 도달)
- `data-testid="btn-logout"` 또는 `aria-label="로그아웃"` 버튼이 visible

**When**:
- 사용자가 로그아웃 버튼 클릭

**Then**:
- Pinia auth store의 `accessToken === null`, `isAuthenticated === false`
- 페이지 URL이 `/login` 으로 변경됨 (redirect query 파라미터 없음)

---

### Scenario 13: 로그아웃 후 보호 라우트 재접근 (Edge Case)

**REQ 매핑**: REQ-ADMIN-E2E-LOGOUT-001, REQ-ADMIN-E2E-GUARD-001
**Spec 파일**: `tests/e2e/logout.spec.ts`

**Given**:
- Scenario 12의 결과 상태 (로그아웃 완료)

**When**:
- 사용자가 직접 `/dashboard`로 navigate 시도

**Then**:
- 페이지 URL이 `/login?redirect=%2Fdashboard` 로 리다이렉트됨

---

## 6. 에러 페이지 시나리오 (Error Pages)

### Scenario 14: 알 수 없는 경로 — 404 NotFound

**REQ 매핑**: REQ-ADMIN-E2E-ERROR-001
**Spec 파일**: `tests/e2e/error-pages.spec.ts`

**Given**:
- 새로운 브라우저 컨텍스트 (clean state)

**When**:
- 사용자가 `/no-such-path-12345`로 navigate

**Then**:
- `data-testid="not-found"` visible 또는 페이지 본문에 "404" 또는 "찾을 수 없" 텍스트 노출됨
- (라우터 동작에 따라) 로그인 페이지로 리다이렉트되지 않고 NotFound view가 직접 렌더되거나, 인증 가드 후 NotFound 도달

---

## 7. 세션 격리 시나리오 (Session Isolation — Pinia Runtime Memory 특성)

### Scenario 15: 페이지 새로고침 시 인증 상태 소실 (Edge Case)

**REQ 매핑**: REQ-ADMIN-E2E-AUTH-002
**Spec 파일**: `tests/e2e/auth-guard.spec.ts`

**Given**:
- `loginAsSuperAdmin(page)` 완료 (`/dashboard` 도달, `isAuthenticated === true`)

**When**:
- `page.reload()` 호출

**Then**:
- Pinia store의 `accessToken === null` (런타임 메모리 소실)
- 라우터 가드가 `/login?redirect=%2Fdashboard` 로 리다이렉트
- 이는 **버그가 아니라 의도된 보안 동작**임을 spec 주석으로 명시

---

## 8. 접근성 시나리오 (KWCAG 2.2 AA — P0 Gate)

### Scenario 16: 로그인 페이지 axe-core 위반 없음

**REQ 매핑**: REQ-ADMIN-E2E-A11Y-001
**Spec 파일**: `tests/e2e/a11y.spec.ts`

**Given**:
- 사용자가 `/login`에 접속함
- 페이지가 완전히 로드됨 (`networkidle`)

**When**:
- `new AxeBuilder({ page }).withTags(['wcag2a','wcag2aa','wcag21a','wcag21aa','wcag22aa']).disableRules(['color-contrast']).analyze()` 실행

**Then**:
- 반환된 `results.violations` 중 `impact === 'critical'` 또는 `impact === 'serious'` 인 항목이 **0건**
- moderate/minor 위반이 있을 경우 console.warn으로 출력 (테스트 fail 트리거 안 함)

---

### Scenario 17: 대시보드 axe-core 위반 없음

**REQ 매핑**: REQ-ADMIN-E2E-A11Y-002
**Spec 파일**: `tests/e2e/a11y.spec.ts`

**Given**:
- `loginAsSuperAdmin(page)` 완료
- `/dashboard` 페이지가 완전히 로드됨

**When**:
- AxeBuilder를 `/dashboard`에서 실행 (Scenario 16과 동일 설정)

**Then**:
- critical/serious 위반 0건
- moderate/minor 위반은 console.warn으로만 보고

---

## 9. 안전성 시나리오 (Safety / Negative)

### Scenario 18: 백엔드 데이터 쓰기 사이드이펙트 0건

**REQ 매핑**: REQ-ADMIN-E2E-SAFETY-001
**Spec 파일**: 모든 spec 파일 공통 (test-level 검증)

**Given**:
- 모든 E2E 테스트 실행
- 백엔드 서비스(`http://localhost:8080`)가 **기동되지 않은 상태**

**When**:
- 전체 E2E suite 실행 (`pnpm --filter @iroum/admin run test:e2e`)

**Then**:
- 모든 테스트가 통과 (page.route() mocking으로 백엔드 의존성 0)
- 실제 백엔드로의 HTTP 요청 0건 (네트워크 로그 확인 가능)
- 데이터베이스 상태 변경 0건

---

## 10. CI 통합 시나리오 (CI Integration)

### Scenario 19: `frontend-e2e-admin` CI Job 통과

**REQ 매핑**: REQ-ADMIN-E2E-CI-001
**Spec 파일**: N/A (CI workflow)

**Given**:
- GitHub Actions가 PR 또는 main 브랜치 push 시 트리거됨
- `.github/workflows/ci.yml`에 `frontend-e2e-admin` job이 추가됨
- `needs: [frontend-test]` 의존성이 충족됨

**When**:
- CI가 다음 단계를 순차 실행:
  1. `actions/checkout@v4`
  2. `actions/setup-node@v4` (Node 22)
  3. pnpm 설정 + `pnpm install --frozen-lockfile`
  4. `pnpm --filter @iroum/admin exec playwright install chromium --with-deps`
  5. `pnpm --filter @iroum/admin run test:e2e`

**Then**:
- 모든 Playwright 테스트 통과 (exit code 0)
- `frontend/admin/playwright-report/` artifact가 14일 retention으로 업로드됨
- Job status가 success로 종료
- 테스트 실패 시 workflow 전체가 fail로 종료 (`continue-on-error: false`)

---

## 11. Definition of Done (DoD)

본 SPEC의 구현이 완료된 것으로 간주하는 기준:

### 11.1 기능적 완성도
- [ ] 19개 시나리오 모두 자동 테스트로 변환되어 통과
- [ ] 11개 spec 파일 모두 Chromium에서 0 failed
- [ ] 모든 REQ-ID가 최소 1개 시나리오에 매핑됨

### 11.2 품질 게이트
- [ ] `pnpm --filter @iroum/admin run test` 회귀 없음 (기존 53개 Vitest 통과)
- [ ] 새로 추가된 데이터-testid가 7개 view에 정확히 부착됨 (admin-layout, dashboard-main, user-list-table, role-matrix, notice-list-table, not-found, btn-logout)
- [ ] CSS 클래스 기반 셀렉터 0건 (`grep -rn "page.locator('\.\|page.locator(\"\." frontend/admin/tests/e2e/` 결과 0건)
- [ ] axe-core critical/serious 위반 0건 (login + dashboard)

### 11.3 CI 통합
- [ ] `frontend-e2e-admin` job이 main 브랜치에서 통과
- [ ] `playwright-report/` artifact가 정상 업로드됨
- [ ] Job 평균 실행 시간이 합리적 범위 (목표 5분 이내, 절대 기준 없음)

### 11.4 문서화
- [ ] `.moai/specs/SPEC-CMS-ADMIN-E2E-001/spec.md`의 상태가 `Draft → Implemented` 로 갱신됨
- [ ] HISTORY 테이블에 구현 완료 항목 추가
- [ ] `frontend/admin/tests/e2e/fixtures/auth.ts`에 `@MX:ANCHOR` + `@MX:NOTE` 태그 부착

### 11.5 안전성
- [ ] 백엔드 서비스 미기동 상태에서도 모든 E2E 테스트 통과
- [ ] frontend/admin/src/ 변경 사항이 data-testid 속성 추가에 국한됨 (`git diff` 검토로 확인)
- [ ] Pinia auth store, 라우터, 컴포넌트 로직 변경 0건

---

## 12. 참고 (References)

- `spec.md` (동일 디렉토리) — EARS 요구사항 원문
- `plan.md` (동일 디렉토리) — 10개 TDD 태스크 분해
- `research.md` (동일 디렉토리) — 라우트 맵, RBAC, data-testid 격차 분석
- `.moai/specs/SPEC-CMS-PUBLIC-E2E-001/acceptance.md` — 자매 SPEC 시나리오 패턴 참조
