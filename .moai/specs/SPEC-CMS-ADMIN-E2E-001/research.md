# Research: SPEC-CMS-ADMIN-E2E-001 — Admin SPA Playwright E2E 테스트

**Generated**: 2026-05-14
**Target**: frontend/admin/ (Admin SPA)

---

## 1. 프로젝트 현황

### 기술 스택
- Vue 3.5.13 + TypeScript 5.6
- Vite (Dev server: port 5173)
- Element Plus 2.8.8, Tailwind CSS 3.4.16
- Pinia 2.2.6, Vue Router 4.4.5
- @iroum/shared (공유 API 클라이언트, 타입)
- **현재 테스트**: Vitest 2.1.8 + jsdom (53개 파일, 949 라인)
- **Playwright**: 미설치 (E2E 테스트 0개)

### 기존 테스트 현황
- 53개 Vitest 파일, 949 라인 (store/composable/컴포넌트)
- **E2E 테스트: 0개** — 모든 테스트가 jsdom 기반 단위 테스트

---

## 2. 인증 아키텍처 (Public SPA와의 핵심 차이점)

### Admin SPA 인증 (Pinia Runtime Store)
```typescript
// frontend/admin/src/stores/auth.ts
export const useAuthStore = defineStore('auth', () => {
  const accessToken = ref<string | null>(null)   // localStorage 아님!
  const expiresAt = ref<number | null>(null)
  const user = ref<{ id: number; username: string; roleCodes: string[] } | null>(null)
  const isAuthenticated = computed(
    () => !!accessToken.value && (expiresAt.value ?? 0) > Date.now()
  )
  async function login(username: string, password: string): Promise<void> {
    const res = await apiClient.post<LoginResponse>('/auth/login', { username, password })
    _applyToken(res.data.accessToken, res.data.expiresInSeconds)
  }
  // ...
})
```

**핵심**: 토큰이 런타임 메모리에만 존재 → 페이지 새로고침 시 인증 상태 소실.
Public SPA와 달리 `localStorage.setItem()` 패턴으로 auth 주입 불가.

### E2E Auth 전략 선택지

| 전략 | 장점 | 단점 |
|------|------|------|
| **폼 기반 로그인** (권장) | 실제 로그인 흐름 검증 | 백엔드 필요, 테스트 계정 필요 |
| `page.route()` 모킹 | 백엔드 불필요 | 실제 auth 플로우 미검증 |
| `store._applyToken()` 직접 호출 | 빠름 | Vue devtools 비활성 시 불안정 |

**결론**: 폼 기반 로그인 글로벌 fixture (`storageState` 활용) → 최초 1회 로그인 후 세션 재사용

### 라우터 인증 가드
```typescript
router.beforeEach((to, _from, next) => {
  const auth = useAuthStore()
  if (to.meta.requiresAuth && !auth.isAuthenticated) {
    next({ name: 'login', query: { redirect: to.fullPath } })
    return
  }
  if (to.name === 'login' && auth.isAuthenticated) {
    next({ name: 'dashboard' })
    return
  }
  next()
})
```

---

## 3. 라우트 맵 (전체)

### 공개 라우트 (2개)
| 경로 | 이름 | 컴포넌트 | 비고 |
|------|------|----------|------|
| /login | login | LoginView.vue | REAL 폼 (공개 SPA와 달리 실제 구현) |
| /forgot-password | forgot-password | ForgotPasswordView.vue | OTP 기반 |

### 인증 필요 라우트 (54개 — AdminLayout 하위)

**대시보드**
| 경로 | 이름 | 권한 |
|------|------|------|
| /dashboard | dashboard | All |
| /dashboard/summary | dashboard-summary | All |
| /dashboard/widgets | dashboard-widgets | SUPER_ADMIN |
| /dashboard/export-history | dashboard-export-history | All |

**사용자/조직/역할**
| 경로 | 이름 | 권한 |
|------|------|------|
| /users | user-list | All |
| /users/:id | user-detail | All |
| /organizations | organization-tree | All |
| /roles | role-matrix | All |

**계정**
| 경로 | 이름 | 권한 |
|------|------|------|
| /account/password | password-change | All |
| /account/personal-data-access | my-personal-data-access | All |
| /account/login-history | my-login-history | All |

**감사**
| 경로 | 이름 | 권한 |
|------|------|------|
| /audit/permission-changes | permission-change-history | All |
| /audit/personal-data-access | personal-data-access-log | All |
| /audit/login-history | login-history | All |

**공지/게시판/FAQ/QnA/출판물/미디어** (콘텐츠)
| 경로 | 이름 |
|------|------|
| /notices, /notices/new, /notices/:id/edit | 공지 CRUD |
| /boards/:code, /boards/:code/posts/:id | 게시판 |
| /faqs, /faqs/new, /faqs/:id/edit | FAQ CRUD |
| /qnas, /qnas/:id | QnA 목록/상세 |
| /publications, /publications/new, /publications/:id/edit | 출판물 CRUD |
| /media | 미디어 갤러리 |

**시스템 (SYSTEM:* 권한)**
| 경로 | 이름 | 권한 |
|------|------|------|
| /system/codes | system-codes | SYSTEM:CODE:READ |
| /system/settings | system-settings | SYSTEM:SETTING:READ |
| /system/maintenance | system-maintenance | SYSTEM:MAINT:READ |
| /system/audit-logs | system-audit-logs | SYSTEM:AUDIT:READ |

**안전관리**
| 경로 | 이름 | 권한 |
|------|------|------|
| /safety/incidents | safety-incidents | All |
| /safety/incidents/:id | safety-incident-detail | All |
| /safety/profile | safety-profile | All |
| /safety/match | safety-match | All |
| /admin/safety/templates | safety-templates | SAFETY:TEMPLATE:READ |

**정책사업**
| 경로 | 이름 | 권한 |
|------|------|------|
| /policy/programs | policy-programs | All |
| /policy/match | policy-match | All |
| /admin/policy/dispatch | policy-dispatch | POLICY:DISPATCH:READ |

**데이터 거버넌스**
| 경로 | 이름 | 권한 |
|------|------|------|
| /governance/dictionary | governance-dictionary | ADMIN |
| /governance/retention-policies | governance-retention | ADMIN |
| /governance/batch-logs | governance-batch-logs | ADMIN |
| /governance/quality-rules | governance-quality-rules | ADMIN |
| /governance/quality-reports | governance-quality-reports | ADMIN |
| /governance/recovery-drills | governance-recovery-drills | ADMIN |
| /governance/stats | governance-stats | ADMIN |

**통합 검색**
| 경로 | 이름 | 권한 |
|------|------|------|
| /search | search | All |
| /search/synonyms | search-synonyms | ADMIN |
| /search/analytics | search-analytics | ADMIN |

---

## 4. data-testid 현황 (매우 희소 — 19개)

| 파일 | data-testid |
|------|------------|
| LoginView.vue | login-notice, login-error |
| ForgotPasswordView.vue | global-error, expiry-countdown, otp-input, attempts-left, resend-button |
| PasswordChangeView.vue | success-alert, error-alert, input-current, input-new, input-confirm, btn-submit, btn-cancel |
| HealthView.vue | health-status, health-service, health-version |
| NotificationSettingsView.vue | switch-qna-answer-email |

**결론**: E2E 테스트 구축 시 핵심 뷰에 data-testid 추가 필수. 76개 뷰 중 5개 파일만 커버됨.

---

## 5. RBAC 권한 코드

### 역할 코드 (roleCodes in JWT payload)
- `SUPER_ADMIN`: 전체 권한 (위젯 관리 포함)
- `DEPT_ADMIN`: 부서 관리
- `ADMIN`: 일반 관리자
- `USER`: 일반 사용자 (로그인만 가능)

### 세부 권한 코드 (route meta.permissions)
- `SYSTEM:CODE:READ`, `SYSTEM:SETTING:READ`, `SYSTEM:MAINT:READ`, `SYSTEM:AUDIT:READ`
- `SAFETY:TEMPLATE:READ`
- `POLICY:DISPATCH:READ`
- `ROLE:READ` (역할 관리)

---

## 6. LoginView 분석 (REAL 구현)

```html
<!-- data-testid: login-notice, login-error -->
<el-input id="username" v-model="form.username" name="username" autocomplete="username" />
<el-input id="password" v-model="form.password" name="password" type="password" />
<el-button type="primary" native-type="submit" :loading="loading" />
```

- 폼 제출: POST /api/v1/auth/login
- 성공: Pinia store 토큰 저장 → redirect query 또는 /dashboard
- 실패: login-error alert 표시 (aria-live="polite")
- KWCAG 3.3.1: 오류 식별, 2.4.6: 레이블

---

## 7. CI 워크플로우 현황 (.github/workflows/ci.yml)

### 기존 Jobs
1. **backend-test**: Spring Boot + Gradle + PostgreSQL 16
2. **frontend-test**: pnpm + Node 22, Matrix [admin, public], Coverage 업로드
3. **frontend-e2e** (SPEC-CMS-PUBLIC-E2E-001에서 추가): public SPA only
4. **docker-build**: main 브랜치 push 시에만

### Admin E2E Job 추가 위치
- `needs: [frontend-test, backend-test]` (백엔드 필수 — 실제 로그인 API 필요)
- 또는 `needs: [frontend-test]` + `page.route()` 모킹으로 backend 불필요

---

## 8. 접근성 패턴 (KWCAG 2.2 AA)

### 코드베이스 발견 패턴
- **로그인 폼**: aria-required, aria-describedby, aria-live="polite"
- **역할 접근성**: role="alert", role="status"
- **포커스 링**: Element Plus 기본 focus-visible
- **i18n**: t() 함수 사용 → data-testid + ARIA로만 셀렉터 사용

---

## 9. 위험 요소 및 제약

| 위험 | 설명 | 완화 방안 |
|------|------|----------|
| 백엔드 의존성 | 로그인 API (실제 구현) | page.route() 인터셉트 or CI 서비스 컨테이너 |
| Pinia 런타임 메모리 | localStorage 주입 불가 | 폼 로그인 fixture + storageState 세션 재사용 |
| data-testid 부재 | 76개 뷰 중 5개만 커버 | E2E 구축 시 핵심 뷰에 data-testid 추가 |
| RBAC 테스트 복잡성 | 역할별 다른 뷰 표시 | SUPER_ADMIN 계정으로 P0 커버, 권한 제한 뷰는 P1 |
| Element Plus 컴포넌트 | 내부 DOM 복잡 | role, aria-label, label[for] 셀렉터 활용 |
| 페이지 새로고침 | Pinia 상태 소실 → 로그인 필요 | storageState 세션 저장/복원 불가능 → 매 테스트 로그인 |

---

## 10. 파일 구조 제안

```
frontend/admin/
├── playwright.config.ts              # Playwright 설정 (port 5173)
├── package.json                      # test:e2e 스크립트 추가
└── tests/
    ├── e2e/
    │   ├── fixtures/
    │   │   ├── auth.ts               # 로그인 폼 기반 auth fixture
    │   │   └── page.route.ts         # API 모킹 헬퍼
    │   ├── login.spec.ts             # 로그인 폼 + 오류 처리
    │   ├── dashboard.spec.ts         # 대시보드 + 인증 가드
    │   ├── users.spec.ts             # 사용자 관리 CRUD
    │   ├── roles.spec.ts             # 역할/권한 매트릭스
    │   ├── notices.spec.ts           # 공지 관리
    │   ├── error-pages.spec.ts       # 404, 인증 리다이렉트
    │   └── a11y.spec.ts              # KWCAG 2.2 AA
    └── ... (기존 Vitest 파일)
```

---

## 11. 권장 우선순위

### P0 (출시 필수)
1. 로그인 성공/실패 플로우
2. 인증 가드 — 미인증 시 /login?redirect= 리다이렉트
3. 대시보드 접근 + 기본 콘텐츠 렌더링
4. 사용자 목록 조회 (/users)
5. 역할/권한 매트릭스 조회 (/roles)
6. 404 페이지
7. 스킵 네비게이션 (KWCAG 2.4.1)

### P1 (다음 스프린트)
1. 공지사항 CRUD (/notices)
2. 비밀번호 변경 (/account/password)
3. RBAC 권한 제한 검증 (SUPER_ADMIN vs ADMIN)
4. 사용자 상세 (/users/:id)
5. FAQ 관리 CRUD

---

## 12. API 모킹 전략

### page.route() 패턴 (backend 불필요)
```typescript
await page.route('/api/v1/auth/login', async (route) => {
  await route.fulfill({
    status: 200,
    json: { accessToken: 'mock.jwt.token', expiresInSeconds: 3600 }
  })
})
```

**단점**: JWT 디코드 실패 → user.value = null → 일부 뷰에서 사용자명 미표시.
**해결**: decodeJwt()가 파싱 가능한 유효한 JWT 구조의 목 토큰 사용.

### 유효한 Mock JWT 생성
```typescript
// header.payload.signature 구조 (서명 검증 없음 — 클라이언트는 파싱만 함)
const mockPayload = btoa(JSON.stringify({ sub: 'admin', uid: 1, roles: ['SUPER_ADMIN'], exp: 9999999999, iat: 1000000000 }))
const mockToken = `eyJhbGciOiJIUzI1NiJ9.${mockPayload}.mock`
```
