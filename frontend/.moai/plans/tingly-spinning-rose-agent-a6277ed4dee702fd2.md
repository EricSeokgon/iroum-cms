# SPEC-CMS-PUBLIC-001 구현 계획 (Implementation Plan)

> 시민 대상 공공 사이트 SPA(`frontend/public/`) — Vue 3.5 + TS + Vite + Pinia + Vue Router + Element Plus + Tailwind + vue-i18n
> 25개 라우트 / 6개 인수기준 그룹(45 시나리오) / KWCAG 2.2 AA P0 게이트
> 백엔드(SPEC-CMS-002~010 + MEDIA-001)는 모두 Tested — 신규 API 정의 없음

---

## 0. 주요 가정 및 결정 (Assumptions & Key Decisions)

작업 진입 전 명시적으로 확정해야 하는 가정과 결정 사항:

### 0.1 가정 (사용자 확인 필요 시 AskUserQuestion 사용)

| # | 가정 | 신뢰도 | 위험 시 영향 |
|---|------|--------|--------------|
| A1 | `@iroum/shared/api/client.ts`(admin 사용)는 재사용하지 않고, public 전용 axios 인스턴스를 새로 작성한다. SPEC §5.3 요구사항(익명 기본·401 조건부 리다이렉트·503 maintenance redirect)이 admin과 다르기 때문. | 높음 | 잘못되면 코드 중복(~150 lines) 발생 또는 shared client 확장 필요 |
| A2 | 차트 라이브러리는 ECharts 5.5.1 + vue-echarts 7.0.3 (admin과 동일). public/package.json에 신규 의존성으로 추가한다. | 높음 | 다른 선택 시 Phase 3 일정 영향 (Chart.js로 대체 가능) |
| A3 | DOMPurify를 신규 의존성으로 추가 (검색 결과 snippet·게시글 본문 XSS 심층 방어). | 중간 | 미추가 시 v-html 보안 게이트 미달 |
| A4 | `useSeoMeta()` 컴포저블은 자체 구현(약 30 lines, `document.title` + `document.querySelector('meta[...]')` 직접 조작). `@unhead/vue` 의존성 추가 회피(YAGNI). | 중간 | SSR 도입 시 후속 SPEC에서 @unhead/vue로 교체 |
| A5 | 기존 `frontend/public/src/{router/index.ts, stores/auth.ts, composables/useApi.ts, i18n.ts, App.vue, views/HomeView.vue, views/NotFoundView.vue, locales/{ko,en}.json, main.ts, assets/main.css}`는 placeholder 수준이므로 모두 재작성 또는 확장 대상. | 높음 | — (기존 코드 6개 파일, 총 123 LOC) |
| A6 | 본 SPEC은 **신규 백엔드 API를 정의하지 않으며**, SPEC-CMS-002~010 + MEDIA-001의 Tested 상태 API만 소비한다. 모든 라우트/페이지는 mock 없이 실제 백엔드 호출로 검증한다. | 높음 | API 스키마 변경 시 별도 SPEC 필요 |
| A7 | E2E 테스트(Playwright)는 1차 출시에서 단위 테스트(Vitest + @vue/test-utils)로 대체 가능하며, 본격 도입은 2차 트랙으로 분리한다(SPEC §8 + acceptance.md DoD). `[PW]` 표시 시나리오는 Vitest로 키보드·라우터 흐름을 검증. | 중간 | Playwright 즉시 도입 시 Phase 5 범위 확대 |
| A8 | i18n locale 추가 시 메뉴 트리 강제 reload 동작(SPEC §5.4)은 백엔드 `GET /api/v1/menus`가 `Accept-Language` 또는 `?lang=` 파라미터로 다국어 라벨을 응답한다고 가정. 응답 형식 미확인 시 SPEC-CMS-010 확인 필요. | 중간 | 백엔드가 i18n 미지원 시 클라이언트 측 i18n 키 매핑 대안 필요 |

### 0.2 핵심 기술 결정

| 결정 | 채택 안 | 사유 |
|------|---------|------|
| 차트 라이브러리 | **ECharts 5.5.1 + vue-echarts 7.0.3** | admin SPA와 일관성 + 데이터 테이블 fallback 구현 용이 + 접근성 옵션 풍부 |
| API 클라이언트 | **public 전용 신규 axios 인스턴스** (`frontend/public/src/api/client.ts`) | SPEC §5.3 익명 기본 + 401 조건부 리다이렉트 + 503 maintenance 핸들링이 admin과 다름 |
| SEO 메타 | **자체 구현 `useSeoMeta()`** | YAGNI — `@unhead/vue` 의존성 회피. SSR 도입 시 후속에서 교체 |
| XSS 방어 | **DOMPurify** 클라이언트 심층 방어 | v-html 렌더링 영역(공지 본문, 검색 snippet, FAQ 답변) 보안 게이트 |
| 라우트 가드 순서 | **maintenance → auth → i18n → (afterEach) title → scroll** | SPEC §4.3 명시 + 무한 루프 방지(가드 자체에서 `/maintenance`·`/error/*` 통과) |
| 인증 토큰 저장 | **LocalStorage** (`accessToken`/`refreshToken`만, `user`는 메모리) | SPEC §7.5 명시. HttpOnly cookie + CSRF token 전환은 후속 |
| 테스트 도구 | **Vitest + @vue/test-utils + jest-axe** (단위·컴포넌트·접근성). Playwright는 2차. | acceptance.md의 [VT]/[VT+AXE] 시나리오 우선 자동화. [PW]는 단위 대체 가능 (DoD 조건) |

---

## 1. 범위 요약 (Scope Summary)

### 1.1 산출물 카운트

| 분류 | 신규 작성 파일 수 | 비고 |
|------|-----------------|------|
| Views (페이지) | **28** | 라우트 25개 + 에러 3개. HomeView/NotFoundView는 placeholder 재작성 |
| Components | **22** | nav 4 + common 7 + notice 2 + policy 3 + safety 2 + search 2 + stats 1 + 기타 1 (BackToTop) |
| Layouts | **1** | PublicLayout.vue |
| Stores (Pinia) | **6** | auth(확장) + menu + maintenance + search + locale + breadcrumb |
| API clients | **13** | client(axios base) + 12 도메인 (auth, notice, board, faq, qna, publication, policy, safety, dashboard, media, search, menu, system) |
| Composables | **7** | useApi(확장) + useDebounce + useMenuTree + usePagination + useBreadcrumb + useSeoMeta + useFocusTrap |
| Types (DTO) | **~10** | 도메인별 TypeScript 인터페이스 (Notice, Post, Policy, ...) |
| Locales | **2** (갱신) | ko.json + en.json (각 ~500~1000 키) |
| Router | **1** (재작성) | router/index.ts — 25라우트 + 4가드 |
| Assets/CSS | **1** (확장) | assets/main.css — Tailwind 베이스 + 디자인 토큰 |
| 환경/설정 | **2** | env.d.ts 확장 + tailwind.config.js (없으면 신규) |
| 테스트 (단위/컴포넌트) | **~50** | 라우터 가드 + 스토어 액션 + 핵심 view + 모든 공용 컴포넌트 + 인수기준 매핑 |
| **합계 (소스)** | **~90 파일** | + 테스트 ~50 파일 = **약 140 파일** |
| **공공 package.json 신규 의존성** | **3** | echarts, vue-echarts, dompurify (+ devDep: jest-axe, @types/dompurify) |

### 1.2 Phase 요약

| Phase | 우선순위 | 핵심 산출물 | 인수기준 그룹 | 병렬화 가능 |
|-------|---------|------------|--------------|------------|
| **Phase 0** Foundation | P0 (Critical Path) | 레이아웃·라우터 가드·API 클라이언트·스토어·컴포저블·i18n 골격 | (없음 — 인프라) | ❌ 모든 후속 Phase 차단 |
| **Phase 1** Core Pages (공지/게시판/FAQ/Q&A) | P0 | HomeView + 공지·게시판·FAQ·Q&A 8 페이지 | A·B 그룹 (16 시나리오) | ❌ Phase 0 후 진입 |
| **Phase 2** Policy & Safety & Publication | P0 | 정책·안전·발간자료 9 페이지 | C 그룹 (8 시나리오) | ✅ Phase 1과 병렬 (다른 도메인) |
| **Phase 3** Search & Stats & Media | P0 검색 / P1 통계·미디어 | 검색·통계·미디어·사이트맵 4 페이지 | D 그룹 (6 시나리오) | ✅ Phase 1·2와 일부 병렬 |
| **Phase 4** Auth & Maintenance & Errors | P0 에러 / P1 인증 페이지 | 로그인·내Q&A·점검·403·500 5 페이지 | F 그룹 (7 시나리오) | ✅ Phase 0 직후 시작 가능 |
| **Phase 5** A11y & i18n Polish | P0 (KWCAG 게이트) | axe-core 통합 + ko/en 전수 검증 + 200% 확대 회귀 | E 그룹 (8 시나리오) | 🔄 Phase 1+ 부터 continuous |
| **Phase 6** Performance & Build | P1 | 번들 분석·코드 스플리팅·Lighthouse CI | (성능 게이트) | ❌ 최종 Phase |

### 1.3 핵심 위험 (Top 5)

| # | 위험 | Phase | 1차 완화 |
|---|------|-------|---------|
| R1 | **KWCAG 2.2 AA 미충족** → 정부 사이트 출시 차단 | Phase 5 | jest-axe를 Phase 1부터 모든 view 테스트에 통합 (사후 검증이 아닌 동시 검증) |
| R2 | **i18n raw 문자열 누락** → 영어 토글 시 한국어 표시 | 전 Phase | ESLint `@intlify/vue-i18n/no-raw-text` 규칙 Phase 0 도입 + CI 게이트 |
| R3 | **점검 가드 무한 루프** → 사이트 진입 불가 | Phase 0 | 라우터 가드 단위 테스트에 `/maintenance`·`/error/*` 통과 케이스 명시 (acceptance F-04) |
| R4 | **다중 탭 토큰 동시성** → 401 다발 | Phase 4 | LocalStorage `storage` 이벤트 + refresh 단일 락 (단위 테스트로 격리) |
| R5 | **번들 크기 > 300KB** → LCP 게이트 미달 | Phase 6 | Phase 0에서 라우트 단위 dynamic import 강제 + ECharts/Element Plus vendor 분리 |

---

## 2. 태스크 분해 (Atomic Tasks)

> 25개 라우트 + 인프라를 10개 그룹 태스크로 분해. 각 태스크는 단일 DDD/TDD 사이클(또는 명확히 분리된 다중 사이클)로 완료 가능한 단위.

### Phase 0 — Foundation

#### T-001. 인프라 셋업 (P0)
- **설명**: 패키지 의존성 추가, 빌드 설정 갱신, 디자인 토큰 정의.
- **수정/생성 파일**:
  - `frontend/public/package.json` (수정 — echarts, vue-echarts, dompurify, jest-axe, @intlify/eslint-plugin-vue-i18n 추가)
  - `frontend/public/tailwind.config.js` (확인/생성 — breakpoint·color tokens)
  - `frontend/public/postcss.config.js` (확인/생성)
  - `frontend/public/src/env.d.ts` (확장 — VITE_API_BASE_URL, VITE_PUBLIC_SITE_CODE 타입)
  - `frontend/public/src/assets/main.css` (확장 — Tailwind directives + 디자인 토큰 CSS 변수)
  - `frontend/public/.eslintrc.cjs` 또는 `eslint.config.js` (i18n no-raw-text 룰 추가)
- **의존성**: (없음 — 진입점)
- **우선순위**: P0
- **검증**: `pnpm --filter @iroum-cms/public install && pnpm --filter @iroum-cms/public build` 성공

#### T-002. API 클라이언트 + 도메인 모듈 (P0)
- **설명**: 공공 사이트 전용 axios 인스턴스 + 4가지 인터셉터(요청 토큰·401 refresh·503 maintenance·5xx fallback) + 12개 도메인 API 클라이언트 모듈 + DTO 타입.
- **수정/생성 파일**:
  - `frontend/public/src/api/client.ts` (신규 — 약 150 lines)
  - `frontend/public/src/api/{auth,notice,board,faq,qna,publication,policy,safety,dashboard,media,search,menu,system}.ts` (신규 13개)
  - `frontend/public/src/types/{notice,post,faq,qna,publication,policy,safety,menu,search,kpi,media}.ts` (신규 ~10개)
- **의존성**: T-001
- **우선순위**: P0
- **검증**: `vue-tsc -b` 빌드 0 errors + 인터셉터 단위 테스트(401 redirect 조건부 + 503 redirect + 토큰 첨부) 통과

#### T-003. Pinia 스토어 6종 (P0)
- **설명**: auth(기존 확장), menu, maintenance, search, locale, breadcrumb 스토어 작성. LocalStorage 영속화(auth 토큰·locale·search history). 60초 캐시(menu·maintenance).
- **수정/생성 파일**:
  - `frontend/public/src/stores/auth.ts` (확장 — login/logout/refresh + LocalStorage)
  - `frontend/public/src/stores/{menu,maintenance,search,locale,breadcrumb}.ts` (신규 5개)
- **의존성**: T-002 (API 클라이언트 사용)
- **우선순위**: P0
- **검증**: 6개 스토어 액션 단위 테스트 통과 (각 store 평균 4~6 케이스)

#### T-004. 라우터 + 4종 가드 + 라우트 메타 (P0)
- **설명**: 25개 라우트 정의(동적 import) + maintenance/auth/i18n/title 가드 + scrollBehavior.
- **수정/생성 파일**:
  - `frontend/public/src/router/index.ts` (재작성 — 약 250 lines, 25 라우트 + 4 가드)
- **의존성**: T-003 (auth + maintenance store)
- **우선순위**: P0
- **검증**: 4종 가드 단위 테스트 (점검 모드 강제 리다이렉트, requiresAuth 리다이렉트, i18n query 처리, title 갱신 — acceptance F-04/B-07 매핑)

#### T-005. PublicLayout + 공용 컴포넌트 + 컴포저블 + i18n 골격 (P0)
- **설명**: PublicLayout(헤더+브레드크럼+main+푸터+스킵내비+백투탑) + 4 nav 컴포넌트 + 7 common 컴포넌트 + 7 composables + ko/en 100키 골격(메뉴·공용 액션·에러).
- **수정/생성 파일**:
  - `frontend/public/src/layouts/PublicLayout.vue` (신규)
  - `frontend/public/src/components/nav/{PublicHeader,PublicFooter,PublicBreadcrumb,PublicSkipNav}.vue` (신규 4)
  - `frontend/public/src/components/common/{PageHeader,PaginationBar,SearchInput,EmptyState,ErrorState,LoadingState,AttachmentDownload,BackToTopButton}.vue` (신규 8)
  - `frontend/public/src/composables/{useApi,useDebounce,useMenuTree,usePagination,useBreadcrumb,useSeoMeta,useFocusTrap}.ts` (useApi 확장 + 6 신규)
  - `frontend/public/src/locales/{ko,en}.json` (확장 — 100개 골격 키: nav.*, common.*, error.*)
  - `frontend/public/src/App.vue` (수정 — `<RouterView />` + 글로벌 toast 호스트)
- **의존성**: T-001, T-003, T-004
- **우선순위**: P0
- **검증**: PublicLayout 렌더링 + 메뉴 트리 로드 + 스킵 내비 동작 + 햄버거 메뉴 + 백투탑 (acceptance A-01~A-08 8 시나리오) 통과

---

### Phase 1 — Core Pages (공지·게시판·FAQ·Q&A)

#### T-006. 공지·게시판·FAQ·Q&A 도메인 페이지 (P0)
- **설명**: 8개 view + 도메인 컴포넌트 + DOMPurify 통합 + 첨부 다운로드(서명 URL) 흐름.
- **수정/생성 파일**:
  - `views/HomeView.vue` (재작성 — 히어로+공지+정책+빠른링크+KPI 5섹션, `Promise.allSettled` 부분 실패 허용)
  - `views/AboutView.vue` (신규 — 정적 콘텐츠, P0 단순)
  - `views/notices/{NoticeListView,NoticeDetailView}.vue` (신규 2)
  - `views/boards/{BoardPostListView,BoardPostDetailView}.vue` (신규 2 — 마스터 타입별 레이아웃 분기)
  - `views/FaqView.vue` (신규 — 아코디언)
  - `views/qnas/{QnaListView,QnaDetailView,QnaCreateView}.vue` (신규 3)
  - `components/notice/{NoticeCard,NoticeContent}.vue` (신규 2)
  - `locales/{ko,en}.json` (확장 — notice.*, board.*, faq.*, qna.* 약 150 키)
- **의존성**: T-002~T-005
- **우선순위**: P0
- **검증**: acceptance B-01~B-08 8 시나리오 통과 + jest-axe 0 critical (각 페이지)

---

### Phase 2 — Policy & Safety & Publication (Phase 1과 병렬)

#### T-007. 정책·안전·발간자료 도메인 페이지 (P0)
- **설명**: 9개 view + 정책 매칭 폼 + 외부 신청 URL 안전 검증 + 안전 체크리스트 + 다중 첨부 zip 다운로드.
- **수정/생성 파일**:
  - `views/policies/{PolicyListView,PolicyDetailView,PolicyMatchView,PolicySubscriptionView}.vue` (신규 4 — Subscription은 P1)
  - `views/safety/{SafetyGuidelineListView,SafetyGuidelineDetailView,SafetyIncidentListView}.vue` (신규 3)
  - `views/publications/{PublicationListView,PublicationDetailView}.vue` (신규 2)
  - `components/policy/{PolicyCard,PolicyFilterBar,PolicyMatchForm}.vue` (신규 3)
  - `components/safety/{SafetyChecklist,IncidentCard}.vue` (신규 2)
  - `locales/{ko,en}.json` (확장 — policy.*, safety.*, publication.* 약 150 키)
- **의존성**: T-002~T-005 (Phase 1과 병렬 가능)
- **우선순위**: P0 (PolicySubscriptionView만 P1)
- **검증**: acceptance C-01~C-08 8 시나리오 통과 + 외부 URL 안전성 검증 단위 테스트 (`javascript:`, `data:`, `file:` 차단) + jest-axe 0 critical

---

### Phase 3 — Search & Stats & Media (Phase 2와 일부 병렬)

#### T-008. 검색·통계·미디어·사이트맵 (P0 검색 / P1 통계·미디어) (P0/P1 혼합)
- **설명**: 통합 검색(타입 탭 + 하이라이트) + 공개 KPI 차트(ECharts + 데이터 테이블 fallback) + 미디어 갤러리(lazy load) + 사이트맵 트리.
- **수정/생성 파일**:
  - `views/SearchResultView.vue` (신규 — P0)
  - `views/PublicStatsView.vue` (신규 — P1, ECharts 첫 도입)
  - `views/MediaGalleryView.vue` (신규 — P1)
  - `views/SitemapView.vue` (신규 — P0)
  - `components/search/{SearchResultCard,SearchFilterTabs}.vue` (신규 2)
  - `components/stats/KpiChart.vue` (신규 — ECharts 래퍼 + `<table>` fallback)
  - `locales/{ko,en}.json` (확장 — search.*, stats.*, media.*, sitemap.* 약 100 키)
- **의존성**: T-002~T-005 + ECharts 의존성(T-001에서 추가)
- **우선순위**: P0 검색·사이트맵 / P1 통계·미디어
- **검증**: acceptance D-01~D-06 6 시나리오 통과 + KpiChart 데이터 테이블 fallback 키보드 접근 검증 + 검색 응답 < 3s (PER-003 수동 측정)

---

### Phase 4 — Auth & Maintenance & Errors

#### T-009. 로그인·내Q&A·점검·에러 페이지 (P0 에러 / P1 인증) (P0/P1 혼합)
- **설명**: 로그인(noLayout 미니멀) + 내 Q&A + 점검 안내(5분 자동 새로고침) + 403/500 에러 페이지 + 다중 탭 토큰 동기화(LocalStorage `storage` 이벤트).
- **수정/생성 파일**:
  - `views/LoginView.vue` (신규)
  - `views/qnas/MyQnaListView.vue` (신규 — P1)
  - `views/MaintenanceView.vue` (신규)
  - `views/errors/{ForbiddenView,ServerErrorView}.vue` (신규 2)
  - `views/NotFoundView.vue` (재작성 — 404 더 친화적)
  - `stores/auth.ts` (보강 — LocalStorage `storage` 이벤트 리스너로 다중 탭 동기화)
  - `locales/{ko,en}.json` (확장 — auth.*, maintenance.*, error.* 약 50 키)
- **의존성**: T-002~T-005 (Phase 0 후 Phase 1·2와 병렬 가능)
- **우선순위**: P0 에러 페이지 / P1 LoginView·MyQnaListView (라우트는 존재하나 인증 가드 없는 라우트 우선 검증 가능)
- **검증**: acceptance F-01~F-07 7 시나리오 통과 + 다중 탭 토큰 동기화 수동 검증 + 점검 모드 가드 무한 루프 회귀 테스트

---

### Phase 5 — Accessibility & i18n Polish (Phase 1+ continuous)

#### T-010. KWCAG 2.2 AA 게이트 통과 + ko/en 전수 검증 (P0)
- **설명**: axe-core 통합 + 키보드 순회 + 색대비 + 폼 라벨 + 200% 확대 회귀 + i18n 누락 검증 스크립트 + Lighthouse Accessibility ≥ 95.
- **수정/생성 파일**:
  - `tests/setup-axe.ts` (신규 — jest-axe 설정 + Vitest 전역)
  - `tests/a11y/` (신규 디렉터리 — 페이지별 axe 검사 5+ 케이스)
  - `scripts/check-i18n-coverage.ts` (신규 — ko↔en 키 누락 검출 스크립트, CI 게이트)
  - `.lighthouseci.json` 또는 `lighthouse.config.js` (신규 — 5 페이지 임계값)
  - 누락 발견 시 `components/`·`views/`의 ARIA 속성 보강 (소규모 다중 파일 편집)
- **의존성**: Phase 1·2·3·4 진행 중 continuous, Phase 6 진입 전 통과 필수
- **우선순위**: P0 (RFP SFR-009 + KWCAG 정부 사이트 요건)
- **검증**: acceptance E-01~E-08 8 시나리오 통과 + Lighthouse Accessibility ≥ 95 (5개 대표 페이지) + axe-core 0 critical 전수 + ko/en 키 누락 0

---

### Phase 6 — Performance & Build

#### (Phase 5와 통합하지 않고 별도 — 성능 게이트는 P1이지만 SPEC §7.6 명시)
> 본 SPEC는 10 태스크 한계 내에서 Phase 6 작업을 T-010의 마지막 단계로 포함 가능. 그러나 명확성을 위해 별도 표기:

#### T-010 보강 / 또는 T-011 (Phase 6 — 시간 여유 시): 성능 최적화
- **설명**: 번들 분석 + 라우트 코드 스플리팅 검증 + Vite manualChunks(vue-router-pinia, element-plus, echarts 분리) + Lighthouse Performance 게이트 + Vitest 커버리지 ≥ 85%.
- **수정/생성 파일**:
  - `frontend/public/vite.config.ts` (확장 — manualChunks, build options)
  - `tests/__perf__/` 또는 Lighthouse CI 통합
  - `frontend/public/README.md` (신규/확장 — 개발 가이드)
- **검증**: Lighthouse Performance ≥ 80 (홈, 검색) + initial JS gzip ≤ 300KB + Vitest 커버리지 ≥ 85%

---

### 태스크 카운트 검증

| 태스크 ID | 설명 요약 | Phase |
|----------|----------|-------|
| T-001 | 인프라 셋업 (deps, Tailwind, ESLint) | 0 |
| T-002 | API 클라이언트 + 13 도메인 모듈 + DTO | 0 |
| T-003 | 6 Pinia 스토어 | 0 |
| T-004 | 라우터 + 4 가드 | 0 |
| T-005 | PublicLayout + 12 공용 컴포넌트 + 7 컴포저블 + i18n 골격 | 0 |
| T-006 | 공지·게시판·FAQ·Q&A 8 페이지 + 2 컴포넌트 | 1 |
| T-007 | 정책·안전·발간자료 9 페이지 + 5 컴포넌트 | 2 |
| T-008 | 검색·통계·미디어·사이트맵 4 페이지 + 3 컴포넌트 | 3 |
| T-009 | 로그인·내Q&A·점검·에러 5 페이지 + 다중 탭 동기화 | 4 |
| T-010 | A11y + i18n + 성능 게이트 (Phase 5 + 6 통합) | 5+6 |

**합계: 10 atomic tasks** (SPEC 한계 준수)

---

## 3. 파일 목록 (Planned Files — 전체)

### 3.1 소스 파일

```
frontend/public/
├── package.json                              [수정] +echarts +vue-echarts +dompurify +jest-axe +@intlify/eslint-plugin-vue-i18n
├── tailwind.config.js                        [확인/생성]
├── postcss.config.js                         [확인/생성]
├── eslint.config.js                          [확인/확장] +vue-i18n no-raw-text
├── vite.config.ts                            [확장] manualChunks (Phase 6)
├── README.md                                 [신규/확장] 개발 가이드 (Phase 6)
└── src/
    ├── env.d.ts                              [확장] VITE_* 타입
    ├── main.ts                               (변경 없음 — Pinia/Router/i18n/ElementPlus 등록 완료)
    ├── App.vue                               [수정] <RouterView /> + 글로벌 toast 호스트
    ├── i18n.ts                               (변경 없음)
    ├── assets/
    │   └── main.css                          [확장] Tailwind + 디자인 토큰 CSS 변수
    ├── api/
    │   ├── client.ts                         [신규] axios 인스턴스 + 4 인터셉터 (~150 lines)
    │   ├── auth.ts                           [신규]
    │   ├── notice.ts                         [신규]
    │   ├── board.ts                          [신규]
    │   ├── faq.ts                            [신규]
    │   ├── qna.ts                            [신규]
    │   ├── publication.ts                    [신규]
    │   ├── policy.ts                         [신규]
    │   ├── safety.ts                         [신규]
    │   ├── dashboard.ts                      [신규]
    │   ├── media.ts                          [신규]
    │   ├── search.ts                         [신규]
    │   ├── menu.ts                           [신규]
    │   └── system.ts                         [신규]
    ├── types/
    │   ├── notice.ts                         [신규]
    │   ├── post.ts                           [신규]
    │   ├── faq.ts                            [신규]
    │   ├── qna.ts                            [신규]
    │   ├── publication.ts                    [신규]
    │   ├── policy.ts                         [신규]
    │   ├── safety.ts                         [신규]
    │   ├── menu.ts                           [신규]
    │   ├── search.ts                         [신규]
    │   ├── kpi.ts                            [신규]
    │   └── media.ts                          [신규]
    ├── stores/
    │   ├── auth.ts                           [재작성] login/logout/refresh + LocalStorage + 다중 탭
    │   ├── menu.ts                           [신규]
    │   ├── maintenance.ts                    [신규]
    │   ├── search.ts                         [신규]
    │   ├── locale.ts                         [신규]
    │   └── breadcrumb.ts                     [신규]
    ├── router/
    │   └── index.ts                          [재작성] 25 라우트 + 4 가드
    ├── layouts/
    │   └── PublicLayout.vue                  [신규]
    ├── components/
    │   ├── nav/
    │   │   ├── PublicHeader.vue              [신규]
    │   │   ├── PublicFooter.vue              [신규]
    │   │   ├── PublicBreadcrumb.vue          [신규]
    │   │   └── PublicSkipNav.vue             [신규]
    │   ├── common/
    │   │   ├── PageHeader.vue                [신규]
    │   │   ├── PaginationBar.vue             [신규]
    │   │   ├── SearchInput.vue               [신규]
    │   │   ├── EmptyState.vue                [신규]
    │   │   ├── ErrorState.vue                [신규]
    │   │   ├── LoadingState.vue              [신규]
    │   │   ├── AttachmentDownload.vue        [신규]
    │   │   └── BackToTopButton.vue           [신규]
    │   ├── notice/
    │   │   ├── NoticeCard.vue                [신규]
    │   │   └── NoticeContent.vue             [신규]
    │   ├── policy/
    │   │   ├── PolicyCard.vue                [신규]
    │   │   ├── PolicyFilterBar.vue           [신규]
    │   │   └── PolicyMatchForm.vue           [신규]
    │   ├── safety/
    │   │   ├── SafetyChecklist.vue           [신규]
    │   │   └── IncidentCard.vue              [신규]
    │   ├── search/
    │   │   ├── SearchResultCard.vue          [신규]
    │   │   └── SearchFilterTabs.vue          [신규]
    │   └── stats/
    │       └── KpiChart.vue                  [신규]
    ├── composables/
    │   ├── useApi.ts                         [확장] 에러 통합 + 재시도 (현재 41 lines → ~80 lines)
    │   ├── useDebounce.ts                    [신규]
    │   ├── useMenuTree.ts                    [신규]
    │   ├── usePagination.ts                  [신규]
    │   ├── useBreadcrumb.ts                  [신규]
    │   ├── useSeoMeta.ts                     [신규] (자체 구현, @unhead/vue 미도입)
    │   └── useFocusTrap.ts                   [신규]
    ├── locales/
    │   ├── ko.json                           [확장] ~500~1000 키
    │   └── en.json                           [확장] ~500~1000 키
    └── views/
        ├── HomeView.vue                      [재작성] 5섹션 Promise.allSettled
        ├── AboutView.vue                     [신규] 정적
        ├── NotFoundView.vue                  [재작성] 친화적 메시지
        ├── notices/
        │   ├── NoticeListView.vue            [신규]
        │   └── NoticeDetailView.vue          [신규]
        ├── boards/
        │   ├── BoardPostListView.vue         [신규]
        │   └── BoardPostDetailView.vue       [신규]
        ├── FaqView.vue                       [신규]
        ├── qnas/
        │   ├── QnaListView.vue               [신규]
        │   ├── QnaDetailView.vue             [신규]
        │   ├── QnaCreateView.vue             [신규]
        │   └── MyQnaListView.vue             [신규]
        ├── publications/
        │   ├── PublicationListView.vue       [신규]
        │   └── PublicationDetailView.vue     [신규]
        ├── policies/
        │   ├── PolicyListView.vue            [신규]
        │   ├── PolicyDetailView.vue          [신규]
        │   ├── PolicyMatchView.vue           [신규]
        │   └── PolicySubscriptionView.vue    [신규]
        ├── safety/
        │   ├── SafetyGuidelineListView.vue   [신규]
        │   ├── SafetyGuidelineDetailView.vue [신규]
        │   └── SafetyIncidentListView.vue    [신규]
        ├── PublicStatsView.vue               [신규]
        ├── MediaGalleryView.vue              [신규]
        ├── SearchResultView.vue              [신규]
        ├── SitemapView.vue                   [신규]
        ├── MaintenanceView.vue               [신규]
        ├── LoginView.vue                     [신규] noLayout
        └── errors/
            ├── ForbiddenView.vue             [신규]
            └── ServerErrorView.vue           [신규]
```

### 3.2 테스트 파일 (단위·컴포넌트·접근성)

```
frontend/public/tests/
├── setup-axe.ts                              [신규] jest-axe Vitest 통합
├── api/
│   ├── client.spec.ts                        [신규] 인터셉터 4종 (요청·401·503·5xx)
│   └── 도메인별 client 테스트는 선택 (mock-adapter 사용 시)
├── stores/
│   ├── auth.spec.ts                          [신규] login/logout/refresh + LocalStorage
│   ├── menu.spec.ts                          [신규] 로드 + 60초 캐시 + locale watcher
│   ├── maintenance.spec.ts                   [신규]
│   ├── search.spec.ts                        [신규] 히스토리 LocalStorage
│   ├── locale.spec.ts                        [신규] 시스템 언어 감지 + LocalStorage
│   └── breadcrumb.spec.ts                    [신규] 정적+동적 합성
├── router/
│   ├── guards.spec.ts                        [신규] 4가드 (maintenance·auth·i18n·title) — F-04, B-07 매핑
│   └── scroll.spec.ts                        [신규] 스크롤 복원·hash 점프
├── layouts/
│   └── PublicLayout.spec.ts                  [신규] 랜드마크 4개 + 스킵내비 (A-01, A-04)
├── components/
│   ├── nav/
│   │   ├── PublicHeader.spec.ts              [신규] 언어 토글 + 햄버거 (A-05, A-07)
│   │   ├── PublicBreadcrumb.spec.ts          [신규] aria-current (A-03)
│   │   └── PublicSkipNav.spec.ts             [신규] (A-04)
│   ├── common/
│   │   ├── PaginationBar.spec.ts             [신규]
│   │   ├── SearchInput.spec.ts               [신규] 디바운스 + 히스토리 (D-04)
│   │   ├── EmptyState.spec.ts                [신규] (F-06)
│   │   ├── ErrorState.spec.ts                [신규] (F-05)
│   │   ├── AttachmentDownload.spec.ts        [신규] 서명 URL (B-05)
│   │   └── BackToTopButton.spec.ts           [신규] (A-08)
│   ├── notice/NoticeContent.spec.ts          [신규] XSS 차단 (B-04)
│   ├── policy/
│   │   ├── PolicyFilterBar.spec.ts           [신규] (C-01)
│   │   └── PolicyMatchForm.spec.ts           [신규] 익명 (C-03)
│   ├── safety/SafetyChecklist.spec.ts        [신규] (C-05)
│   ├── search/SearchResultCard.spec.ts       [신규] mark 하이라이트 (D-02)
│   └── stats/KpiChart.spec.ts                [신규] 데이터 테이블 fallback (D-05)
├── views/
│   ├── HomeView.spec.ts                      [신규] Promise.allSettled (F-07)
│   ├── notices/NoticeListView.spec.ts        [신규] 페이징·검색·고정 (B-01~B-03)
│   ├── notices/NoticeDetailView.spec.ts      [신규]
│   ├── FaqView.spec.ts                       [신규] 아코디언 (B-06)
│   ├── qnas/QnaCreateView.spec.ts            [신규] authGuard (B-07)
│   ├── qnas/QnaDetailView.spec.ts            [신규] 비공개 404 (B-08)
│   ├── publications/PublicationDetailView.spec.ts [신규] zip 다운로드 (C-08)
│   ├── policies/PolicyDetailView.spec.ts     [신규] 외부 URL 안전 (C-02)
│   ├── policies/PolicyMatchView.spec.ts      [신규] 익명 (C-03)
│   ├── safety/SafetyIncidentListView.spec.ts [신규] 공개 필터 (C-06)
│   ├── SearchResultView.spec.ts              [신규] 타입 탭·빈 결과 (D-01, D-03)
│   ├── MediaGalleryView.spec.ts              [신규] lazy load (D-06)
│   ├── MaintenanceView.spec.ts               [신규] (F-04)
│   ├── LoginView.spec.ts                     [신규] redirect 처리
│   └── errors/ServerErrorView.spec.ts        [신규] (F-03)
├── a11y/
│   ├── home.a11y.spec.ts                     [신규] jest-axe (E-02, E-03)
│   ├── notice-list.a11y.spec.ts              [신규]
│   ├── notice-detail.a11y.spec.ts            [신규]
│   ├── policy-detail.a11y.spec.ts            [신규]
│   └── search.a11y.spec.ts                   [신규]
└── i18n/
    └── coverage.spec.ts                      [신규] ko↔en 키 누락 검출
```

> **총 테스트 파일 수**: 약 50 (인수기준 45 시나리오 중 [VT]/[VT+AXE] 표시 시나리오 자동화. [PW] 표시는 1차에서 Vitest 라우터 모킹으로 대체. [MAN]은 수동 절차로 별도 체크리스트 관리.)

### 3.3 스크립트 / CI

```
frontend/public/scripts/
└── check-i18n-coverage.ts                    [신규] ko↔en 키 누락 검출 + CI 게이트

frontend/public/
├── .lighthouseci.json 또는 lighthouserc.cjs  [신규] 5 페이지 임계값
└── 기존 CI 워크플로(레포 루트 .github/workflows/) 갱신은 본 SPEC 범위 외 — 별도 SPEC에서 처리
```

---

## 4. 기술 결정 (Technical Decisions — 확정)

### 4.1 상태 관리 (Pinia)

| 스토어 | 목적 | 영속화 | 캐시 정책 |
|--------|------|--------|----------|
| `auth` | accessToken·refreshToken·user·isAuthenticated + login/logout/refresh | LocalStorage(토큰만) | refresh 단일 락 + `storage` 이벤트로 다중 탭 |
| `menu` | 메뉴 트리 (siteCode=public) | (메모리) | 60초 TTL + locale watcher로 강제 reload |
| `maintenance` | 점검 모드 boolean | (메모리) | 60초 TTL (라우터 가드에서 호출) |
| `search` | 검색어 히스토리 최근 5개 | LocalStorage(`public.search.history`) | (없음 — 직접 조작) |
| `locale` | i18n locale (ko/en) | LocalStorage(`public.locale`) | 시스템 언어 1회 감지 |
| `breadcrumb` | 정적+동적 브레드크럼 합성 | (메모리) | 라우트 변경 시 재계산 |

### 4.2 API 클라이언트 패턴

- **베이스**: `frontend/public/src/api/client.ts` — public 전용 axios 인스턴스 (admin과 별개, 공유 client.ts 미사용).
- **인터셉터 (요청)**:
  - `auth.accessToken` 존재 시 `Authorization: Bearer {token}` 첨부 (없으면 첨부 안 함 — 익명 호출 표준)
- **인터셉터 (응답)**:
  - 401: `to.meta.requiresAuth === true` 라우트만 refresh 시도 → 실패 시 `/login?redirect=` 리다이렉트. requiresAuth=false 라우트는 401 단순 throw (컴포넌트가 처리)
  - 403: `router.push({name: 'forbidden'})`
  - 503 + `code === 'MAINTENANCE_MODE_ACTIVE'`: `router.push({name: 'maintenance'})`
  - 5xx (GET): `router.push({name: 'server-error'})` (POST/PUT/DELETE는 컴포넌트에서 토스트로 처리하도록 그대로 reject)
  - 네트워크 실패: ErrorState로 fallback (컴포넌트 측 처리)
- **baseURL**: `import.meta.env.VITE_API_BASE_URL || '/api/v1'`
- **timeout**: 15000ms

### 4.3 라우터 가드 순서 (확정)

```
beforeEach 순서:
  1. maintenanceGuard   — 점검 모드 시 /maintenance 강제 (예외: /maintenance, /error/*)
  2. authGuard           — requiresAuth=true + !isAuthenticated → /login?redirect=
  3. i18nGuard           — ?lang=ko|en 처리 후 query 제거 (replace)

afterEach:
  4. titleGuard          — document.title = `${t(to.meta.title)} | iroum-cms`

scrollBehavior:
  5. 표준 패턴 — savedPosition || hash anchor || top
```

### 4.4 레이아웃 (단일 PublicLayout)

- 단일 `PublicLayout.vue` (admin의 `AdminLayout.vue` 패턴 차용 — 그러나 admin 코드를 import하지 않음, 독립 작성)
- 슬롯: 헤더(PublicHeader) + 브레드크럼(PublicBreadcrumb) + main(`<RouterView />`) + 푸터(PublicFooter)
- 보조: PublicSkipNav (첫 포커스 가능 요소), BackToTopButton (스크롤 > 400px 시 노출)
- 라우트 메타 `noLayout: true`인 경우 (LoginView) PublicLayout 미적용 — App.vue 또는 라우트 children 구조로 분기

### 4.5 테스트 전략

| 카테고리 | 도구 | 시나리오 매핑 |
|---------|------|--------------|
| 단위·컴포넌트 | Vitest + @vue/test-utils + jsdom | [VT] 표시 — A·B·C·D·F 그룹 다수 |
| 접근성 | jest-axe (Vitest 통합) | [VT+AXE] 표시 — E 그룹 + A-04, B-06, C-05, D-05 |
| E2E (1차 단위 대체) | (Vitest router 모킹) → 2차 Playwright | [PW] 표시 시나리오는 1차에서 단위로 대체 가능 (DoD 명시) |
| 성능 | Lighthouse CI | LCP/INP/CLS/번들 크기 |
| 수동 | 체크리스트 | [MAN] 표시 — E-07 (200% 확대), 스크린리더 (NVDA + VoiceOver) |

### 4.6 i18n 메시지 네임스페이스

```
nav.*           — 메뉴 라벨
common.*        — 공용 액션 (search, login, logout, save, cancel, retry, ...)
error.*         — 에러 메시지 코드별 매핑
notice.*        — 공지 도메인
board.*         — 게시판
faq.*           — FAQ
qna.*           — Q&A
publication.*   — 발간자료
policy.*        — 정책사업
safety.*        — 안전관리
search.*        — 검색
stats.*         — 통계
media.*         — 미디어
sitemap.*       — 사이트맵
maintenance.*   — 점검
auth.*          — 로그인
```

---

## 5. 구현 순서 및 우선순위 (Critical Path)

### 5.1 순차/병렬 다이어그램

```
                    ┌──> T-006 (Phase 1: 공지/게시판/FAQ/Q&A) ─┐
T-001 → T-002 →     ├──> T-007 (Phase 2: 정책/안전/발간자료) ──┤
T-003 → T-004 →     ├──> T-008 (Phase 3: 검색/통계/미디어) ────┤──> T-010 (Phase 5+6: A11y + Perf)
T-005 (Phase 0) ─── ├──> T-009 (Phase 4: 인증/점검/에러) ───────┤
                    └────────── (T-010 a11y는 Phase 1+ continuous로 실행) ──────┘
```

### 5.2 Critical Path (직렬 부분)

**T-001 → T-002 → T-003 → T-004 → T-005** (Phase 0 직렬, 약 5 atomic tasks)

이후 T-006/T-007/T-008/T-009는 도메인 분리이므로 병렬 작업 가능.

T-010(Phase 5+6)은 Phase 1~4 완료 후 수렴.

### 5.3 병렬화 권장 (Team mode 사용 시)

| Phase | 권장 병렬 teammates | 파일 소유권 |
|-------|------------------|-----------|
| Phase 0 | (sequential — 단일 implementer) | — |
| Phase 1~4 | 최대 4 teammates 병렬 (T-006/T-007/T-008/T-009) | T-006: `views/{notices,boards,qnas}/`, `components/notice/`<br>T-007: `views/{policies,safety,publications}/`, `components/{policy,safety}/`<br>T-008: `views/{Search,Stats,Media,Sitemap}*View.vue`, `components/{search,stats}/`<br>T-009: `views/{Login,Maintenance,errors}/`, `stores/auth.ts` |
| Phase 5+6 | (수렴 — 단일 implementer + tester) | T-010 |

> **충돌 가능 파일**: `locales/{ko,en}.json` 모든 phase에서 수정 발생. 해결책: 도메인별 분리 JSON 파일(`locales/notice.ko.json` 등)로 빌드 시 merge 또는 Phase 0에서 namespace 골격 미리 잡고 각 phase는 자기 namespace만 편집.

### 5.4 진입/완료 마커

- Phase 0 진입: SPEC `Draft` → `Implemented` 전이 시작
- Phase 0 완료: `pnpm --filter @iroum-cms/public dev`로 빈 홈 + 레이아웃 정상 렌더링 (수동 검증)
- Phase 1~4 완료: 각 phase 인수기준 그룹 시나리오 100% 통과
- Phase 5+6 완료: Lighthouse Accessibility ≥ 95 + Performance ≥ 80 + 번들 ≤ 300KB + axe-core 0 critical + i18n 누락 0
- 전체 완료: SPEC `Implemented` → `Tested` 전이 (acceptance.md §DoD 6개 조건 모두 충족)

---

## 6. 비례성 검토 (Proportionality Check)

본 계획이 SPEC 범위에 적정한지 점검한 결과:

### 6.1 과도한 설계 없음 (No Over-engineering)

- ✅ **단일 PublicLayout** — 운영자 SPA처럼 복잡한 사이드바·테마 전환 미포함. SPEC §5.2 단순 헤더+main+푸터+브레드크럼 구조에 정확히 부합.
- ✅ **자체 useSeoMeta** — `@unhead/vue` 의존성 회피. CSR 1차 출시에는 `document.title` + `meta` DOM 조작 30 lines로 충분.
- ✅ **API 캐싱 미도입** — vue-query·자체 캐시는 후속 SPEC. 1차는 컴포넌트 ref + 스토어 60초 TTL만 사용.
- ✅ **댓글 작성·SSR·PWA·SSO·다크모드** — SPEC §3.2 비범위 항목 13건 명시 — 모두 본 계획에서 제외 (YAGNI 충실).
- ✅ **E2E Playwright** — 1차는 단위 테스트로 대체 (DoD 허용). 2차에서 본격 도입.
- ✅ **차트 라이브러리** — admin과 동일한 ECharts 채택 (학습·디버깅 비용 0 — 두 SPA 일관성).

### 6.2 admin SPA 패턴 재사용 (참조만, 수정 불가)

- ✅ **라우터 가드 패턴** (admin `router/index.ts`의 beforeEach·afterEach 구조) → 동일 형태로 작성 (admin 파일 수정 없음)
- ✅ **JWT 디코더 + auth 스토어 구조** (admin `stores/auth.ts`의 `decodeJwt` + `defineStore` setup) → 동일 형태로 작성
- ✅ **API client 인터셉터 패턴** (shared `api/client.ts`의 401 refresh 큐) → public client.ts에 동일 큐 로직 작성 (코드 복사 아닌 패턴 차용)
- ✅ **컴포저블 패턴** (admin의 useApi) → public/composables/useApi.ts (기존 41 lines) 확장

### 6.3 단순 대안 검토

| 영역 | 더 단순한 대안 검토 | 결정 |
|------|------------------|------|
| 13개 API 도메인 모듈 | 단일 `api/index.ts`로 통합? | ❌ 도메인 분리 + tree-shaking 이점. SPEC §5.1 명시. |
| 6개 스토어 | locale·breadcrumb를 단일 ui 스토어로? | ❌ 책임 분리 + watcher 명확성. SPEC §5.4 명시. |
| 22개 컴포넌트 | Element Plus 그대로 사용? | 🔶 일부 가능(`<el-pagination>` 등) — 그러나 PaginationBar는 aria-label 한글화 래핑 필요. SPEC §7.1 명시. |
| Phase 0 단일 implementer | 5명 병렬? | ❌ 의존성 직렬 (T-001→T-002→...→T-005). 단일 진행이 최적. |

### 6.4 YAGNI 준수 (P1 항목 처리)

P1 항목은 라우트는 정의하되 구현 깊이를 최소화:
- **PublicStatsView (P1)**: ECharts 1~2개 차트 + 데이터 테이블 fallback (요구 최소)
- **MediaGalleryView (P1)**: 그리드 + lazy load (이미지 변환·동영상 모달 등 추가 기능 없음)
- **MyQnaListView (P1)**: 본인 Q&A 단순 목록 (필터·정렬 미포함)
- **PolicySubscriptionView (P1)**: 구독 토글 단일 폼 (이메일 외 알림 채널 미포함)
- **AboutView (P0이나 정적)**: 단일 정적 페이지 (CMS 연동 없이 i18n 메시지로 콘텐츠 작성)

### 6.5 admin SPA 보호

- ❌ `frontend/admin/src/**`는 본 작업에서 **읽기 전용**. 어떤 admin 파일도 수정·생성·삭제하지 않음.
- ❌ `frontend/shared/src/**` 역시 본 작업에서 수정하지 않음 (필요 시 별도 SPEC). public은 shared의 axios client를 재사용하지 않고 자체 client.ts를 작성하므로 shared 변경 불필요.

---

## 7. SPEC 인수기준 매핑 (Acceptance Criteria → Tasks)

| 그룹 | 시나리오 수 | 매핑 태스크 |
|------|-----------|-----------|
| A. 레이아웃·내비게이션 | 8 (A-01~A-08) | T-005 (PublicLayout + 공용 컴포넌트) |
| B. 공지·게시판·FAQ·Q&A | 8 (B-01~B-08) | T-006 |
| C. 정책·안전·발간자료 | 8 (C-01~C-08) | T-007 |
| D. 검색·통계·미디어 | 6 (D-01~D-06) | T-008 |
| E. 접근성·i18n·반응형 | 8 (E-01~E-08) | T-010 (Phase 5) — 단, jest-axe는 T-005~T-009에서 continuous |
| F. 에러·엣지케이스 | 7 (F-01~F-07) | T-009 + T-004 (점검 가드 F-04) + T-006 (Promise.allSettled F-07) |
| **합계** | **45** | |

---

## 8. 다음 단계 (Next Steps after Approval)

본 계획 승인 후 진행 절차:

1. **TDD 모드 확정**: `.moai/config/sections/quality.yaml` `quality.development_mode` 확인 (기본 TDD). 본 SPEC는 신규 코드 다수이므로 TDD가 적합.
2. **manager-ddd 또는 manager-tdd로 위임**: 다음 페이로드로 전달 가능:
   - SPEC ID: `SPEC-CMS-PUBLIC-001`
   - 라이브러리 버전 결정: echarts 5.5.1, vue-echarts 7.0.3, dompurify 최신, jest-axe 최신
   - 차트 결정: ECharts 채택 (admin 일관)
   - SEO 메타 결정: 자체 구현 (YAGNI)
   - 테스트 전략: Vitest + jest-axe 우선, [PW] 시나리오는 단위 대체
   - 10개 태스크 분해 (T-001~T-010)
   - 위험 완화 5종 (R1~R5)
3. **사용자 승인 대기**: 본 계획에 대한 명시적 승인(예: "진행해주세요" 또는 AskUserQuestion 응답) 후에만 manager-ddd/tdd로 위임.
4. **annotation 사이클(SPEC 워크플로우 §Plan Phase Sub-phase 3)**: 사용자가 본 계획 검토 후 1~6회 수정 라운드 가능. 매 라운드는 본 plan 파일을 갱신하며 사용자 명시적 "Proceed" 시 manager-ddd/tdd 호출.

---

## 9. 작성 메타

- **작성**: manager-strategy (MoAI)
- **작성일**: 2026-05-14
- **기반 SPEC**: SPEC-CMS-PUBLIC-001 v0.1.0 (Draft)
- **기반 문서**: spec.md (681 lines), acceptance.md (444 lines), plan.md (409 lines)
- **검증 상태**: 본 계획은 SPEC §3 범위 100% 커버 + acceptance.md 45 시나리오 100% 매핑 + plan.md §2 Phase 0~6 7단계 100% 반영
- **본 파일 위치**: `frontend/.moai/plans/tingly-spinning-rose-agent-a6277ed4dee702fd2.md` (Plan mode 요구사항)
