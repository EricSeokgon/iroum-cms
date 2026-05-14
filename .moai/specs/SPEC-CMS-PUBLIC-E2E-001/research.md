# Research: SPEC-CMS-PUBLIC-E2E-001 — Public SPA Playwright E2E 테스트

**Generated**: 2026-05-14  
**Target**: frontend/public/ (Public Citizen-Facing SPA)

---

## 1. 프로젝트 현황

### 기술 스택
- Vue 3.5.13 + TypeScript 5.6
- Vite 6.0.3 (Dev server: port 5174)
- Element Plus 2.8.8, Tailwind CSS 3.4.16
- Pinia 2.2.6, Vue Router 4.4.5
- Axios 1.7.9 (API client)
- **현재 테스트**: Vitest 2.1.8 + jsdom (단위/컴포넌트 테스트만 존재)
- **Playwright**: 미설치 (tech.md에 계획됨)

### 기존 테스트 현황
- 30개 Vitest 파일, 3,216 라인 (API/Store/컴포넌트/뷰/접근성/i18n)
- **E2E 테스트: 0개** — 모든 테스트가 jsdom 기반 단위 테스트

---

## 2. 라우트 맵 (전체)

| 경로 | 이름 | 컴포넌트 | 인증 | noLayout |
|------|------|----------|------|---------|
| / | home | HomeView.vue | No | No |
| /notices | notice-list | NoticeListView.vue | No | No |
| /notices/:id | notice-detail | NoticeDetailView.vue | No | No |
| /boards/:code | board-post-list | BoardPostListView.vue | No | No |
| /boards/:code/posts/:id | board-post-detail | BoardPostDetailView.vue | No | No |
| /faqs | faq | FaqView.vue | No | No |
| /qnas | qna-list | QnaListView.vue | No | No |
| /qnas/new | qna-create | QnaCreateView.vue | **Yes** | No |
| /qnas/:id | qna-detail | QnaDetailView.vue | No | No |
| /me/qnas | my-qna | StubView | **Yes** | No |
| /publications | publication-list | PublicationListView.vue | No | No |
| /publications/:id | publication-detail | PublicationDetailView.vue | No | No |
| /policies | policy-list | PolicyListView.vue | No | No |
| /policies/match | policy-match | PolicyMatchView.vue | No | No |
| /policies/subscriptions | policy-subscription | StubView | **Yes** | No |
| /policies/:id | policy-detail | PolicyDetailView.vue | No | No |
| /safety/guidelines | safety-guideline-list | SafetyGuidelineListView.vue | No | No |
| /safety/guidelines/:id | safety-guideline-detail | SafetyGuidelineDetailView.vue | No | No |
| /safety/incidents | safety-incident-list | SafetyIncidentListView.vue | No | No |
| /stats | public-stats | PublicStatsView.vue | No | No |
| /media | media-gallery | MediaGalleryView.vue | No | No |
| /search | search | SearchResultView.vue | No | No |
| /sitemap | sitemap | SiteMapView.vue | No | No |
| /login | login | LoginView.vue | No | **Yes** |
| /maintenance | maintenance | MaintenanceView.vue | No | **Yes** |
| /error/403 | forbidden | ForbiddenView.vue | No | **Yes** |
| /error/500 | server-error | ServerErrorView.vue | No | **Yes** |
| /:pathMatch(.*) | not-found | NotFoundView.vue | No | **Yes** |

### 주요 참고사항
- `requiresAuth: true` 라우트 → /login?redirect=URL 리다이렉트
- `noLayout: true` 라우트 → PublicLayout 미사용 (에러/로그인 페이지)
- LoginView는 Phase 0 스텁 (실제 폼 미구현)

---

## 3. API 엔드포인트 (공개 SPA 사용)

| API 파일 | 엔드포인트 |
|---------|-----------|
| authApi.ts | POST /auth/login, POST /auth/logout, POST /auth/refresh, GET /auth/me |
| noticeApi.ts | GET /notices, GET /notices/:id |
| boardApi.ts | GET /boards/:code, GET /boards/:code/posts/:id |
| faqApi.ts | GET /faqs |
| qnaApi.ts | GET /qnas, GET /qnas/:id, POST /qnas |
| policyApi.ts | GET /policies, GET /policies/:id, POST /policies/match |
| searchApi.ts | GET /search?q=&type=&page=&size= |
| statsApi.ts | GET /stats/kpi-values, GET /stats/widgets |
| safetyApi.ts | GET /safety/guidelines, GET /safety/guidelines/:id, GET /safety/incidents |
| publicationApi.ts | GET /publications, GET /publications/:id |
| menuApi.ts | GET /menus/public |
| systemApi.ts | GET /system/health |
| mediaApi.ts | GET /media |

### 인증 패턴
- localStorage 키: `public.accessToken`, `public.refreshToken`
- 401 응답: requiresAuth=true인 경우만 /login 리다이렉트
- 403 응답: 항상 /error/403 리다이렉트
- 500+ 응답: GET/HEAD 메서드에서만 /error/500 리다이렉트
- 503+MAINTENANCE_MODE_ACTIVE: /maintenance 리다이렉트

---

## 4. data-testid 셀렉터 (주요 뷰)

### HomeView
- `home-hero`, `home-search-form`, `home-search-input`
- `home-notices-section`, `home-notices-list`, `home-notices-error`
- `home-policies-section`, `home-policies-list`, `home-quicklinks`

### NoticeListView
- form[role="search"], select#notice-category, input#notice-keyword
- `notice-list`, `notice-category-select`

### FaqView
- `faq-list`, `faq-header-${idx}`, `faq-panel-${id}`
- button[aria-expanded], panel[aria-controls]
- 키보드: @keydown.enter.prevent, @keydown.space.prevent

### SearchResultView
- SearchFilterTabs (role="tablist"), `search-result-list`, `search-empty-tip`
- 6탭: ALL/POST/FAQ/QNA/POLICY/SAFETY

### QnaCreateView
- `qna-create-form`, `qna-title`, `qna-content`, `qna-private`
- aria-invalid, aria-describedby (에러 상태)

### PolicyMatchView
- PolicyMatchForm: industry, capitalAmount, revenueAmount, employeeCount, region
- 결과: PolicyCard with match-score, match-reason

---

## 5. CI 워크플로우 현황 (.github/workflows/ci.yml)

### 기존 Job
1. **backend-test**: Spring Boot + Gradle + PostgreSQL 16
2. **frontend-test**: pnpm + Node 22, Matrix [admin, public], Coverage 업로드
3. **docker-build**: main 브랜치 push 시에만 (backend-test + frontend-test 성공 후)

### E2E Job 추가 위치
- `needs: frontend-test` (단위 테스트 통과 후 실행)
- backend API 서비스 컨테이너 필요 (localhost:8080)
- 아티팩트: playwright-report/ (14일 보관)

---

## 6. 접근성 패턴 (KWCAG 2.2 AA)

### 코드베이스 발견 패턴
- **스킵 네비게이션**: i18n 키 `common.skipNav` 존재 (PublicLayout에 있을 것)
- **폼 접근성**: label[for=], role="search", aria-invalid, aria-describedby
- **아코디언**: button[aria-expanded], panel[aria-controls], Enter/Space/Tab
- **탭 패널**: role="tablist", role="tab", aria-selected
- **포커스 링**: focus-visible:outline-2 focus-visible:outline-primary-600
- **장식 아이콘**: aria-hidden="true"
- **jest-axe**: tests/a11y/pages.spec.ts (critical violations, 색상 대비 제외)

### E2E에서 추가 검증 가능 항목
- 실제 키보드 탐색 (Tab 순서)
- 스킵네비 포커스 점프
- 실제 색상 대비 (axe-playwright 활용)
- 뷰포트별 반응형 동작

---

## 7. 환경 설정

- Dev 서버 포트: 5174
- API 프록시: /api → http://localhost:8080
- 환경 변수: VITE_API_BASE=/api/v1
- 테스트 데이터: 백엔드 시드 데이터 필요

---

## 8. 위험 요소 및 제약

| 위험 | 설명 | 완화 방안 |
|------|------|----------|
| 백엔드 의존성 | E2E는 실제 API 필요 | Docker Compose 또는 CI 서비스 컨테이너 |
| 인증 스텁 | LoginView 미구현 | localStorage 직접 주입으로 인증 상태 시뮬레이션 |
| 동적 데이터 | 공지/정책 데이터 변동 | 시드 데이터 고정 또는 범용 셀렉터 사용 |
| ECharts 렌더링 | Canvas 기반, CI에서 느림 | 애니메이션 비활성화, isVisible() 체크 |
| i18n 텍스트 | 번역 키로 렌더링됨 | data-testid 및 ARIA 속성으로 셀렉터 |
| 유지보수 모드 | 503으로 전체 차단 가능 | 테스트 환경 유지보수 모드 비활성화 |

---

## 9. 권장 우선순위

### P0 (출시 필수)
1. 홈 → 검색 → 결과 탭 필터링
2. 공지 목록 → 공지 상세
3. FAQ 아코디언 (키보드 포함)
4. 정책 매칭 폼 → 결과
5. 404/403/500 에러 페이지
6. 스킵 네비게이션 (KWCAG 2.2 AA)

### P1 (다음 스프린트)
1. QnA 생성 (인증 필요 — LoginView 구현 후)
2. 검색 히스토리
3. 정책 목록 필터링
4. 미디어 갤러리 탭
5. 크로스 브라우저 (Firefox, Safari)

---

## 10. 파일 구조 제안

```
frontend/public/
├── playwright.config.ts          # Playwright 설정
├── package.json                  # test:e2e 스크립트 추가
└── tests/
    ├── e2e/
    │   ├── fixtures/
    │   │   └── auth.ts           # localStorage 인증 헬퍼
    │   ├── home.spec.ts          # 홈 + 영웅 검색
    │   ├── notices.spec.ts       # 공지 목록/상세
    │   ├── faq.spec.ts           # FAQ 아코디언 + 키보드
    │   ├── search.spec.ts        # 검색 6탭
    │   ├── policy-match.spec.ts  # 정책 매칭 폼
    │   ├── error-pages.spec.ts   # 404/403/500/유지보수
    │   └── a11y.spec.ts          # KWCAG 2.2 AA
    └── ... (기존 Vitest 파일)
```
