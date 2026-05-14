# SPEC-CMS-PUBLIC-001: 구현 계획 (Implementation Plan)

## 1. 개요

본 문서는 SPEC-CMS-PUBLIC-001(시민 대상 공공 사이트 SPA) 구현을 위한 마일스톤·기술 접근법·리스크·완료 정의를 정의한다. 백엔드 API는 SPEC-CMS-002~010 + MEDIA-001에서 모두 Tested 상태이므로 본 SPEC은 프론트엔드 구현만 다룬다.

기준 디렉터리: `frontend/public/`

기술 스택(고정):

- Vue 3.5.13 + TypeScript 5.6.3
- Vite 6.0.3 (빌드/개발)
- Pinia 2.2.6 (상태)
- Vue Router 4.4.5 (라우팅)
- Element Plus 2.8.8 (UI 컴포넌트)
- Tailwind CSS 3.4.16 (스타일링)
- vue-i18n 9.14.1 (다국어)
- axios 1.7.9 (HTTP 클라이언트)
- Vitest 2.1.8 + @vue/test-utils 2.4.6 + jsdom 25.0.1 (단위/컴포넌트 테스트)

추가 도입 검토(Phase 0에서 결정):

- 차트 라이브러리: ECharts 5.x (선호) 또는 Chart.js 4.x
- DOMPurify (XSS 클라이언트 심층 방어)
- @unhead/vue 또는 자체 useSeoMeta 구현(SEO 메타 동적 갱신)
- ESLint plugin-vue-i18n (raw 문자열 작성 금지)

---

## 2. 마일스톤 (Priority 기반, 시간 추정 없음)

### Phase 0: Foundation (Priority High)

**목표**: 모든 페이지가 사용할 공용 인프라(레이아웃, 라우터 가드, API 클라이언트, 스토어, i18n, 컴포저블)를 완성.

**산출물**:

1. `frontend/public/src/api/client.ts` — axios 인스턴스 + 인터셉터 (요청 토큰 첨부, 401 refresh, 503 maintenance, 5xx fallback)
2. `frontend/public/src/api/*.ts` — 9개 도메인 클라이언트 모듈(auth, notice, board, faq, qna, publication, policy, safety, dashboard, media, search, menu, system) 타입 시그니처만 작성(스텁 가능)
3. `frontend/public/src/stores/auth.ts` — 기존 placeholder 확장 (login/logout/refresh + LocalStorage 영속화)
4. `frontend/public/src/stores/{menu,maintenance,search,locale,breadcrumb}.ts` — 신규 5개 스토어
5. `frontend/public/src/layouts/PublicLayout.vue` — 헤더+브레드크럼+main+푸터+스킵내비+백투탑
6. `frontend/public/src/components/nav/{PublicHeader,PublicFooter,PublicBreadcrumb,PublicSkipNav}.vue` — 4종 컴포넌트
7. `frontend/public/src/components/common/{PageHeader,PaginationBar,SearchInput,EmptyState,ErrorState,LoadingState,AttachmentDownload}.vue` — 7종 공용 컴포넌트
8. `frontend/public/src/composables/{useApi,useDebounce,useMenuTree,usePagination,useBreadcrumb,useSeoMeta,useFocusTrap}.ts` — 7종 컴포저블
9. `frontend/public/src/router/index.ts` — 25개 라우트 정의 + 4개 가드(점검·인증·i18n·타이틀)
10. `frontend/public/src/locales/{ko,en}.json` — i18n 메시지 골격(약 100개 키 — 메뉴, 공용 액션, 에러)
11. `frontend/public/src/assets/main.css` — Tailwind 베이스 + 디자인 토큰(색상, 폰트, 간격)
12. 차트 라이브러리 1차 결정(ECharts vs Chart.js) — Phase 3 진입 전 필수
13. ESLint 규칙 추가 — plugin-vue-i18n no-raw-text

**완료 조건**:

- `pnpm --filter @iroum-cms/public dev`로 빈 홈 페이지 + 레이아웃 정상 렌더링
- 라우터 가드 4종 단위 테스트 통과(Vitest 12 케이스 이상)
- 9개 API 클라이언트 타입 시그니처 빌드 통과(`vue-tsc -b`)
- Tailwind breakpoint 4종(default/sm/md/lg/xl) 헤더 동작 수동 검증

### Phase 1: Core Pages — 공지·게시판·FAQ·Q&A (Priority High)

**목표**: 시민 사이트의 가장 빈번한 진입 경로(공지·FAQ·게시판) 완성.

**산출물**:

1. `views/HomeView.vue` — 5개 섹션(히어로/공지/정책/빠른링크/KPI) — `GET /api/v1/notices`, `GET /api/v1/policies?featured`, `GET /api/v1/dashboard/widgets/public-home/data`
2. `views/notices/NoticeListView.vue` + `NoticeDetailView.vue` — SPEC-CMS-003 공지 API
3. `views/boards/BoardPostListView.vue` + `BoardPostDetailView.vue` — SPEC-CMS-003 게시판 API + 마스터 타입별 레이아웃 분기
4. `views/FaqView.vue` — 카테고리 아코디언 + 키워드 검색
5. `views/qnas/QnaListView.vue` + `QnaDetailView.vue` + `QnaCreateView.vue` — 공개 목록 + 인증 작성
6. `components/notice/{NoticeCard,NoticeContent}.vue` — 공통 카드 + 본문 렌더링
7. `components/common/AttachmentDownload.vue` 통합 — 서명 URL 발급 → 즉시 리다이렉트
8. 인수기준 A·B 그룹 자동화 테스트(Vitest 16 케이스)

**완료 조건**:

- A·B 그룹 인수기준 16개 시나리오 통과
- 첨부 다운로드 흐름 수동 회귀 통과 (서명 URL TTL 15분 검증)
- 본문 v-html 렌더링 + XSS 차단 검증 (`<script>` 포함 mock 응답 → 렌더링 안됨)

### Phase 2: Policy & Safety & Publication (Priority High)

**목표**: 정책사업 매칭·구독 흐름 + 안전 가이드·사고사례 + 발간자료 완성.

**산출물**:

1. `views/policies/{PolicyListView,PolicyDetailView,PolicyMatchView,PolicySubscriptionView}.vue` — SPEC-CMS-007
2. `views/safety/{SafetyGuidelineListView,SafetyGuidelineDetailView,SafetyIncidentListView}.vue` — SPEC-CMS-006
3. `views/publications/{PublicationListView,PublicationDetailView}.vue` — SPEC-CMS-003 발간자료
4. `components/policy/{PolicyCard,PolicyFilterBar,PolicyMatchForm}.vue`
5. `components/safety/{SafetyChecklist,IncidentCard}.vue`
6. 다중 첨부 zip 다운로드 흐름 — SPEC-CMS-003 `POST /api/v1/posts/:id/download-zip`
7. 외부 신청 URL 안전 처리(`rel="noopener noreferrer"` + 도메인 표시)
8. 인수기준 C 그룹 자동화 테스트(Vitest 8 케이스)

**완료 조건**:

- C 그룹 인수기준 8개 시나리오 통과
- 정책 매칭 익명 흐름 수동 회귀(`/policies/match` 비로그인 진입 → 결과 표시)
- 알림 구독 인증 흐름(미로그인 → `/login?redirect=/policies/subscriptions` → 로그인 후 복귀)

### Phase 3: Search & Stats & Media (Priority Medium)

**목표**: 통합 검색 결과·공개 통계·미디어 갤러리·사이트맵 완성.

**산출물**:

1. `views/SearchResultView.vue` — SPEC-CMS-010 통합 검색 + 타입 탭 + 하이라이트
2. `views/PublicStatsView.vue` — SPEC-CMS-008 공개 위젯 + 차트(ECharts 또는 Chart.js)
3. `views/MediaGalleryView.vue` — SPEC-CMS-MEDIA-001 + lazy load
4. `views/SitemapView.vue` — SPEC-CMS-010 메뉴 트리(3-depth)
5. `components/search/{SearchResultCard,SearchFilterTabs}.vue`
6. `components/stats/KpiChart.vue` — 차트 + 데이터 테이블 fallback(접근성)
7. 인수기준 D 그룹 자동화 테스트(Vitest 6 케이스)

**완료 조건**:

- D 그룹 인수기준 6개 시나리오 통과
- 검색 응답 < 3s (PER-003) 수동 측정
- 차트 데이터 테이블 fallback 키보드 접근 검증

### Phase 4: Auth & Maintenance & Errors (Priority Medium)

**목표**: 로그인·내 Q&A·점검 안내·에러 페이지 완성.

**산출물**:

1. `views/LoginView.vue` — `noLayout` 미니멀 디자인 + redirect 처리
2. `views/qnas/MyQnaListView.vue` — 본인 Q&A 목록(인증 필수)
3. `views/MaintenanceView.vue` — 점검 안내 + 자동 새로고침(5분)
4. `views/errors/{ForbiddenView,ServerErrorView}.vue` — 403/500 표시
5. 토큰 refresh 다중 탭 동기화(LocalStorage `storage` 이벤트)
6. 인수기준 F 그룹 자동화 테스트(Vitest 7 케이스)

**완료 조건**:

- F 그룹 인수기준 7개 시나리오 통과
- 점검 모드 가드 강제 리다이렉트 수동 검증(점검 모드 on/off 토글)
- 다중 탭 토큰 동기화 수동 검증

### Phase 5: Accessibility & i18n Polish (Priority High, Phase 1~4와 병행)

**목표**: KWCAG 2.2 AA 인증 수준 달성 + ko/en 전수 검증.

**산출물**:

1. axe-core 통합 Vitest 플러그인 (`@axe-core/test-utils` 또는 수동 통합)
2. 모든 페이지 axe-core 검사 0 critical
3. 키보드 순회 E2E (Playwright 골격 — 1차는 단위 테스트로 대체)
4. Lighthouse CI 통합 (목표: Accessibility ≥ 95, Best Practices ≥ 90)
5. 모든 컴포넌트 ARIA 속성 검증 단위 테스트
6. ko/en 메시지 누락 검증 스크립트 (`scripts/check-i18n-coverage.ts`)
7. 200% 확대 회귀 수동 검증 (Chrome DevTools)
8. 색대비 자동 검증 (axe-core color-contrast 규칙)
9. 인수기준 E 그룹 자동화 테스트(Vitest 8 케이스)

**완료 조건**:

- E 그룹 인수기준 8개 시나리오 통과
- Lighthouse Accessibility ≥ 95 (홈, 공지 목록, 공지 상세, 정책 상세, 검색 결과 5개 페이지)
- axe-core 0 critical 위반 전체 페이지
- ko/en 메시지 키 누락 0건

### Phase 6: Performance & Build & Quality Gates (Priority Medium)

**목표**: 성능 목표 충족 + 빌드 최적화 + 품질 게이트.

**산출물**:

1. Vite 번들 분석 (`vite-bundle-visualizer`)
2. 라우트 단위 코드 스플리팅 검증 (`() => import(...)` 패턴 전수)
3. 이미지 lazy load (`<img loading="lazy" decoding="async">`)
4. Vite manualChunks (vendor 분리: vue/vue-router/pinia, element-plus, echarts)
5. Lighthouse CI 통합 (목표: LCP ≤ 2.5s 데스크탑, ≤ 4s 4G 모바일)
6. 번들 크기 게이트 (initial JS gzip ≤ 300KB)
7. ESLint + vue-tsc 0 errors 게이트
8. Vitest 커버리지 ≥ 85% (lines/functions/branches)
9. README 갱신 (`frontend/public/README.md` — 개발 가이드)

**완료 조건**:

- Lighthouse Performance ≥ 80 (홈, 검색 결과)
- 번들 크기 ≤ 300KB (initial JS gzip)
- ESLint + vue-tsc 0 errors
- Vitest 커버리지 ≥ 85%

---

## 3. 기술 접근법

### 3.1 폴더 구조 원칙

- **도메인별 분리**: views, components, api는 도메인별 하위 폴더(notices/, policies/, safety/, qnas/) 사용. 공용은 common/, nav/.
- **컴포넌트 명명**: `*View.vue` (라우트 진입점), `*Card.vue` (재사용 카드), `*Bar.vue` (필터/페이징), `*Form.vue` (입력 폼), `*Content.vue` (본문 렌더링).
- **타입 분리**: `types/` 폴더에 도메인 DTO 인터페이스(`Notice`, `Post`, `Policy`, `Faq`, `Qna`, `SafetyGuideline`, `MenuNode`, `SearchResult` 등). API 응답 스키마와 1:1.

### 3.2 상태 관리 전략 (Pinia)

- **자주 변경되는 상태**: 컴포넌트 로컬 ref (페이지 페이징, 폼 입력)
- **전역 공유 + 캐시**: Pinia 스토어 (auth, menu 트리, maintenance 상태, locale, breadcrumb, search 히스토리)
- **API 응답 캐시**: 본 SPEC은 1차 미적용. 향후 vue-query 또는 자체 캐시 도입 검토.
- **LocalStorage 영속화**:
  - `public.auth` — accessToken/refreshToken만(user는 메모리)
  - `public.locale` — i18n locale (ko/en)
  - `public.search.history` — 최근 검색어 5개
  - `public.consent` — 쿠키/추적 동의(후속)

### 3.3 라우터 가드 순서

```
1. maintenanceGuard — 점검 모드 시 /maintenance로 강제 (제외: /maintenance, /error/*)
2. authGuard — requiresAuth=true && !isAuthenticated 시 /login?redirect=...로
3. i18nGuard — ?lang=... 처리 후 query 제거
4. (afterEach) titleGuard — document.title 갱신
5. (scrollBehavior) — 스크롤 복원 + hash 점프
```

### 3.4 API 호출 표준

- **컴포넌트 → composable(`useApi`) → api 클라이언트 모듈 → axios 인스턴스**
- 페이지 진입 시: `onMounted` + `useApi(() => api.fetchX(), { onError, retry: false })`
- 폼 제출: `useApi(() => api.submitX(payload), { showLoading, showError })` — Element Plus `ElMessage` 토스트
- 검색 자동완성: `useDebounce(300)` + axios cancel token (이전 요청 취소)

### 3.5 SEO 메타 갱신 패턴

```
// useSeoMeta 컴포저블 (자체 구현 또는 @unhead/vue)
onMounted(() => {
  useSeoMeta({
    title: notice.title,
    description: notice.content_text.slice(0, 150),
    ogImage: notice.coverImage || '/default-og.png',
  })
})
```

1차 한계: Googlebot은 JS 실행 후 메타 인덱싱 가능하나 일부 크롤러(네이버봇 등)는 미흡. 후속 SSR/prerender SPEC에서 보완.

### 3.6 접근성 검증 체크리스트 (PR 게이트)

PR 머지 전 다음 항목 자동/수동 검증:

| 항목 | 도구 | 자동/수동 |
|------|------|----------|
| axe-core 0 critical | jest-axe (Vitest) | 자동 |
| Lighthouse Accessibility ≥ 95 | Lighthouse CI | 자동 |
| 키보드 순회 (Tab/Shift+Tab) | Playwright (Phase 5 후) | 자동 → 1차 수동 |
| 스크린리더 (NVDA, VoiceOver) | 수동 | 수동 |
| 200% 확대 가로 스크롤 미발생 | Chrome DevTools | 수동 |
| 색대비 4.5:1 (본문) / 3:1 (큰 텍스트) | axe-core color-contrast | 자동 |
| 폼 라벨 누락 | axe-core label | 자동 |
| 이미지 alt 누락 | axe-core image-alt | 자동 |
| h1~h6 계층 위반 | axe-core heading-order | 자동 |

### 3.7 테스트 전략

**Vitest + @vue/test-utils** (단위·컴포넌트):

- 라우터 가드 4종 (`router.beforeEach` mock 테스트)
- 스토어 액션 (auth.login/logout, menu.loadMenuTree, maintenance.checkMaintenance)
- API 클라이언트 인터셉터 (mock-adapter로 401 refresh, 503 redirect)
- 컴포넌트 렌더링 (props, slots, emits)
- 컴포넌트 상호작용 (click, input, keydown)
- ARIA 속성 검증 (`expect(wrapper.attributes('aria-label')).toBe(...)`)
- jest-axe 통합 (각 페이지 렌더링 후 axe 검사)

**Playwright** (E2E — 1차 골격, 2차 본격):

- 키보드 순회 (Tab으로 모든 인터랙티브 요소 접근)
- 다국어 토글 (ko ↔ en 전 페이지 갱신 확인)
- 인증 흐름 (로그인 → 보호 페이지 → 로그아웃)
- 검색 흐름 (홈 → 검색 → 결과 → 상세)
- 점검 모드 시뮬레이션 (mock 응답으로 강제 리다이렉트)

**Lighthouse CI**:

- 5개 대표 페이지 (홈, 공지 목록, 공지 상세, 정책 상세, 검색 결과)
- 목표: Performance ≥ 80, Accessibility ≥ 95, Best Practices ≥ 90, SEO ≥ 80(CSR 한계 고려)

### 3.8 빌드·배포 전략

- **개발 환경**: Vite dev server on `5174` (admin은 `5173`). Backend proxy `http://localhost:8080`.
- **빌드**: `pnpm --filter @iroum-cms/public build` → `dist/`에 정적 파일.
- **배포**: 정적 호스팅(Nginx 또는 CDN) + SPA fallback (`try_files $uri /index.html`).
- **환경 변수**:
  - `VITE_API_BASE_URL` — 백엔드 베이스 URL
  - `VITE_PUBLIC_SITE_CODE` — `public` (SPEC-CMS-010 메뉴 트리 siteCode)
  - `VITE_PRINT_ENV` — `production` / `staging` / `development` (디버그 배너 표시)

---

## 4. 도메인별 컴포넌트 매핑 (요약)

| 도메인 | Views | Components | Stores | API Modules |
|--------|-------|-----------|--------|-------------|
| 홈·소개 | HomeView, AboutView | NoticeCard, PolicyCard | menu | notice, policy, dashboard, menu |
| 공지·게시판 | NoticeList/Detail, BoardPostList/Detail | NoticeCard, NoticeContent | — | notice, board |
| FAQ·Q&A | FaqView, QnaList/Detail/Create, MyQnaList | — | auth | faq, qna |
| 발간자료 | PublicationList/Detail | — | — | publication |
| 정책 | PolicyList/Detail/Match/Subscription | PolicyCard, PolicyFilterBar, PolicyMatchForm | auth | policy |
| 안전 | SafetyGuidelineList/Detail, SafetyIncidentList | SafetyChecklist, IncidentCard | — | safety |
| 검색 | SearchResultView | SearchResultCard, SearchFilterTabs | search | search |
| 통계 | PublicStatsView | KpiChart | — | dashboard |
| 미디어 | MediaGalleryView | — | — | media |
| 사이트맵 | SitemapView | — | menu | menu |
| 인증 | LoginView | — | auth | auth |
| 점검 | MaintenanceView | — | maintenance | system |
| 에러 | ForbiddenView, ServerErrorView, NotFoundView | — | — | — |

---

## 5. 의존성·전제

### 5.1 백엔드 의존성 (모두 Tested 상태 확인)

| SPEC | 사용 엔드포인트 | 비고 |
|------|---------------|------|
| SPEC-CMS-002 | `/api/v1/auth/login`, `/auth/refresh`, `/auth/logout` | JWT Bearer |
| SPEC-CMS-003 | `/api/v1/boards`, `/api/v1/posts`, `/api/v1/notices`, `/api/v1/faqs`, `/api/v1/qnas`, `/api/v1/publications`, `/api/v1/attachments/:id/download-url` | 게시판/공지/FAQ/Q&A/발간자료 |
| SPEC-CMS-005 | `/api/v1/system/codes` | 카테고리 코드 lookup |
| SPEC-CMS-006 | `/api/v1/safety/guidelines`, `/api/v1/safety/incidents`, `/api/v1/safety/checklists` | 안전관리 |
| SPEC-CMS-007 | `/api/v1/policies`, `/api/v1/policies/match`, `/api/v1/policies/subscriptions` | 정책사업 |
| SPEC-CMS-008 | `/api/v1/dashboard/widgets/*/data`, `/api/v1/kpi/values` | 공개 KPI |
| SPEC-CMS-009 | `/api/v1/system/health`, `/api/v1/system/maintenance-notices` | 점검 모드 |
| SPEC-CMS-010 | `/api/v1/sites`, `/api/v1/menus`, `/api/v1/search` | 사이트·메뉴·통합검색 |
| SPEC-CMS-MEDIA-001 | `/api/v1/media` | 미디어 |

### 5.2 백엔드 API 게이트

본 SPEC 진입 시 위 9개 SPEC이 모두 `Tested` 상태인지 확인 필수. 임의의 SPEC이 `Implemented` 이하면 본 SPEC RED 단계에서 mock 응답으로 대체하고 GREEN 단계 진입 전 백엔드 완료 대기.

### 5.3 백엔드 API 누락 또는 변경 시 대응

- 응답 스키마 변경 발견 시: `frontend/public/src/types/` 갱신 + 컴파일 에러 점검 + 영향 페이지 회귀.
- 신규 필드 필요 시: 본 SPEC은 신규 백엔드 엔드포인트 정의 비범위. 별도 SPEC(예: SPEC-CMS-003 v0.7) 추가 또는 본 SPEC 보강.

---

## 6. 위험 및 완화 방안 (요약 — 상세는 spec.md §10)

| ID | 위험 | Phase | 완화 |
|----|------|-------|------|
| RP-01 | SEO 1차 CSR 한계 | 후속 | prerender + 동적 메타 |
| RP-02 | KWCAG AA 미충족 | Phase 5 | axe-core + Lighthouse + 외부 인증 |
| RP-03 | i18n 누락 문자열 | 전 Phase | ESLint no-raw-text + 누락 검증 스크립트 |
| RP-04 | 백엔드 응답 스키마 변경 | 전 Phase | 타입 동기화 + TypeScript 컴파일 게이트 |
| RP-05 | 점검 가드 무한 루프 | Phase 0/4 | `/maintenance`, `/error/*` 가드 제외 |
| RP-06 | 다중 탭 토큰 동시성 | Phase 4 | LocalStorage `storage` 이벤트 + 락 |
| RP-07 | 검색 PER-003 미달 | Phase 3 | 디바운스 + 로딩 + 백엔드 FTS 의존 |
| RP-08 | 모바일 브라우저 호환 | Phase 6 | Vite legacy plugin + 회귀 매트릭스 |
| RP-09 | 차트 라이브러리 미선정 | Phase 0 | Phase 0 1차 결정 |
| RP-10 | 외부 URL 보안 | Phase 2 | `rel="noopener noreferrer"` + 도메인 표시 |

---

## 7. Definition of Done (전체 SPEC 완료 기준)

본 SPEC은 다음 조건을 모두 충족할 때 `Implemented` → `Tested` 전환한다.

**기능 완성도**:

- [ ] 25개 라우트 모두 진입 가능 (404 미발생)
- [ ] P0 18개 페이지 인수기준 시나리오 통과 (acceptance.md A·B·C·D·F 그룹 = 약 37개)
- [ ] P1 5개 페이지 인수기준 시나리오 통과 (acceptance.md E 그룹 8개 포함)
- [ ] 모든 API 호출이 실제 백엔드(스테이징 환경)에서 정상 동작 — Tested SPEC API 모두 통과

**품질 게이트 (TRUST 5 + 본 SPEC 추가)**:

- [ ] Vitest 커버리지 ≥ 85% (lines/functions/branches)
- [ ] vue-tsc 0 errors
- [ ] ESLint 0 errors (warnings은 별도 트리거)
- [ ] axe-core 0 critical 위반 (전 페이지)
- [ ] Lighthouse Accessibility ≥ 95 (5개 대표 페이지)
- [ ] Lighthouse Performance ≥ 80 (홈, 검색)
- [ ] 번들 크기 initial JS gzip ≤ 300KB
- [ ] i18n ko/en 메시지 키 누락 0건
- [ ] 모든 외부 링크 `rel="noopener noreferrer"` 검증

**접근성 인증**:

- [ ] 키보드 단독 조작 전수 검증 (Tab 순회 + Enter/Space 동작)
- [ ] 스크린리더 (NVDA Windows 1회 + VoiceOver macOS 1회) 주요 5페이지 음성 출력 확인
- [ ] 200% 확대 시 가로 스크롤 미발생 전수 검증
- [ ] 색대비 4.5:1 (본문) / 3:1 (큰 텍스트) 자동 검증
- [ ] (선택) 외부 인증기관 KWCAG 2.2 AA 사전 점검

**문서**:

- [ ] `frontend/public/README.md` 개발 가이드 갱신
- [ ] CHANGELOG 본 SPEC 항목 추가 (sync 단계)
- [ ] SPEC 상태 Draft → Implemented → Tested 전이 이력 기록

---

## 8. 후속 SPEC 후보 (Out of Scope — 별도 SPEC 추진)

| 후보 SPEC ID | 주제 | 우선순위 |
|-------------|------|---------|
| SPEC-CMS-PUBLIC-002 | 시민 회원가입·계정 관리 | P1 |
| SPEC-CMS-SSO-001 | 상급기관 SSO 어댑터 (RFP SFR-010) | P0 (별도 트랙) |
| SPEC-CMS-PUBLIC-SSR-001 | Vite SSR 또는 Nuxt 마이그레이션 (SEO 보강) | P1 |
| SPEC-CMS-PUBLIC-COMMENT-001 | 공공 측 댓글 작성(reCAPTCHA + 회원 인증) | P2 |
| SPEC-CMS-PUBLIC-PWA-001 | PWA / Service Worker / 오프라인 모드 | P3 |
| SPEC-CMS-SIM-001 | 가상 시뮬레이션 (RFP SFR-003) | P1 |
| SPEC-CMS-PUBLIC-DARKMODE-001 | 다크 모드 | P3 |
| SPEC-CMS-PUBLIC-KAKAO-001 | 카카오 알림톡 가입 UI | P2 |
| SPEC-CMS-PUBLIC-SURVEY-001 | 설문조사 응답 화면 | P2 |
| SPEC-CMS-PUBLIC-A11Y-EXT-001 | 추가 언어 (ja/zh) + 음성 안내 | P3 |
