---
id: SPEC-CMS-PUBLIC-001
version: 0.1.0
status: Draft
created: 2026-05-14
updated: 2026-05-14
author: manager-spec
priority: P0
parent: SPEC-CMS-001 v0.5
related:
  - SPEC-CMS-002 (auth/users APIs)
  - SPEC-CMS-003 (board/notice/faq/qna APIs)
  - SPEC-CMS-005 (system/code APIs)
  - SPEC-CMS-006 (safety APIs)
  - SPEC-CMS-007 (policy APIs)
  - SPEC-CMS-008 (dashboard/KPI APIs)
  - SPEC-CMS-009 (maintenance APIs)
  - SPEC-CMS-010 (search/menu/site APIs)
  - SPEC-CMS-MEDIA-001 (media APIs)
issue_number: TBD
---

# SPEC-CMS-PUBLIC-001: 시민 대상 공공 사이트 SPA (Public Citizen-Facing Web SPA)

## 1. 개요

| 항목 | 내용 |
|------|------|
| SPEC ID | SPEC-CMS-PUBLIC-001 |
| 제목 | 시민 대상 공공 사이트 SPA (Public Citizen-Facing Web SPA) |
| 부모 SPEC | SPEC-CMS-001 (Umbrella) v0.5 |
| 동급 SPEC | SPEC-CMS-002~010 (백엔드 API — Tested 상태, 본 SPEC은 소비자) |
| 작성일 | 2026-05-14 |
| 최종 수정 | 2026-05-14 (v0.1 — 초안) |
| 작성자 | manager-spec (MoAI) |
| 상태 | Draft |
| 우선순위 | P0 (RFP §10 통합 홈페이지 이관 + INR-003/004/005 반응형 UI 충족) |
| 분류 | Frontend Detail SPEC |
| 대상 사용자 | 일반 시민 (중소기업 대표자/실무자/일반 방문자) — 비인증 + 선택적 인증 |
| 기술 스택 | Vue 3.5.13 + TypeScript 5.6.3 + Vite 6.0.3 + Pinia 2.2.6 + Vue Router 4.4.5 + Element Plus 2.8.8 + Tailwind CSS 3.4.16 + vue-i18n 9.14.1 |
| 모듈 위치 | `frontend/public/` (모노레포 두 번째 SPA) |
| 테스트 도구 | Vitest 2.1.8 + @vue/test-utils 2.4.6 + jsdom 25.0.1 + (E2E는 Playwright 후속) |

본 SPEC은 SPEC-CMS-001 §4.1 다중 SPA(`frontend/admin`, `frontend/public`) 아키텍처에서 두 번째 SPA인 시민 대상 공공 사이트의 화면·라우터·스토어·API 클라이언트·접근성 정책을 확정한다. 백엔드 API는 SPEC-CMS-002~010 + SPEC-CMS-MEDIA-001에서 이미 Tested 상태로 존재하므로 본 SPEC은 신규 백엔드 엔드포인트를 정의하지 않는다.

본 SPEC의 핵심 가치:

1. **시민 접근성 우선** — KWCAG 2.2 AA를 P0 게이트로 강제. 모든 페이지는 키보드 단독 조작, 스크린리더 음성 출력, 4.5:1 색대비, 200% 확대 시 가로 스크롤 미발생.
2. **반응형** — 모바일/태블릿/데스크탑 3종 시안(RFP INR-004). Tailwind breakpoint(`sm` 640 / `md` 768 / `lg` 1024 / `xl` 1280) 일관 사용.
3. **i18n 한국어/영어** — 기본 한국어, 영어 토글 가능. vue-i18n locale 변경 시 모든 UI·메뉴·라우트 메타 타이틀 동시 갱신.
4. **인증 선택** — 대부분 익명 허용. Q&A 제출, 정책 알림 구독 등 일부 액션만 SPEC-CMS-002 로그인 요구.
5. **SEO 친화** — 메타 태그 동적 갱신(title, description, og:*, canonical). 1차 출시는 SPA SSR/SSG 미적용(후속 SPEC). 검색엔진 크롤러 대응은 prerender.io 또는 Vite SSR을 후속 도입.

---

## 2. 참조 문서

- 부모 SPEC: `.moai/specs/SPEC-CMS-001/spec.md` §4.1 (다중 SPA 아키텍처), §6.5 (REQ-CROSS-001 접근성), §17 (RFP 통합 비기능 — PER-003 검색 < 3초)
- 동급 SPEC (백엔드 API 소스 — 모두 Tested):
  - SPEC-CMS-002: 인증·사용자 (`/api/v1/auth/login`, `/auth/logout`, `/auth/refresh`)
  - SPEC-CMS-003: 게시판·공지·FAQ·Q&A (`/api/v1/boards`, `/api/v1/posts`, `/api/v1/notices`, `/api/v1/faqs`, `/api/v1/qnas`, `/api/v1/publications`, `/api/v1/surveys`)
  - SPEC-CMS-005: 시스템·코드·KPI 정의 (`/api/v1/system/codes`, `/api/v1/kpi/definitions`)
  - SPEC-CMS-006: 안전관리 (`/api/v1/safety/guidelines`, `/api/v1/safety/incidents`, `/api/v1/safety/checklists`)
  - SPEC-CMS-007: 정책사업 (`/api/v1/policies`, `/api/v1/policies/match`, `/api/v1/policies/subscriptions`)
  - SPEC-CMS-008: 대시보드·KPI 데이터 (`/api/v1/dashboard/widgets/*/data`, `/api/v1/kpi/values`)
  - SPEC-CMS-009: 점검·시스템 헬스 (`/api/v1/system/health`, `/api/v1/system/maintenance-notices`)
  - SPEC-CMS-010: 사이트·메뉴·통합검색 (`/api/v1/sites`, `/api/v1/menus`, `/api/v1/search`)
  - SPEC-CMS-MEDIA-001: 미디어 (`/api/v1/media`)
- RFP: `.moai/refs/rfp-summary.md` §0.1 4대 사업범위(④ 사용자 최적화), §1 SFR-009(시각화 UI/UX + 웹표준·웹접근성), §3 INR-001~012(인터페이스 요구), §10(통합 홈페이지 이관)
- 관리자 SPA 패턴 참조 (수정 금지): `frontend/admin/src/router/index.ts`, `frontend/admin/src/layouts/AdminLayout.vue`, `frontend/admin/src/api/*.ts`, `frontend/admin/src/stores/*.ts`
- 기존 구조 (확장 대상): `frontend/public/src/{App.vue,main.ts,i18n.ts,router/index.ts,stores/auth.ts}`

---

## 3. 범위 및 비범위

### 3.1 범위 (1차 출시 P0)

본 SPA는 약 25개의 라우트를 제공한다.

**A. 홈·정보 (P0)**

1. 홈(`/`) — 히어로 배너, 주요 공지 카드, 정책 하이라이트, 안전 가이드 진입, 통합 검색 박스, 빠른 링크
2. 사이트 소개(`/about`) — 기관 소개, 연혁, 조직(정적 콘텐츠)

**B. 공지·게시판·FAQ·Q&A (P0 — SPEC-CMS-003)**

3. 공지 목록(`/notices`) — 페이징·카테고리·키워드 검색
4. 공지 상세(`/notices/:id`) — 본문(sanitize 완료), 첨부 다운로드(서명 URL), 인쇄 보기
5. 게시판 목록(`/boards/:code`) — 게시판 코드별 게시글 페이징
6. 게시글 상세(`/boards/:code/posts/:id`) — 본문, 첨부, 댓글(읽기 전용 — 비로그인 작성 금지)
7. FAQ(`/faqs`) — 카테고리 아코디언, 키워드 검색
8. Q&A 목록(`/qnas`) — 공개된 답변 완료 Q&A만 표시
9. Q&A 작성(`/qnas/new`) — 인증 필요 시 로그인 페이지로 리다이렉트 후 복귀
10. 발간자료(`/publications`) — 연도·문서종류·카테고리 필터, 다운로드 통계
11. 발간자료 상세(`/publications/:id`) — 다중 첨부 zip 다운로드 요청

**C. 정책·안전 (P0 — SPEC-CMS-006, SPEC-CMS-007)**

12. 정책사업 목록(`/policies`) — 업종·지역·유형 필터
13. 정책사업 상세(`/policies/:id`) — 신청자격, 지원금액, 신청 CTA, 외부 신청 링크
14. 정책 매칭(`/policies/match`) — 익명 가능 — 기업 정보 입력 → 적합도 % TOP-N
15. 안전 가이드 목록(`/safety/guidelines`) — 업종·공정 필터
16. 안전 가이드 상세(`/safety/guidelines/:id`) — 체크리스트, 관련 사고사례
17. 사고사례 목록(`/safety/incidents`) — 공개 사례만, 업종 필터

**D. 통계·미디어 (P1)**

18. 공개 KPI 대시보드(`/stats`) — SPEC-CMS-008 공개 위젯, 차트 4~6종(Chart.js 또는 ECharts — 후속 1차 결정)
19. 미디어 갤러리(`/media`) — 이미지·동영상 썸네일 그리드, 카테고리 필터

**E. 검색·사이트맵 (P0 — SPEC-CMS-010)**

20. 통합 검색 결과(`/search`) — `?q=` — 게시글·FAQ·Q&A·정책·안전 가이드 통합 결과
21. 사이트맵(`/sitemap`) — 메뉴 트리 전체 노출(접근성 보조)

**F. 인증·계정 (P1 — SPEC-CMS-002, 일부만 필요)**

22. 로그인(`/login`) — Q&A 작성·정책 알림 구독 시 진입. SPEC-CMS-002 `/auth/login` 호출. 로그인 후 `?redirect=` 경로로 복귀.
23. 정책 알림 구독(`/policies/subscriptions`) — 인증 필수. SPEC-CMS-007 `POST /api/v1/policies/subscriptions`.
24. 내 Q&A(`/me/qnas`) — 인증 필수. 본인이 작성한 Q&A 목록(SPEC-CMS-003 `GET /api/v1/qnas?mine=true`).
25. 점검 안내(`/maintenance`) — SPEC-CMS-009 점검 모드 활성 시 라우터 가드가 강제 리다이렉트.

**G. 에러 페이지 (P0)**

- 404 NotFound (이미 존재)
- 403 Forbidden (권한 없음)
- 500 Server Error (API 실패 fallback)

### 3.2 비범위 (Out of Scope)

| 항목 | 사유 |
|------|------|
| SSR/SSG (Nuxt 또는 Vite SSR) | 1차는 CSR(SPA)만. SEO 보강은 prerender.io + meta tag 동적 갱신으로 우회. SSR은 후속 SPEC. |
| 회원가입(시민 본인 회원) | SPEC-CMS-002는 운영자 회원만 정의. 시민 회원가입은 별도 SPEC(SPEC-CMS-PUBLIC-002 예정). |
| SSO 연동(상급기관 통합로그인) | RFP §1 SFR-010 명시 P0이나 백엔드 구현 미완. SSO 어댑터는 별도 SPEC(SPEC-CMS-SSO-001 예정). |
| 댓글 작성 기능 (공공 측) | 비로그인 작성 보안 위험(스팸·욕설·개인정보 노출). 1차는 읽기 전용. 댓글 작성은 후속에서 reCAPTCHA + 인증 회원만. |
| 다크 모드 | 1차 P0 미포함. 후속에서 prefers-color-scheme + Tailwind dark 모드 추가. |
| PWA(Service Worker) | 1차 P0 미포함. 후속 SPEC. |
| 카카오 알림톡 가입 UI | SPEC-CMS-007의 알림 구독은 이메일·인앱 1차. 알림톡은 SPEC-CMS-007 후속. |
| 가상 시뮬레이션 화면(RFP SFR-003) | 1차 공공 사이트 비범위. 별도 SPEC(SPEC-CMS-SIM-001 예정). |
| 설문조사 응답 화면 | 1차는 응답 등록(`POST /api/v1/surveys/{id}/responses`) UI 미제공. 후속. |
| 다크 패턴·광고·트래커 통합 | 정부 사이트 정책상 금지. 별도 SPEC 없음. |
| 한국어/영어 외 언어 | 1차는 ko/en 2종. 일본어·중국어 등은 후속. |

---

## 4. 라우터 구조

### 4.1 Vue Router 트리

```
/ (PublicLayout)
├── '' (HomeView)                                — 홈
├── 'about' (AboutView)                          — 사이트 소개
├── 'notices' (NoticeListView)                   — 공지 목록 (SPEC-CMS-003)
├── 'notices/:id' (NoticeDetailView)             — 공지 상세
├── 'boards/:code' (BoardPostListView)           — 게시판 목록
├── 'boards/:code/posts/:id' (BoardPostDetailView) — 게시글 상세
├── 'faqs' (FaqView)                             — FAQ 아코디언
├── 'qnas' (QnaListView)                         — Q&A 목록 (공개+답변완료)
├── 'qnas/new' (QnaCreateView)  [requiresAuth]   — Q&A 작성
├── 'qnas/:id' (QnaDetailView)                   — Q&A 상세 (private는 본인+admin)
├── 'publications' (PublicationListView)         — 발간자료 목록
├── 'publications/:id' (PublicationDetailView)   — 발간자료 상세
├── 'policies' (PolicyListView)                  — 정책사업 목록 (SPEC-CMS-007)
├── 'policies/:id' (PolicyDetailView)            — 정책사업 상세
├── 'policies/match' (PolicyMatchView)           — 정책 매칭(익명 가능)
├── 'policies/subscriptions' [requiresAuth]      — 알림 구독 관리
├── 'safety/guidelines' (SafetyGuidelineListView) — 안전 가이드 (SPEC-CMS-006)
├── 'safety/guidelines/:id' (SafetyGuidelineDetailView)
├── 'safety/incidents' (SafetyIncidentListView)  — 사고사례 (공개분)
├── 'stats' (PublicStatsView)                    — 공개 KPI (SPEC-CMS-008)
├── 'media' (MediaGalleryView)                   — 미디어 갤러리 (SPEC-CMS-MEDIA-001)
├── 'search' (SearchResultView)                  — 통합 검색 결과 (SPEC-CMS-010)
├── 'sitemap' (SitemapView)                      — 사이트맵
├── 'me/qnas' [requiresAuth] (MyQnaListView)     — 내 Q&A
├── 'maintenance' (MaintenanceView)              — 점검 안내 (SPEC-CMS-009)
└── '/login' (no layout) (LoginView)             — 로그인

/:pathMatch(.*)* (NotFoundView)                  — 404
/error/403 (ForbiddenView)                       — 403
/error/500 (ServerErrorView)                     — 500
```

### 4.2 라우트 메타

| 키 | 타입 | 기본값 | 의미 |
|----|------|--------|------|
| `title` | string | "" | 페이지 타이틀 (i18n 키 또는 직접 문자열). `router.afterEach`에서 `document.title` 갱신. |
| `requiresAuth` | boolean | false | true 시 비인증 사용자는 `/login?redirect={원래경로}`로 리다이렉트 (Q&A 작성, 알림 구독, 내 Q&A). |
| `public` | boolean | true | 시민 사이트는 기본 공개. 운영자 사이트(admin)와 구분용. |
| `breadcrumb` | string[] | [] | 브레드크럼 트리 (i18n 키 배열). 빈 배열이면 브레드크럼 미표시(홈만). |
| `seo.description` | string | "" | 메타 description. 동적 라우트는 상세 페이지가 onMounted 시 setter 호출로 갱신. |
| `seo.ogImage` | string | "" | og:image URL. 미설정 시 기본 사이트 로고. |
| `noLayout` | boolean | false | true 시 PublicLayout 미적용 (로그인 페이지 등 미니멀 화면). |

### 4.3 라우터 가드

라우터 가드는 다음 순서로 실행한다(`router.beforeEach`):

1. **점검 모드 가드** — SPEC-CMS-009 `GET /api/v1/system/health` 결과로 `maintenance_mode=true` 시(어드민이 활성) 모든 라우트를 `/maintenance`로 강제 리다이렉트. 단, `/maintenance` 자체와 `/error/*`는 통과. 응답 캐시 60초(maintenance 스토어).
2. **인증 가드** — `to.meta.requiresAuth === true` AND `!authStore.isAuthenticated` 시 `/login?redirect={to.fullPath}`로 리다이렉트.
3. **i18n 가드** — `to.query.lang` (`ko` 또는 `en`)이 있으면 `useI18n().locale.value`에 적용 후 query에서 제거(replace).
4. **타이틀 갱신** — `router.afterEach`에서 `to.meta.title`을 i18n 변환 후 `document.title = "${title} | iroum-cms"` 설정.
5. **스크롤 복원** — `scrollBehavior(to, from, savedPosition)` 표준 패턴. 동일 라우트 내 해시 점프는 `el.scrollIntoView({ behavior: 'smooth' })`.

---

## 5. 컴포넌트 아키텍처

### 5.1 디렉토리 구조

```
frontend/public/src/
├── api/                          — REST 클라이언트 (axios 인스턴스 + 도메인별 모듈)
│   ├── client.ts                 — axios.create + 인터셉터 (토큰·에러)
│   ├── auth.ts                   — SPEC-CMS-002
│   ├── notice.ts                 — SPEC-CMS-003 (공지)
│   ├── board.ts                  — SPEC-CMS-003 (게시판)
│   ├── faq.ts                    — SPEC-CMS-003 (FAQ)
│   ├── qna.ts                    — SPEC-CMS-003 (Q&A)
│   ├── publication.ts            — SPEC-CMS-003 (발간자료)
│   ├── policy.ts                 — SPEC-CMS-007
│   ├── safety.ts                 — SPEC-CMS-006
│   ├── dashboard.ts              — SPEC-CMS-008 (공개 KPI)
│   ├── media.ts                  — SPEC-CMS-MEDIA-001
│   ├── search.ts                 — SPEC-CMS-010
│   ├── menu.ts                   — SPEC-CMS-010
│   └── system.ts                 — SPEC-CMS-009 (health, maintenance)
├── stores/                       — Pinia 스토어
│   ├── auth.ts                   — 인증 상태(JWT, refresh)
│   ├── menu.ts                   — 메뉴 트리 (60초 캐시)
│   ├── maintenance.ts            — 점검 모드 상태 (60초 캐시)
│   ├── search.ts                 — 검색어 히스토리(LocalStorage)
│   ├── locale.ts                 — i18n locale 동기화(LocalStorage)
│   └── breadcrumb.ts             — 동적 브레드크럼 (라우트 메타 + 동적 라벨)
├── layouts/
│   └── PublicLayout.vue          — 시민 사이트 공용 레이아웃
├── components/                   — 공용 컴포넌트
│   ├── nav/
│   │   ├── PublicHeader.vue      — 상단 헤더 (로고, 메인 메뉴, 언어 토글, 검색박스, 로그인)
│   │   ├── PublicFooter.vue      — 푸터 (기관 정보, 사이트맵 링크, 접근성 정책)
│   │   ├── PublicBreadcrumb.vue  — 브레드크럼(ol > li, aria-current)
│   │   └── PublicSkipNav.vue     — 본문 바로가기(스킵 내비)
│   ├── common/
│   │   ├── PageHeader.vue        — 페이지 타이틀 + 설명
│   │   ├── PaginationBar.vue     — el-pagination 래퍼 (aria-label 한글화)
│   │   ├── SearchInput.vue       — 검색 입력 + 자동완성 (SPEC-CMS-010)
│   │   ├── EmptyState.vue        — 빈 상태 (검색 결과 없음 등)
│   │   ├── ErrorState.vue        — 에러 상태(재시도 버튼)
│   │   ├── LoadingState.vue      — 로딩 스켈레톤
│   │   └── AttachmentDownload.vue — 첨부 다운로드 버튼 (SPEC-CMS-003 서명 URL)
│   ├── notice/
│   │   ├── NoticeCard.vue        — 홈·목록 공용 카드
│   │   └── NoticeContent.vue     — 본문 렌더링 (DOMPurify는 백엔드에서 sanitize 완료 가정, 추가 escape)
│   ├── policy/
│   │   ├── PolicyCard.vue
│   │   ├── PolicyFilterBar.vue   — 업종/지역/유형 다중 필터
│   │   └── PolicyMatchForm.vue   — 기업 정보 입력 폼
│   ├── safety/
│   │   ├── SafetyChecklist.vue   — 체크리스트 렌더링
│   │   └── IncidentCard.vue
│   ├── search/
│   │   ├── SearchResultCard.vue  — 타입별 결과 카드 (POST/FAQ/QNA/POLICY/SAFETY)
│   │   └── SearchFilterTabs.vue  — 타입 필터 탭
│   └── stats/
│       └── KpiChart.vue          — 공개 KPI 차트 (Chart.js 또는 ECharts)
├── composables/
│   ├── useApi.ts                 — 데이터 패칭 + 에러 처리 (관리자 SPA 패턴 차용)
│   ├── useDebounce.ts            — 검색 자동완성 디바운스(300ms)
│   ├── useMenuTree.ts            — SPEC-CMS-010 메뉴 트리 로드 + 캐시
│   ├── usePagination.ts          — 페이징 상태(page, size, total)
│   ├── useBreadcrumb.ts          — 동적 브레드크럼 설정
│   ├── useSeoMeta.ts             — 메타 태그 동적 갱신(title, description, og:*)
│   └── useFocusTrap.ts           — 다이얼로그 포커스 트랩(접근성)
├── locales/
│   ├── ko.json                   — 한국어 i18n 메시지(기본)
│   └── en.json                   — 영어 i18n 메시지
├── views/                        — 페이지 컴포넌트 (라우트 진입점)
│   ├── HomeView.vue
│   ├── AboutView.vue
│   ├── notices/
│   │   ├── NoticeListView.vue
│   │   └── NoticeDetailView.vue
│   ├── boards/
│   │   ├── BoardPostListView.vue
│   │   └── BoardPostDetailView.vue
│   ├── FaqView.vue
│   ├── qnas/
│   │   ├── QnaListView.vue
│   │   ├── QnaDetailView.vue
│   │   ├── QnaCreateView.vue
│   │   └── MyQnaListView.vue
│   ├── publications/
│   │   ├── PublicationListView.vue
│   │   └── PublicationDetailView.vue
│   ├── policies/
│   │   ├── PolicyListView.vue
│   │   ├── PolicyDetailView.vue
│   │   ├── PolicyMatchView.vue
│   │   └── PolicySubscriptionView.vue
│   ├── safety/
│   │   ├── SafetyGuidelineListView.vue
│   │   ├── SafetyGuidelineDetailView.vue
│   │   └── SafetyIncidentListView.vue
│   ├── PublicStatsView.vue
│   ├── MediaGalleryView.vue
│   ├── SearchResultView.vue
│   ├── SitemapView.vue
│   ├── MaintenanceView.vue
│   ├── LoginView.vue
│   ├── errors/
│   │   ├── ForbiddenView.vue
│   │   └── ServerErrorView.vue
│   └── NotFoundView.vue          — 이미 존재
└── router/
    └── index.ts                  — 라우트 정의 + 가드 (기존 파일 확장)
```

### 5.2 PublicLayout.vue 구조

```
PublicLayout (template)
├── PublicSkipNav             (a href="#main-content" — KWCAG 2.4.1)
├── PublicHeader (header role="banner")
│   ├── 로고 (a href="/" — 사이트명 + 로고 이미지, alt 텍스트)
│   ├── 메인 메뉴 (nav role="navigation" aria-label="주메뉴")
│   │   └── 메뉴 트리(`/api/v1/menus` from menuStore, 2단계 드롭다운)
│   ├── 검색 박스 (SearchInput — SPEC-CMS-010 자동완성)
│   ├── 언어 토글 (button — ko/en, currentLocale aria-pressed)
│   └── 사용자 메뉴 (인증 시: 내 Q&A · 알림 구독 · 로그아웃 / 비인증 시: 로그인)
├── PublicBreadcrumb          (nav aria-label="현재 위치", ol > li[aria-current])
├── main#main-content (role="main", tabindex="-1")
│   └── <router-view />       (각 페이지 본문)
├── BackToTopButton (button aria-label="맨 위로", scrollY > 400px 시 노출)
└── PublicFooter (footer role="contentinfo")
    ├── 기관 정보 (주소, 대표 전화, 사업자등록번호)
    ├── 사이트맵 링크 + 접근성 정책 + 개인정보 처리방침
    ├── 카피라이트
    └── 관련 사이트 select (정부24, 중기부 등)
```

### 5.3 API 클라이언트 패턴 (`api/client.ts`)

axios 인스턴스 패턴은 관리자 SPA의 `frontend/admin/src/api/client.ts`를 차용한다. 차이점:

- **인터셉터(요청)**: `auth.accessToken`이 있으면 `Authorization: Bearer {token}` 헤더 추가. 토큰 미존재 시 헤더 미추가(시민 사이트는 익명 호출이 다수).
- **인터셉터(응답 — 401)**: refresh 토큰으로 재발급 시도 → 실패 시 `auth.clearAuth()` + 현재 라우트가 `requiresAuth=true`인 경우만 `/login?redirect={현재경로}`로 리다이렉트. `requiresAuth=false` 라우트는 401을 단순 무시(익명 호출 정상).
- **인터셉터(응답 — 5xx)**: `/error/500` 라우트로 fallback. 단, 폼 제출(`POST/PUT/DELETE`)은 컴포넌트 측에서 처리 가능하도록 `axios.isCancel` 외 에러는 그대로 reject.
- **인터셉터(응답 — 503)**: `code === 'MAINTENANCE_MODE_ACTIVE'` 시 `/maintenance`로 리다이렉트 (SPEC-CMS-009).
- **baseURL**: `import.meta.env.VITE_API_BASE_URL || '/api/v1'`. 개발 시 Vite proxy로 `http://localhost:8080`에 프록시.
- **timeout**: 15000ms (검색 PER-003은 3초 게이트지만 안전 마진).

### 5.4 스토어 패턴 (`stores/`)

- `auth.ts`: 기존 placeholder 확장 — `accessToken`, `refreshToken`, `user`, `isAuthenticated`, `login()`, `logout()`, `refresh()`. LocalStorage 직렬화는 `accessToken`/`refreshToken`만(민감 정보 분리). `user`는 메모리만.
- `menu.ts`: `loadMenuTree()` — 첫 호출 시 `GET /api/v1/menus?siteCode=public`. 60초 캐시. locale 변경 시 강제 reload.
- `maintenance.ts`: `checkMaintenance()` — `GET /api/v1/system/health` 호출 후 `maintenanceMode: boolean`. 60초 캐시. 라우터 가드에서 호출.
- `search.ts`: 검색어 히스토리 최근 5개를 LocalStorage(`public.search.history`)에 보존. 인기 검색어는 후속.
- `locale.ts`: vue-i18n `locale` 값을 LocalStorage(`public.locale`)에 영속화. 시스템 언어 감지(`navigator.language`)는 1회만 적용(이미 LocalStorage에 값 있으면 우선).
- `breadcrumb.ts`: 라우트 메타의 정적 breadcrumb과 페이지에서 setter로 주입하는 동적 breadcrumb(예: 정책사업 상세 — 정책명)을 합성하여 PublicBreadcrumb에 공급.

---

## 6. 페이지별 명세

각 페이지는 `{컴포넌트, 라우트, 사용 스토어, API 호출, 주요 props/query, 빈 상태/에러 상태 처리}` 형식으로 정의한다.

### 6.1 HomeView (`/`)

- **컴포넌트**: `views/HomeView.vue`
- **사용 스토어**: `menu`(2-depth 빠른 링크), 없음 외 직접 API 호출
- **API 호출**:
  - `GET /api/v1/notices?page=0&size=5` — 최신 공지 5건
  - `GET /api/v1/policies?page=0&size=4&featured=true` — 추천 정책 4건(featured 쿼리 미지원 시 최신순 4건)
  - `GET /api/v1/dashboard/widgets/public-home/data` — 공개 위젯 데이터(SPEC-CMS-008)
- **구성 섹션**:
  1. 히어로 배너 (h1 + 간략 설명 + CTA 2개: "정책 매칭 시작" → `/policies/match`, "안전 가이드 보기" → `/safety/guidelines`)
  2. 최신 공지 카드 그리드(5개, 모바일 1열·태블릿 2열·데스크탑 3열)
  3. 정책 하이라이트(4개 카드)
  4. 빠른 링크(아이콘 + 라벨 6~8개: FAQ, Q&A, 발간자료, 안전 가이드, 통계, 사이트맵)
  5. KPI 위젯(공개 통계 3~4개 숫자 카드)
- **빈 상태**: 공지·정책 0건 시 "준비 중입니다" 메시지(접근성 라이브 영역)
- **에러 상태**: 각 섹션은 독립적으로 실패해도 다른 섹션 표시 (Promise.allSettled)

### 6.2 NoticeListView (`/notices`)

- **API**: `GET /api/v1/notices?page=&size=&keyword=&from=&to=&categoryCode=`
- **Query 파라미터**: `page` (기본 0), `size` (기본 20), `keyword`, `categoryCode`, `from`, `to`
- **구성**: 검색 입력 + 카테고리 select + 기간 picker + 목록 테이블(또는 카드 — 모바일) + 페이징 바
- **공지 상단 고정**: `notices[]` 배열은 페이지 0에서만 별도 노출(SPEC-CMS-003 §6.5 `GET /api/v1/notices/active`)
- **빈 상태**: "검색 결과가 없습니다" + 검색 초기화 버튼
- **접근성**: 테이블은 `<caption>` + `<th scope="col">`. 페이지네이션은 `aria-label="페이지 이동"` + 현재 페이지 `aria-current="page"`.

### 6.3 NoticeDetailView (`/notices/:id`)

- **API**: `GET /api/v1/posts/:id` (공지는 BBS 게시글의 NOTICE 타입)
- **구성**: 제목 h1, 작성자/작성일/조회수 메타, 본문 v-html(서버측 sanitize 완료 — DOMPurify 추가 적용 불요. 단 `<script>` 차단 안전망), 첨부파일 목록(AttachmentDownload — SPEC-CMS-003 서명 URL TTL 15분), 이전/다음 글 링크, 인쇄 버튼.
- **첨부 다운로드 흐름**: 클릭 → `POST /api/v1/attachments/:id/download-url` → `{url, expiresAt}` 응답 → `window.location.href = url`로 즉시 이동. 비공개 게시글 첨부는 401/403 시 로그인 페이지로.
- **에러 404**: 게시글 없음 → NotFoundView로 push.

### 6.4 BoardPostListView (`/boards/:code`)

- **API**:
  - `GET /api/v1/boards?code={code}` (마스터 조회 → bbsId + 마스터 메타)
  - `GET /api/v1/boards/{bbsId}/posts?page=&size=&keyword=&category=`
- **마스터 타입별 레이아웃**: SPEC-CMS-003 §15.1 `bbs_type_template` 응답을 활용. NORMAL은 리스트, GALLERY는 그리드(3열), PUBLICATION은 별도 라우트(`/publications`)로 리다이렉트.
- **비공개 게시판** (`role_required_read` 설정): 401 응답 시 로그인 페이지로.

### 6.5 BoardPostDetailView (`/boards/:code/posts/:id`)

- **API**: `GET /api/v1/posts/:id` + `GET /api/v1/posts/:id/comments` (댓글 트리)
- **댓글**: 1차 출시는 **읽기 전용**(범위 표 §3.2). 작성 폼 미렌더링. 댓글 0건 시 "아직 댓글이 없습니다".
- **첨부**: NoticeDetailView와 동일 패턴.

### 6.6 FaqView (`/faqs`)

- **API**:
  - `GET /api/v1/faqs/categories` — 카테고리 목록
  - `GET /api/v1/faqs?categoryCode=&keyword=&page=&size=`
- **구성**: 카테고리 탭 + 키워드 검색 + 아코디언(el-collapse)
- **접근성**: 아코디언 헤더는 `<button aria-expanded="false">`. 열기 토글 시 본문 영역 `aria-hidden` 갱신. 키보드 Enter/Space로 토글.

### 6.7 QnaListView (`/qnas`) / QnaCreateView (`/qnas/new`) / QnaDetailView (`/qnas/:id`)

- **목록 API**: `GET /api/v1/qnas?status=ANSWERED&page=&size=&keyword=` (공개+답변완료만)
- **작성 API**: `POST /api/v1/qnas {title, questionHtml, isPrivate}` — `requiresAuth=true`
- **상세 API**: `GET /api/v1/qnas/:id`. `is_private=true`이고 본인이 아니면 백엔드가 404 반환(SPEC-CMS-003 REQ-BOARD-008-D-3) → NotFoundView.
- **본문 입력**: 위지윅 에디터는 1차 미적용(서버측 sanitize는 있으나 클라이언트 부담 최소화). plain `<textarea>` + 가이드 문구("HTML은 지원되지 않습니다") + 최대 2000자 카운터.

### 6.8 PublicationListView (`/publications`) / Detail

- **API**: SPEC-CMS-003 §6.6 미정의지만 `GET /api/v1/publications?year=&month=&documentType=&categoryId=&keyword=` (REQ-BOARD-012-D-5) 가정.
- **카테고리 트리**: `GET /api/v1/publications/categories` (트리 응답)
- **압축 다운로드**: 상세 페이지에서 다중 첨부 선택 → `POST /api/v1/posts/:id/download-zip {attachmentIds:[]}` → 50MB 이하 즉시 zip 응답, 초과 시 `{jobId}` + "준비 중입니다. 완료 시 알림이 발송됩니다" 토스트.

### 6.9 PolicyListView (`/policies`) / Detail / Match / Subscription

- **목록 API**: `GET /api/v1/policies?industry=&region=&type=&page=&size=&keyword=`
- **매칭 API**: `POST /api/v1/policies/match {industry, capitalAmount, revenueAmount, employeeCount, region}` → `[{policyId, score, reason}]` TOP-10. 익명 가능(SPEC-CMS-007).
- **상세**: 신청자격 EARS 형식 렌더링, 지원금액 강조, 외부 신청 URL은 `<a target="_blank" rel="noopener noreferrer">`. URL 안전성 검사(http/https만 허용).
- **알림 구독**: `requiresAuth=true`. `POST /api/v1/policies/subscriptions {policyId, channels:['EMAIL']}` (카카오는 1차 비범위 §3.2).

### 6.10 SafetyGuidelineListView (`/safety/guidelines`) / Detail / IncidentList

- **API**:
  - `GET /api/v1/safety/guidelines?industryCode=&processCode=&page=&size=`
  - `GET /api/v1/safety/guidelines/:id` (체크리스트 + 관련 사고사례 ID 목록)
  - `GET /api/v1/safety/incidents?industryCode=&page=&size=` (공개 사례만, 공개 플래그 백엔드에서 필터)
- **체크리스트 렌더링**: `<ul role="list">` + 체크박스(읽기 전용, 인쇄용). 사용자 입력 저장은 1차 비범위.

### 6.11 PublicStatsView (`/stats`)

- **API**: `GET /api/v1/dashboard/widgets/public-stats/data` (SPEC-CMS-008)
- **차트 라이브러리**: ECharts 5.x (관리자 SPA에서 사용 시 동일 라이브러리 채택 권장. 미사용 시 Chart.js 4.x. **1차 출시 RED 단계에서 1차 결정**).
- **접근성**: 모든 차트에는 데이터 테이블 fallback (`<table>` 동등 정보) — 스크린리더 사용자 필수.

### 6.12 MediaGalleryView (`/media`)

- **API**: `GET /api/v1/media?type=IMAGE&page=&size=&collectionId=`
- **이미지 lazy load**: `<img loading="lazy" decoding="async">`. 동영상은 썸네일만, 클릭 시 모달 또는 별도 라우트.

### 6.13 SearchResultView (`/search`)

- **Query**: `?q=&type=ALL|POST|FAQ|QNA|POLICY|SAFETY&page=&size=`
- **API**: `GET /api/v1/search?q=&types=&page=&size=` (SPEC-CMS-010)
- **타입 탭**: 전체 / 공지·게시글 / FAQ / Q&A / 정책 / 안전. 각 탭 클릭 시 query `type` 갱신.
- **하이라이트**: 응답의 `snippet` 필드에 `<mark>` 포함 (SPEC-CMS-003 §10.3). v-html로 안전 렌더링(snippet은 백엔드 sanitize 완료 가정 + `<mark>` 외 태그 클라이언트 strip).
- **빈 상태**: "{q}에 대한 검색 결과가 없습니다" + 검색 팁(2자 이상, 다른 키워드 시도 등).

### 6.14 SitemapView (`/sitemap`)

- **API**: `GET /api/v1/menus?siteCode=public&depth=3`
- **렌더링**: `<nav aria-label="사이트맵">` + 중첩 `<ul>` 트리. 1단계는 `<h2>`, 2~3단계는 `<a>` 링크.

### 6.15 MaintenanceView (`/maintenance`)

- **API**: `GET /api/v1/system/maintenance-notices?active=true`
- **표시**: 점검 시작/종료 시각, 점검 사유, 문의처. 자동 새로고침(5분 간격)으로 점검 종료 감지 → 자동 리다이렉트.

### 6.16 LoginView (`/login`)

- **Query**: `?redirect={원래경로}`
- **API**: `POST /api/v1/auth/login {username, password}` → `{accessToken, refreshToken, user}` → auth 스토어 저장 → `router.replace(redirect || '/')`.
- **레이아웃**: `noLayout=true` (PublicLayout 미적용). 미니멀 디자인(로고 + 폼).

### 6.17 MyQnaListView (`/me/qnas`)

- **API**: `GET /api/v1/qnas?mine=true&page=&size=` — 본인 작성 Q&A 전부(PENDING/ANSWERED/CLOSED)
- **인증 필수**: `requiresAuth=true`. 미인증 시 `/login?redirect=/me/qnas`.

### 6.18 ErrorViews (`/error/403`, `/error/500`)

- **표시**: 에러 코드, 사용자 친화 메시지, 홈 버튼 + 이전 페이지 버튼.
- **자동 진입**: API 클라이언트 인터셉터가 403/500 응답 시 push.

---

## 7. 공통 요구사항 (Cross-Cutting)

### 7.1 접근성 (KWCAG 2.2 AA — REQ-CROSS-001 + RFP SFR-009)

**P0 게이트 (Lighthouse Accessibility ≥ 95, axe-core 0 critical)**:

| 항목 | 정책 |
|------|------|
| 키보드 단독 조작 | 모든 인터랙티브 요소(링크, 버튼, 폼, 드롭다운, 아코디언, 모달)는 Tab/Shift+Tab 순회 가능. 모든 동작은 Enter 또는 Space로 가능. |
| 포커스 가시성 | `:focus-visible` 4.5:1 색대비 outline. Tailwind `focus-visible:outline-2 focus-visible:outline-blue-600`. |
| 스킵 내비 | PublicSkipNav 항상 첫 포커스 가능 요소. "본문 바로가기" + Tab 1회로 main으로 점프. |
| 의미적 마크업 | `<header role="banner">`, `<nav role="navigation">`, `<main role="main">`, `<footer role="contentinfo">`. h1~h6 계층 준수(h1 페이지당 1개). |
| ARIA 라벨 | 아이콘 단독 버튼은 `aria-label` 필수. 동적 알림(toast, error)은 `role="alert"` 또는 `aria-live="polite"`. |
| 색대비 | 본문 텍스트 4.5:1 이상, 큰 텍스트 3:1 이상. Tailwind `text-gray-700 on bg-white` (8.6:1) 기본. |
| 200% 확대 | 가로 스크롤 미발생. `viewport` meta + 모든 컨테이너 `max-width` + flex/grid 활용. |
| 폼 라벨 | 모든 input은 `<label for="...">` 또는 `aria-label`. error 메시지는 `aria-describedby`로 연결. |
| 이미지 대체 텍스트 | `<img alt="...">` 필수. 장식 이미지는 `alt=""`. |
| 테이블 | `<caption>` + `<th scope="col|row">`. 페이징·정렬 컨트롤은 aria-label. |
| 동영상 | `<video controls>` + 자막 트랙(VTT). 자동 재생 금지. |

**테스트**: vitest로 컴포넌트 단위 ARIA 속성 검증 + Playwright(후속)로 키보드 순회 E2E + axe-core 정적 검사 + Lighthouse CI.

### 7.2 i18n (한국어/영어)

- 기본 locale: `ko`. `navigator.language` 첫 시작 시 1회 감지하여 LocalStorage 미보유 시 적용.
- vue-i18n `legacy: false` (composition API). `useI18n()` + `<i18n-t>` 컴포넌트 사용.
- 모든 사용자 표시 문자열은 `t('key')` 또는 `<i18n-t keypath="key">`로 분리. 직접 문자열 작성 금지.
- 라우트 메타 `title`도 i18n 키 사용 (`t(to.meta.title)`).
- 백엔드 응답 중 i18n 대상은 게시판 마스터 name (SPEC-CMS-003 REQ-BOARD-001-D-5 `metadata.i18n_name`), 메뉴 라벨(SPEC-CMS-010), 에러 메시지(REQ-CROSS-006). 클라이언트는 응답의 적절한 locale 필드 선택.
- 언어 토글 시: `locale.value` 변경 + LocalStorage 저장 + `menuStore.reload()` 강제 호출(메뉴 라벨이 i18n 의존).

### 7.3 반응형 (Mobile/Tablet/Desktop — RFP INR-003/004/005)

Tailwind breakpoint 일관 사용:

| breakpoint | 화면 폭 | 대상 |
|-----------|--------|------|
| `default` | < 640px | 모바일 (세로 1열, 햄버거 메뉴) |
| `sm` | ≥ 640px | 큰 모바일 (1열 유지, 헤더 일부 표시) |
| `md` | ≥ 768px | 태블릿 (2열 그리드, 헤더 메뉴 펼침) |
| `lg` | ≥ 1024px | 데스크탑 (3열 그리드, 사이드 네비 옵션) |
| `xl` | ≥ 1280px | 큰 데스크탑 (4열 그리드 가능) |

- **헤더**: 모바일은 햄버거 메뉴 → 풀스크린 오버레이. 태블릿+ 인라인 메뉴.
- **그리드**: 카드 리스트는 `grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3` 패턴.
- **이미지**: `<img srcset>` + `sizes` 활용. 1차는 단일 이미지(`loading="lazy"`)로 시작, 후속 최적화.
- **터치 영역**: 모든 탭/버튼 최소 44x44px (KWCAG 2.5.5).

### 7.4 SEO 메타 (1차 — CSR 기반)

- `useSeoMeta()` 컴포저블 — `useHead` 패턴 직접 구현(또는 `@unhead/vue` 의존성 추가 고려).
- 동적 라우트(공지/정책/Q&A 상세)는 onMounted에서 `useSeoMeta({ title, description, ogImage })` 호출.
- 1차 P0 한계: 검색엔진 크롤러는 JS 실행 후에야 메타 확인 가능. Googlebot은 지원하나 일부 크롤러는 미흡 → 후속 SSR 또는 prerender 도입.

### 7.5 보안 (XSS·CSRF·CORS)

- **XSS**: 모든 백엔드 응답의 HTML 본문(`content_html`, `answer_html`, `description_html`)은 SPEC-CMS-003 §8.1 서버측 sanitize 완료 가정. 클라이언트는 `v-html` 사용 시 검색 결과 snippet 등 동적 텍스트만 DOMPurify로 한 번 더 정화(보안 심층 방어).
- **CSRF**: JWT Bearer 토큰 인증 + 쿠키 인증 미사용. CORS는 백엔드에서 SPEC-CMS-002 §5.1 정책에 따라 origin 검증.
- **외부 링크**: `<a target="_blank">`는 `rel="noopener noreferrer"` 필수.
- **LocalStorage 토큰**: accessToken/refreshToken만 저장(httpOnly cookie 미사용 — SPA + JWT 표준 패턴). XSS 발생 시 토큰 탈취 위험은 §7.5의 XSS 방어로 완화. 더 강한 보안은 후속 HttpOnly cookie + CSRF token 전환 검토.

### 7.6 성능 (PER-001~004 매핑)

| 항목 | 목표 | 측정 |
|------|------|------|
| 초기 페이지 로드(LCP, 데스크탑) | ≤ 2.5s | Lighthouse |
| 초기 페이지 로드(LCP, 4G 모바일) | ≤ 4s | Lighthouse |
| INP (Interaction to Next Paint) | ≤ 200ms | Lighthouse |
| CLS | ≤ 0.1 | Lighthouse |
| 검색 결과 응답 (UI 표시까지) | ≤ 3s (PER-003) | 통합 측정 |
| 번들 크기(initial JS gzip) | ≤ 300KB | Vite analyzer |
| 코드 스플리팅 | 라우트 단위 dynamic import | `() => import(...)` 모든 라우트 |

### 7.7 에러 처리 표준

- API 에러 응답 표준: `{code, message, traceId}` (SPEC-CMS-003 §6).
- 사용자 메시지: `error_messages: en` (백엔드) → 클라이언트는 i18n 메시지 키로 매핑하여 사용자 언어로 표시.
- 토스트(Element Plus `ElMessage`) 또는 인라인 에러 (`role="alert"`).
- 네트워크 실패(`!err.response`): "네트워크 연결을 확인해주세요" 토스트 + 재시도 버튼.

### 7.8 분석/로깅 (1차 미적용 — 후속)

- Google Analytics, Hotjar 등 트래커: 1차 비범위(개인정보 처리방침 확정 후 도입).
- 클라이언트 에러 리포팅(Sentry): 1차 비범위. 후속 SPEC.

---

## 8. 인수 기준 (Acceptance Criteria 요약)

전체 Given-When-Then 시나리오는 `acceptance.md` 파일에 분리하여 정의한다. 본 절은 그룹별 카운트와 요지만 제시한다.

| 그룹 | 시나리오 수 | 요지 |
|------|-----------|------|
| A. 레이아웃·내비게이션 | 8 | PublicLayout 렌더링, 메뉴 트리 로드, 브레드크럼, 스킵 내비, 언어 토글, 다크모드 비활성, 햄버거 메뉴 |
| B. 공지·게시판 | 8 | 공지 목록 페이징, 카테고리 필터, 검색, 상단 고정 공지, 상세 본문 sanitize, 첨부 다운로드 서명 URL, FAQ 아코디언, Q&A 작성 인증 |
| C. 정책·안전 | 8 | 정책 목록 필터, 정책 상세 외부 링크 안전, 정책 매칭 폼(익명), 알림 구독 인증, 안전 가이드 체크리스트, 사고사례 공개 필터 |
| D. 검색·통계 | 6 | 통합 검색 결과 타입 탭, 하이라이트 mark 렌더링, 빈 결과 메시지, 검색어 히스토리, 공개 KPI 차트, 차트 데이터 테이블 fallback |
| E. 접근성·i18n | 8 | KWCAG 키보드 순회, ARIA 라벨, 스킵 내비 동작, 색대비, 폼 라벨, i18n ko/en 토글, 메뉴 라벨 i18n 재로드, 200% 확대 |
| F. 에러·엣지케이스 | 7 | 404 NotFound, 403 Forbidden, 500 ServerError, 점검 모드 강제 리다이렉트, 네트워크 실패 재시도, 빈 상태 EmptyState, API 부분 실패 |

**합계: 45개 시나리오** (RFP INR/SER/PER 요구사항 매핑 포함)

자동화 도구:

- **Vitest + @vue/test-utils**: A·B·D·E·F 중 컴포넌트 단위(라우터 가드, 스토어 액션, 컴포넌트 렌더링·상호작용)
- **Playwright (후속 1차 → 2차에서 본격)**: B·C·E·F의 E2E (실제 브라우저 네비게이션, 키보드 순회, 다국어)
- **axe-core**: E의 접근성 자동 검증 (Lighthouse CI + jest-axe 통합)

---

## 9. 구현 계획 (Implementation Plan 요약)

전체 마일스톤·기술 접근법·리스크는 `plan.md` 파일에 분리한다. 본 절은 단계 개요만 제시한다.

**Phase 0 (Foundation)** — Priority High
- API 클라이언트, 스토어 6종, 레이아웃, 라우터 가드, i18n locale 메시지 골격 작성

**Phase 1 (Core Pages — P0 — A·B 그룹)** — Priority High
- HomeView, NoticeListView, NoticeDetailView, BoardPostList/Detail, FaqView, QnaList/Detail/Create

**Phase 2 (Policy & Safety — A·B·C 그룹)** — Priority High
- PolicyList/Detail/Match/Subscription, SafetyGuideline/Incident, PublicationList/Detail

**Phase 3 (Search & Stats — D 그룹)** — Priority Medium
- SearchResultView, PublicStatsView, MediaGalleryView, SitemapView

**Phase 4 (Auth & Maintenance — F 그룹)** — Priority Medium
- LoginView, MyQnaListView, MaintenanceView, ErrorViews

**Phase 5 (Accessibility & i18n Polish — E 그룹)** — Priority High (병행)
- axe-core 통합, 키보드 순회 E2E, ko/en 전수 검증, 200% 확대 회귀

**Phase 6 (Performance & Build)** — Priority Medium
- 번들 분석, 코드 스플리팅 최적화, 이미지 lazy load, Lighthouse CI 통합

순서 원칙: Phase 0 → Phase 1 → (Phase 2, Phase 3 병렬 가능) → Phase 4 → Phase 5 → Phase 6.

---

## 10. 위험 및 대응

| ID | 위험 | 영향 | 완화 방안 |
|----|------|------|----------|
| RP-01 | SPA의 SEO 한계 (1차 CSR) | 검색 노출 저조 → 시민 접근성 저하 | 1차 prerender.io 또는 정적 메타 보완. 후속 Vite SSR 또는 Nuxt 마이그레이션 SPEC. |
| RP-02 | KWCAG 2.2 AA 미충족 발견 | 정부 사이트 요건 미달 → 출시 차단 | Phase 5에서 axe-core + Lighthouse 게이트 + 외부 인증기관 사전 점검(GS 인증 또는 한국정보화진흥원). |
| RP-03 | i18n 누락 문자열 | 사용자 혼선 | ESLint 규칙으로 raw 문자열 작성 금지(plugin-vue-i18n). PR 시 누락 검증. |
| RP-04 | 백엔드 API 응답 스키마 변경 | UI 깨짐 | OpenAPI 스펙 동기화(SPEC-CMS-005 system codes 외 표준 응답 포맷 준수). TypeScript 타입 자동 생성(후속). |
| RP-05 | 점검 모드 가드 무한 루프 | 사용자 페이지 진입 불가 | `/maintenance`와 `/error/*`는 가드 통과. 가드 자체 에러 시 fallback으로 홈 표시. |
| RP-06 | 토큰 만료 동시성 (다중 탭) | 401 다발 → UX 저하 | refresh 중복 호출 방지 락(클라이언트). LocalStorage `storage` 이벤트로 다른 탭에 토큰 갱신 알림. |
| RP-07 | 검색 PER-003 < 3s 미달 | RFP 게이트 미충족 | 클라이언트 측 디바운스 + 로딩 스켈레톤 + 백엔드 PostgreSQL FTS 최적화 의존. |
| RP-08 | 모바일 브라우저 호환 | iOS Safari, Android Chrome 미동작 | Vite `legacy` plugin + `core-js` polyfill. BrowserStack(또는 LambdaTest) 회귀 매트릭스. |
| RP-09 | 차트 라이브러리 미선정 (ECharts vs Chart.js) | Phase 3 진입 지연 | Phase 0에서 1차 결정. 데이터 테이블 fallback은 라이브러리 무관 구현. |
| RP-10 | 외부 신청 URL 보안 | 정책 상세의 외부 링크가 피싱 사이트로 변경됨 | URL 화이트리스트 또는 신뢰 도메인 마크. `rel="noopener noreferrer"` 강제. 클릭 전 도메인 표시. |

---

## 11. 비범위 정리 (Exclusions — What NOT to Build)

본 SPEC은 다음 항목을 명시적으로 **빌드하지 않는다**(범위 외):

1. **시민 회원가입 화면** — SPEC-CMS-002 운영자 회원만 정의. 시민 회원 가입은 SPEC-CMS-PUBLIC-002에서.
2. **SSO 어댑터** — RFP SFR-010. 백엔드 미구현. SPEC-CMS-SSO-001에서.
3. **댓글 작성** — 1차는 읽기 전용. 작성은 reCAPTCHA + 인증 후속.
4. **위지윅 에디터** — Q&A 작성은 plain textarea. 관리자만 Tiptap.
5. **다크 모드** — 후속.
6. **PWA / Service Worker** — 후속.
7. **카카오 알림톡 가입 UI** — 이메일·인앱만 1차.
8. **가상 시뮬레이션 (RFP SFR-003)** — SPEC-CMS-SIM-001에서.
9. **설문조사 응답 화면** — 후속.
10. **다크 패턴·광고·추적 트래커** — 정책상 영구 금지.
11. **한국어/영어 외 언어** — 1차 ko/en만.
12. **SSR/SSG** — 1차 CSR만. SEO 보강은 prerender + meta 동적 갱신 우회.
13. **신규 백엔드 API** — 본 SPEC은 SPEC-CMS-002~010 + MEDIA-001의 기존 Tested API를 소비. 신규 백엔드 엔드포인트는 정의하지 않음.

---

## 12. HISTORY

| 버전 | 일자 | 작성자 | 변경 내용 |
|------|------|--------|-----------|
| v0.1.0 | 2026-05-14 | manager-spec (MoAI) | 초안 작성. SPEC-CMS-001 §4.1 다중 SPA 아키텍처 두 번째 SPA(`frontend/public`) 시민 대상 공공 사이트 전체 화면·라우터·스토어·API 클라이언트·접근성 정책. 25개 라우트(P0 18개 + P1 5개 + 에러 2개). 9개 도메인 API 클라이언트 모듈(SPEC-CMS-002~010 + MEDIA-001 소비). PublicLayout 구조(헤더+브레드크럼+main+푸터+스킵내비+백투탑). 6개 Pinia 스토어(auth/menu/maintenance/search/locale/breadcrumb). KWCAG 2.2 AA P0 게이트(axe-core 0 critical + Lighthouse ≥ 95). i18n ko/en + 시스템 언어 감지. Tailwind 5종 breakpoint 반응형. SEO 1차 CSR + 동적 메타(후속 SSR). 보안 XSS 심층 방어 + JWT Bearer LocalStorage. 성능 LCP 2.5s + INP 200ms + 번들 300KB. 45개 인수 시나리오(A·B·C·D·E·F 6개 그룹). Phase 0~6 구현 계획. 위험 RP-01~10. 13개 비범위 명시. 신규 백엔드 API 정의 없음. |
