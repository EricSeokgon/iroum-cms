# SPEC-CMS-PUBLIC-001: 인수 기준 (Acceptance Criteria)

본 문서는 SPEC-CMS-PUBLIC-001(시민 대상 공공 사이트 SPA)의 인수 기준을 Given-When-Then 형식으로 정의한다.

총 45개 시나리오를 6개 그룹(A~F)으로 분류한다. 각 시나리오는 자동화 도구를 명시한다:

- **[VT]** Vitest + @vue/test-utils (단위·컴포넌트)
- **[VT+AXE]** Vitest + jest-axe (접근성)
- **[PW]** Playwright (E2E — 1차는 단위로 대체, 2차에서 본격)
- **[LH]** Lighthouse CI (성능·접근성·SEO)
- **[MAN]** 수동 검증 (시각적·스크린리더)

라우트·API 식별자는 `spec.md` §4·§6과 일치한다.

---

## A. 레이아웃·내비게이션 (8 시나리오)

### A-01. PublicLayout 렌더링 [VT]

**Given** 사용자가 시민 사이트의 임의 라우트(`/`, `/notices`, `/policies` 등)에 접근하고

**When** 페이지가 마운트되었을 때

**Then** 시스템은 `<header role="banner">` + `<nav aria-label="주메뉴">` + `<main id="main-content" role="main" tabindex="-1">` + `<footer role="contentinfo">` 4개 랜드마크를 모두 렌더링해야 하며, `<a href="#main-content">본문 바로가기</a>` 스킵 내비가 첫 번째 포커스 가능 요소로 존재해야 한다.

### A-02. 메뉴 트리 로드 [VT]

**Given** 사용자가 사이트에 처음 방문하고 menuStore가 비어 있을 때

**When** PublicLayout이 마운트되며 `menuStore.loadMenuTree()`가 호출되었을 때

**Then** 시스템은 `GET /api/v1/menus?siteCode=public&depth=2`를 호출하여 응답을 menuStore에 저장하고, PublicHeader는 1단계 메뉴 5~7개를 인라인으로(태블릿+) 또는 햄버거 아이콘 클릭 시(모바일) 렌더링해야 한다. 이후 60초 이내 재방문 시 캐시된 데이터를 재사용하고 API를 재호출하지 않아야 한다.

### A-03. 브레드크럼 표시 [VT]

**Given** 사용자가 `/notices/123` 라우트에 진입하고 라우트 메타 `breadcrumb: ['home', 'notice.list', 'notice.detail']`가 설정되었을 때

**When** PublicBreadcrumb가 렌더링되었을 때

**Then** 시스템은 `<nav aria-label="현재 위치">` 안에 `<ol>` + 3개 `<li>`를 렌더링하고, 마지막 항목에는 `aria-current="page"`를 설정해야 한다. 처음 2개는 클릭 가능한 링크(홈, 공지 목록)여야 한다.

### A-04. 스킵 내비 동작 [VT+AXE]

**Given** 사용자가 페이지에 진입한 직후

**When** Tab 키를 한 번 누르면

**Then** 첫 번째로 PublicSkipNav 링크에 포커스가 위치하고(`.sr-only`가 `:focus`로 가시화되어 화면에 노출), 사용자가 Enter를 누르면 포커스가 `<main id="main-content" tabindex="-1">`으로 이동하여 본문 영역으로 점프해야 한다.

### A-05. 언어 토글 [VT]

**Given** 사용자가 시민 사이트에서 한국어 locale(`ko`)로 페이지를 보고 있고

**When** PublicHeader 우측의 언어 토글 버튼에서 `English`를 선택하면

**Then** 시스템은 (a) `useI18n().locale.value`를 `en`으로 변경 (b) LocalStorage `public.locale`에 `en` 저장 (c) `menuStore.reload()`로 영어 메뉴 라벨 재로드 (d) `document.title`을 i18n 변환하여 갱신 (e) 모든 가시적 텍스트(메뉴, 버튼, 헤더, 푸터)가 영어로 즉시 갱신되어야 한다.

### A-06. 다크 모드 비활성 검증 [VT]

**Given** 사용자가 OS 설정에서 `prefers-color-scheme: dark`를 활성화한 상태에서 사이트에 접근하고

**When** PublicLayout이 마운트되었을 때

**Then** 시스템은 라이트 모드로만 렌더링해야 한다(1차 비범위, spec.md §3.2). `<html>` 또는 `<body>`에 `.dark` 클래스가 적용되지 않으며, Tailwind `dark:` variant 스타일이 적용되지 않아야 한다.

### A-07. 모바일 햄버거 메뉴 [VT]

**Given** 사용자가 모바일 폭(`< 640px`)에서 시민 사이트에 접근하고

**When** PublicHeader의 햄버거 아이콘 버튼(`aria-label="메뉴 열기"`, `aria-expanded="false"`)을 클릭하면

**Then** 시스템은 풀스크린 오버레이로 메뉴 트리를 표시하고, 버튼 `aria-expanded`를 `true`로 갱신하며, 닫기 버튼(`aria-label="메뉴 닫기"`) 또는 Esc 키로 닫을 수 있어야 한다. 오버레이 노출 중에는 본문 스크롤이 잠겨야 한다(`overflow: hidden`).

### A-08. 백투탑 버튼 [VT]

**Given** 사용자가 페이지를 400px 이상 스크롤한 상태에서

**When** PublicLayout의 BackToTopButton이 가시화되었을 때

**Then** 버튼은 `aria-label="맨 위로"`를 가지고 클릭 시 `window.scrollTo({ top: 0, behavior: 'smooth' })`를 실행해야 한다. 400px 미만으로 다시 스크롤하면 버튼이 fade-out으로 사라져야 한다.

---

## B. 공지·게시판·FAQ·Q&A (8 시나리오)

### B-01. 공지 목록 페이징 [VT]

**Given** 사용자가 `/notices`에 진입하고 백엔드에 공지가 35건 존재하며 페이지 크기 20일 때

**When** 페이지가 마운트되어 `GET /api/v1/notices?page=0&size=20`을 호출하면

**Then** 시스템은 20건의 공지 카드/행을 렌더링하고 페이지네이션 바에 `1 / 2` 표시 + `다음 페이지` 버튼을 활성화해야 한다. `다음 페이지` 버튼 클릭 시 `?page=1`로 URL 갱신하고 나머지 15건을 표시해야 한다.

### B-02. 공지 카테고리·키워드 검색 [VT]

**Given** 사용자가 `/notices`에서 카테고리 select에서 `EVENT`를 선택하고 검색 입력에 `세미나`를 입력 후 Enter를 누르면

**When** 검색이 제출되었을 때

**Then** 시스템은 `GET /api/v1/notices?categoryCode=EVENT&keyword=세미나&page=0&size=20`을 호출하고 URL을 `/notices?category=EVENT&keyword=세미나`로 갱신해야 한다. 결과가 0건일 때 EmptyState 컴포넌트("검색 결과가 없습니다") + 검색 초기화 버튼이 표시되어야 한다.

### B-03. 공지 상단 고정 [VT]

**Given** 백엔드 공지 목록 응답이 `{notices: [고정공지 2건], content: [일반공지 18건], totalElements: 35, page: 0}`일 때

**When** NoticeListView가 렌더링되었을 때

**Then** 시스템은 페이지 0에서만 고정 공지 2건을 별도 섹션(시각적 강조 + `aria-label="고정 공지"`)으로 상단에 노출하고, 일반 공지 18건을 그 아래에 표시해야 한다. 페이지 1 이상에서는 고정 공지 섹션을 숨겨야 한다.

### B-04. 공지 상세 본문 sanitize [VT]

**Given** 백엔드가 `content_html: "<p>본문</p><script>alert('xss')</script><img src=x onerror=alert(1)>"`를 반환할 때(서버 sanitize 우회 가정의 회귀 테스트)

**When** NoticeDetailView가 본문을 `v-html`로 렌더링하면

**Then** 시스템은 DOMPurify 클라이언트 심층 방어를 거쳐 `<p>본문</p>`만 렌더링하고 `<script>` 및 `onerror` 핸들러는 제거되어야 한다. `<img>`는 `src=x` 그대로 또는 제거(정책에 따라). 렌더링 후 DOM에 `<script>` 노드가 존재하지 않아야 한다.

### B-05. 첨부 다운로드 서명 URL [VT]

**Given** 사용자가 `/notices/123` 상세 페이지에서 첨부파일 "보고서.pdf"를 클릭할 때

**When** AttachmentDownload 컴포넌트가 `POST /api/v1/attachments/456/download-url`을 호출하면

**Then** 시스템은 응답 `{url: "/api/v1/attachments/456/download?token=...&expires=...&sig=...", expiresAt: "..."}`를 받아 즉시 `window.location.href = url`로 다운로드를 트리거해야 한다. 응답이 403(권한 없음) 또는 423(`FILE_NOT_READY`) 시 적절한 에러 토스트("권한이 없습니다" 또는 "파일 검사 중입니다") 표시 + 다운로드 미실행해야 한다.

### B-06. FAQ 아코디언 키보드 조작 [VT+AXE]

**Given** 사용자가 `/faqs`에 진입하여 카테고리 `ACCOUNT`를 선택하면 8개 FAQ가 표시될 때

**When** Tab으로 첫 번째 FAQ 헤더에 포커스 후 Enter 또는 Space를 누르면

**Then** 해당 헤더(`<button aria-expanded="false">`)가 `aria-expanded="true"`로 갱신되고 본문이 펼쳐지며 `aria-hidden`이 `false`로 변경되어야 한다. 다시 Enter/Space로 닫을 수 있어야 한다. 모든 FAQ 헤더는 Tab 순회로 접근 가능해야 한다.

### B-07. Q&A 작성 인증 가드 [VT]

**Given** 비인증 사용자가 `/qnas/new`에 직접 접근할 때

**When** 라우터의 authGuard가 실행되면

**Then** 시스템은 `router.replace({ name: 'login', query: { redirect: '/qnas/new' } })`로 리다이렉트해야 한다. 로그인 성공 후 `router.replace('/qnas/new')`로 자동 복귀해야 한다.

### B-08. Q&A 비공개 게시글 접근 [VT]

**Given** 사용자 A가 작성한 `is_private=true` Q&A(id=789)가 존재하고 다른 사용자 B가 인증 상태로 `/qnas/789`에 접근할 때

**When** `GET /api/v1/qnas/789`가 호출되어 백엔드가 404 `QNA_NOT_FOUND`를 반환하면(SPEC-CMS-003 REQ-BOARD-008-D-3)

**Then** 시스템은 본문이 아닌 NotFoundView(404 페이지)를 표시해야 한다. 403(존재함을 노출)을 표시하지 않아야 한다.

---

## C. 정책·안전·발간자료 (8 시나리오)

### C-01. 정책 목록 다중 필터 [VT]

**Given** 사용자가 `/policies`에서 PolicyFilterBar의 업종 multiselect에서 `IT`, 지역 select에서 `서울`, 유형 checkbox에서 `자금지원`을 선택할 때

**When** 필터가 적용되면

**Then** 시스템은 `GET /api/v1/policies?industry=IT&region=서울&type=자금지원&page=0&size=20`을 호출하고 URL을 동일한 query string으로 갱신해야 한다. 필터 초기화 버튼 클릭 시 모든 필터가 제거되고 `/policies`로 URL이 단순화되어야 한다.

### C-02. 정책 상세 외부 신청 링크 안전 [VT]

**Given** 정책 상세에 `applyUrl: "https://gov.kr/apply/123"`이 포함되었을 때

**When** PolicyDetailView가 신청 CTA 버튼을 렌더링하면

**Then** 버튼은 `<a href="https://gov.kr/apply/123" target="_blank" rel="noopener noreferrer">신청하러 가기</a>` 형식이어야 하며, 사용자에게 도메인(`gov.kr`)을 별도 표시(`title` 또는 보조 텍스트)해야 한다. `applyUrl`이 `javascript:`, `data:`, `file:` 스킴이거나 `http`/`https`가 아닐 경우 버튼이 비활성화되고 "신청 링크 확인 중입니다" 메시지를 표시해야 한다.

### C-03. 정책 매칭 익명 가능 [VT]

**Given** 비인증 사용자가 `/policies/match`에 접근하고 PolicyMatchForm에 (업종: IT, 자본금: 1억, 매출: 5억, 직원: 10명, 지역: 서울)을 입력할 때

**When** 폼 제출 시 `POST /api/v1/policies/match`가 호출되면

**Then** 시스템은 인증 헤더 없이 요청하고(`Authorization` 헤더 미첨부), 응답으로 TOP-10 매칭 결과(`[{policyId, score, reason}]`)를 받아 PolicyCard 형식으로 표시해야 한다. 401 응답이 와도 `/login`으로 리다이렉트하지 않아야 한다(`requiresAuth=false` 라우트).

### C-04. 정책 알림 구독 인증 필요 [VT]

**Given** 사용자가 정책 상세에서 "알림 구독" 버튼을 클릭할 때

**When** `policySubscriptionStore.subscribe(policyId)`가 호출되고 비인증 상태이면

**Then** 시스템은 `router.push({ name: 'login', query: { redirect: '/policies/subscriptions?policyId=123' } })`로 리다이렉트해야 한다. 로그인 후 자동 복귀 + 구독 모달 자동 오픈해야 한다.

### C-05. 안전 가이드 체크리스트 렌더링 [VT+AXE]

**Given** SafetyGuidelineDetailView가 `GET /api/v1/safety/guidelines/45` 응답으로 12개 체크리스트 항목을 받았을 때

**When** SafetyChecklist 컴포넌트가 렌더링되면

**Then** `<ul role="list">` + 12개 `<li>` + 각 항목 `<input type="checkbox" disabled aria-label="...항목명...">` 형식으로 출력해야 한다(1차는 읽기 전용, 사용자 입력 저장 비범위). 인쇄 버튼 클릭 시 `window.print()` 호출 + 인쇄 전용 CSS로 헤더·푸터·내비 숨김.

### C-06. 사고사례 공개 필터 [VT]

**Given** 백엔드 `GET /api/v1/safety/incidents?page=0&size=20`이 공개 사례만 반환(서버측 필터)할 때

**When** SafetyIncidentListView가 렌더링되면

**Then** 응답된 모든 사례는 `is_public=true`(또는 공개 분류)인 사례만 표시되어야 한다. 비공개 사례는 카드 자체가 렌더링되지 않아야 한다. 업종 select 필터 적용 시 `?industryCode=...`를 추가하여 재호출.

### C-07. 발간자료 다중 필터 [VT]

**Given** 사용자가 `/publications`에서 연도 select에서 `2025`, 문서종류 radio에서 `RESEARCH`, 카테고리 트리에서 `통계연구`를 선택할 때

**When** 필터가 적용되면

**Then** 시스템은 `GET /api/v1/publications?year=2025&documentType=RESEARCH&categoryId=12&page=0&size=20`을 호출하고 결과를 PublicationCard(또는 행) 형식으로 표시해야 한다. 응답이 `{thumbnailUrl, title, publicationYear, downloadCount}` 정보를 포함하면 카드에 모두 노출.

### C-08. 발간자료 압축 다운로드 [VT]

**Given** 사용자가 `/publications/789` 상세에서 첨부 5개 중 3개를 체크박스로 선택 후 "선택 다운로드" 버튼을 클릭할 때

**When** `POST /api/v1/posts/789/download-zip {attachmentIds: [1,2,3]}`이 호출되면

**Then** 합계 ≤ 50MB일 때 즉시 zip 응답으로 다운로드되고, 합계 > 50MB일 때 응답 `{jobId}` 수신 후 "준비 중입니다. 완료 시 알림이 발송됩니다" 토스트를 표시해야 한다. 합계 > 500MB일 때 백엔드가 400을 반환하면 "500MB를 초과합니다" 에러 메시지를 표시해야 한다(SPEC-CMS-003 REQ-BOARD-012-D-4).

---

## D. 검색·통계·미디어 (6 시나리오)

### D-01. 통합 검색 결과 타입 탭 [VT]

**Given** 사용자가 헤더 검색박스에 `안전 가이드`를 입력하고 Enter를 누르면

**When** 라우터가 `/search?q=안전 가이드&type=ALL`로 이동하고 `GET /api/v1/search?q=안전 가이드&types=POST,FAQ,QNA,POLICY,SAFETY`가 호출되면

**Then** SearchResultView는 SearchFilterTabs로 5개 타입 탭(전체/공지·게시글/FAQ/Q&A/정책/안전)을 표시하고 각 탭에 결과 카운트 배지를 노출해야 한다. 사용자가 "안전" 탭 클릭 시 URL을 `?type=SAFETY`로 갱신하고 SAFETY 타입 결과만 표시.

### D-02. 검색 결과 하이라이트 mark 렌더링 [VT]

**Given** 백엔드가 `snippet: "안전관리 <mark>가이드</mark>라인을 준수하여..."`를 반환할 때

**When** SearchResultCard가 snippet을 `v-html`로 렌더링하면

**Then** `<mark>` 태그가 그대로 시각적으로 강조(노란 배경)되어 표시되고, snippet 내 다른 HTML 태그(`<script>`, `<a>` 등)는 클라이언트 측 strip 처리로 제거되어야 한다. DOMPurify 사용 시 `ALLOWED_TAGS: ['mark']`로 제한.

### D-03. 검색 빈 결과 메시지 [VT]

**Given** 사용자가 `/search?q=xyzabc1234nonexistent`로 진입하고 응답이 `{totalElements: 0, results: []}`일 때

**When** SearchResultView가 렌더링되면

**Then** EmptyState 컴포넌트가 "'xyzabc1234nonexistent'에 대한 검색 결과가 없습니다" 메시지(검색어 강조) + 검색 팁("2자 이상 입력", "다른 키워드 시도") + 인기 검색어(후속, 1차는 미표시) 형식으로 표시되어야 한다.

### D-04. 검색어 히스토리 [VT]

**Given** 사용자가 SearchInput에 `정책`, `안전`, `발간자료` 순으로 검색을 수행한 후

**When** 빈 SearchInput에 포커스를 두면

**Then** 자동완성 드롭다운이 최근 검색어 3개를 표시해야 한다(`정책`, `안전`, `발간자료` — 최신순). LocalStorage `public.search.history`에 최대 5개까지 보관. 동일 검색어 중복 시 최상단으로 이동. 사용자가 X 아이콘으로 개별 항목 또는 전체 삭제 가능해야 한다.

### D-05. 공개 KPI 차트 + 데이터 테이블 fallback [VT+AXE]

**Given** PublicStatsView가 `GET /api/v1/dashboard/widgets/public-stats/data` 응답으로 4개 위젯 데이터를 받았을 때

**When** KpiChart 컴포넌트가 렌더링되면

**Then** 각 차트(예: bar/line)는 시각적으로 렌더링되고 동시에 `<table aria-label="{차트 제목} 데이터">`로 동등한 데이터를 제공해야 한다. 데이터 테이블은 시각적으로 숨기되(`.sr-only` 또는 `<details><summary>차트 데이터 보기</summary><table>...</table></details>`) 스크린리더는 인식 가능해야 한다. axe-core 검사 통과.

### D-06. 미디어 갤러리 lazy load [VT]

**Given** MediaGalleryView가 100개 이미지 메타를 응답으로 받았을 때

**When** 첫 화면에서 가시 범위(viewport) 내 9개 카드가 렌더링되면

**Then** 가시 범위 이미지는 `<img loading="lazy" decoding="async" src="..." alt="...">` 속성으로 즉시 로드되고, 스크롤로 가시 범위 밖 이미지는 브라우저 native lazy loading에 의해 지연 로드되어야 한다. 동영상은 썸네일만 표시하고 클릭 시 모달로 video 재생.

---

## E. 접근성·i18n·반응형 (8 시나리오)

### E-01. 키보드 단독 조작 — 전체 페이지 Tab 순회 [VT+AXE]

**Given** 사용자가 마우스 없이 키보드만 사용하여 `/`에 진입할 때

**When** Tab 키를 반복하여 모든 인터랙티브 요소를 순회하면

**Then** 스킵 내비 → 로고 → 메인 메뉴 → 검색 → 언어 토글 → 로그인/사용자 메뉴 → 본문 인터랙티브 요소들 → 푸터 링크들 순서로 포커스가 이동해야 하며, 모든 포커스 가능 요소는 `:focus-visible` outline(4.5:1 색대비)으로 시각적으로 강조되어야 한다. 모든 버튼/링크는 Enter(또는 버튼은 Space로도) 활성화 가능해야 한다.

### E-02. ARIA 라벨 누락 검증 [VT+AXE]

**Given** 임의의 시민 사이트 페이지가 렌더링되었을 때

**When** axe-core가 검사를 실행하면

**Then** `button-name`, `link-name`, `image-alt`, `label`, `aria-allowed-attr`, `aria-required-attr` 규칙에서 critical 위반 0건이어야 한다. 아이콘 단독 버튼(검색 돋보기, 햄버거, 닫기 X)은 `aria-label="검색 열기"`, `aria-label="메뉴 열기"`, `aria-label="닫기"` 등 한국어 라벨을 가져야 한다.

### E-03. 색대비 4.5:1 (본문) / 3:1 (큰 텍스트) [VT+AXE]

**Given** 모든 시민 사이트 페이지가 렌더링되었을 때

**When** axe-core `color-contrast` 규칙 검사를 실행하면

**Then** 본문 텍스트(16px 미만 또는 일반 두께)는 배경 대비 4.5:1 이상, 큰 텍스트(18px 이상 또는 14px 이상 굵게)는 3:1 이상이어야 한다. critical 위반 0건. Tailwind `text-gray-700 on bg-white`(8.6:1), `text-blue-600 on bg-white`(8.59:1) 기본 조합 사용.

### E-04. 폼 라벨 연결 [VT+AXE]

**Given** PolicyMatchForm 또는 QnaCreateView가 렌더링되었을 때

**When** 사용자가 입력 필드에 포커스하면

**Then** 모든 `<input>`/`<textarea>`/`<select>`는 (a) `<label for="id">` 연관 (b) 에러 메시지는 `aria-describedby="error-id"` 연관 (c) required 필드는 `aria-required="true"` 속성을 가져야 한다. 스크린리더가 라벨 + 에러 메시지를 함께 읽도록 보장.

### E-05. i18n ko/en 토글 전 페이지 갱신 [VT]

**Given** 사용자가 한국어 locale로 `/policies/123` 정책 상세 페이지를 보고 있을 때

**When** 언어 토글 버튼으로 English를 선택하면

**Then** (a) 페이지 타이틀(`document.title`) (b) 헤더 메뉴 라벨 (c) 푸터 텍스트 (d) 페이지 내 정적 라벨(필드명, 버튼 텍스트) (e) PublicBreadcrumb 라벨 모두 영어로 즉시 갱신되어야 한다. 동적 데이터(정책명, 본문)는 백엔드 응답의 적절한 locale 필드(`name_en`, `description_en`)가 있으면 영어로, 없으면 한국어로 fallback. 새로고침 후에도 LocalStorage `public.locale=en` 유지로 영어 상태 보존.

### E-06. 메뉴 라벨 i18n 재로드 [VT]

**Given** 사용자가 한국어로 메뉴 트리를 로드한 후 영어로 토글할 때

**When** localeStore가 변경되면 menuStore의 watcher가 트리거되어 `menuStore.reload()`가 호출되면

**Then** 시스템은 `GET /api/v1/menus?siteCode=public&depth=2&lang=en`(또는 헤더 `Accept-Language: en`)을 호출하여 영어 메뉴 라벨을 받아 PublicHeader가 즉시 갱신되어야 한다. 캐시는 무효화되어야 한다.

### E-07. 200% 확대 가로 스크롤 미발생 [MAN]

**Given** 사용자가 Chrome DevTools에서 페이지 줌을 200%로 설정하고 데스크탑 폭(1280px)에서 모든 P0 페이지를 순회할 때

**When** 각 페이지가 렌더링되면

**Then** 가로 스크롤바가 나타나지 않아야 하며(`overflow-x: hidden` 강제 + 모든 컨테이너 `max-width` 또는 flex/grid 반응형), 본문 텍스트는 잘림 없이 줄바꿈되어야 한다. KWCAG 1.4.10 매핑.

### E-08. 모바일 반응형 전환 (sm/md/lg) [VT]

**Given** 뷰포트 폭을 320px(모바일), 768px(태블릿), 1024px(데스크탑) 순으로 변경할 때

**When** PublicLayout이 리렌더링되면

**Then** (a) 320px: 햄버거 메뉴 + 1열 카드 그리드 + 푸터 1열 (b) 768px: 인라인 메뉴 + 2열 카드 그리드 + 푸터 2열 (c) 1024px: 인라인 메뉴 + 3열 카드 그리드 + 푸터 3열 형식으로 자동 전환되어야 한다. 모든 터치 영역(버튼, 링크)은 최소 44x44px (KWCAG 2.5.5).

---

## F. 에러·엣지케이스 (7 시나리오)

### F-01. 404 NotFound 라우트 [VT]

**Given** 사용자가 존재하지 않는 경로(예: `/nonexistent-page-xyz`)에 진입할 때

**When** Vue Router의 `:pathMatch(.*)*` 라우트가 매칭되면

**Then** NotFoundView가 표시되어 (a) "페이지를 찾을 수 없습니다" h1 메시지 (b) 사용자 친화 안내 텍스트 (c) "홈으로 이동" 버튼 (d) "이전 페이지" 버튼 (e) "통합 검색" 링크가 노출되어야 한다. `document.title`이 "페이지를 찾을 수 없습니다 | iroum-cms"로 갱신되어야 한다.

### F-02. 403 Forbidden 자동 진입 [VT]

**Given** 사용자가 권한 없는 API를 호출하여 백엔드가 403 응답을 반환할 때

**When** axios 응답 인터셉터가 403을 감지하면

**Then** 시스템은 `router.push({ name: 'forbidden' })`로 ForbiddenView로 진입하고(`/error/403`), 화면에 "권한이 없습니다" 메시지 + 홈으로 이동 버튼이 표시되어야 한다. 단, `requiresAuth=true` 라우트는 401만 로그인으로 리다이렉트하고 403은 forbidden 페이지로 분리한다.

### F-03. 500 ServerError fallback [VT]

**Given** 사용자가 페이지 진입 시 API 호출이 5xx 응답을 반환할 때

**When** axios 응답 인터셉터가 5xx를 감지하면

**Then** 시스템은 `router.push({ name: 'server-error' })`로 ServerErrorView로 진입하고 "일시적인 오류가 발생했습니다" 메시지 + "다시 시도" 버튼(현재 라우트 재시도) + "홈으로" 버튼이 표시되어야 한다. 단, 폼 제출(`POST/PUT/DELETE`)의 5xx는 인터셉터에서 라우팅하지 않고 컴포넌트가 ElMessage 토스트로 처리.

### F-04. 점검 모드 강제 리다이렉트 [VT]

**Given** 백엔드 `GET /api/v1/system/health` 응답이 `{maintenanceMode: true, until: "2026-05-14T18:00:00+09:00", reason: "..."}`일 때

**When** maintenanceGuard가 실행되어 사용자가 `/notices` 등 일반 라우트에 진입하려 하면

**Then** 시스템은 `router.replace({ name: 'maintenance' })`로 강제 리다이렉트해야 한다. `/maintenance`와 `/error/*` 자체는 가드를 통과해야 한다(무한 루프 방지). MaintenanceView는 5분마다 자동 새로고침으로 점검 종료 감지 시 홈으로 자동 복귀.

### F-05. 네트워크 실패 재시도 [VT]

**Given** 사용자가 `/notices`에 진입할 때 백엔드가 네트워크 단절(`!err.response`, axios timeout) 상태일 때

**When** API 호출이 실패하면

**Then** NoticeListView는 ErrorState 컴포넌트("네트워크 연결을 확인해주세요" + "다시 시도" 버튼)를 렌더링해야 한다. "다시 시도" 클릭 시 동일 API 재호출 + 성공 시 정상 목록 표시. 최대 3회 재시도 후에도 실패 시 "지속적으로 실패합니다. 잠시 후 다시 시도해주세요" 메시지로 변경.

### F-06. 빈 상태 EmptyState 일관성 [VT]

**Given** 임의의 목록 페이지(공지/정책/안전/검색 등)에서 결과 0건 응답일 때

**When** 페이지가 렌더링되면

**Then** EmptyState 컴포넌트가 (a) 적절한 아이콘(box, search-empty 등) (b) 컨텍스트 메시지("검색 결과가 없습니다", "등록된 정책이 없습니다" 등) (c) 액션 버튼(필터 초기화, 홈으로) 형식으로 일관되게 표시되어야 한다. 모든 EmptyState는 `role="status"` 또는 `aria-live="polite"`로 스크린리더에 알림.

### F-07. API 부분 실패 (Promise.allSettled) [VT]

**Given** HomeView가 5개 섹션(히어로/공지/정책/빠른링크/KPI)을 위해 4개 API를 병렬 호출할 때

**When** 정책 API만 5xx 실패하고 나머지는 성공하면

**Then** HomeView는 (a) 공지 섹션 정상 표시 (b) 정책 섹션 ErrorState("일부 정보를 불러오지 못했습니다" + 재시도 버튼) (c) 빠른링크 정상 (d) KPI 정상 형식으로 부분 렌더링되어야 한다. 전체 페이지를 ServerErrorView로 redirect하지 않아야 한다(중요한 사용자 경험). `Promise.allSettled` 사용.

---

## 그룹별 시나리오 카운트 검증

| 그룹 | 시나리오 수 | 검증 도구 |
|------|-----------|----------|
| A. 레이아웃·내비게이션 | 8 (A-01 ~ A-08) | VT, VT+AXE, MAN |
| B. 공지·게시판·FAQ·Q&A | 8 (B-01 ~ B-08) | VT, VT+AXE |
| C. 정책·안전·발간자료 | 8 (C-01 ~ C-08) | VT, VT+AXE |
| D. 검색·통계·미디어 | 6 (D-01 ~ D-06) | VT, VT+AXE |
| E. 접근성·i18n·반응형 | 8 (E-01 ~ E-08) | VT+AXE, MAN |
| F. 에러·엣지케이스 | 7 (F-01 ~ F-07) | VT |
| **합계** | **45** | — |

---

## Definition of Done — 인수기준 통과 기준

본 SPEC의 45개 시나리오는 다음 기준을 모두 충족할 때 통과로 간주한다:

1. **자동화 도구별 통과**:
   - [VT] / [VT+AXE]: Vitest 단위·컴포넌트 테스트 통과 (실제 코드 실행, mock 응답)
   - [PW]: Playwright E2E 통과 (1차는 단위로 대체 가능, 2차에서 필수)
   - [LH]: Lighthouse CI 임계값 통과 (Accessibility ≥ 95, Performance ≥ 80)
   - [MAN]: 수동 검증 시 명시된 행위 100% 재현

2. **회귀 보호**:
   - 모든 자동화 시나리오는 CI 파이프라인 통과 필수
   - PR 머지 전 전체 시나리오 재실행

3. **접근성 게이트**:
   - axe-core 0 critical 위반 (E 그룹 + 전체 페이지)
   - 키보드 순회 검증 (E-01) 통과
   - 색대비 검증 (E-03) 통과

4. **성능 게이트**:
   - 검색 응답 < 3s (PER-003 — D-01 수동 측정)
   - LCP ≤ 2.5s 데스크탑 (홈 + 검색 결과)
   - 번들 크기 ≤ 300KB (initial JS gzip)

5. **i18n 게이트**:
   - ko/en 메시지 키 누락 0건 (E-05, E-06 자동 검증 스크립트)

6. **외부 인증**(선택):
   - KWCAG 2.2 AA 외부 인증기관(한국정보화진흥원 또는 GS인증) 사전 점검 통과 시 추가 가산.
