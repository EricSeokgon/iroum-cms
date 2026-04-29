# Session Memo

## 묶음 3 진행 상태 (2026-04-29)

### 완료 (Bundle C 핵심 4 도메인 백엔드)

- 21e694c: Step 1 RED — V13 마이그레이션(11 tables) + 4 도메인 골격 + 31 RED 테스트
- ea1c8c8: Step 2 GREEN — 32/32 테스트 통과
  - SiteServiceImpl, MenuServiceImpl, TemplateServiceImpl, PageServiceImpl, ContentBlockServiceImpl
  - jsoup 1.17.2 sanitize 적용

### 다음 세션 잔여 작업 (묶음 3 후속 또는 묶음 4 합병)

**4-A. Bundle C 잔여 도메인 GREEN (V13 DDL은 있음, 코드 없음)**
- popup 도메인 (REQ-CONTENT-008-D)
- banner 도메인 (REQ-CONTENT-009-D)
- i18n_resource 도메인 (REQ-CONTENT-010-D)
- seo_redirect 도메인 (REQ-CONTENT-005-D-8 일부는 Step 2에서 SeoRedirectMapper만 추가)
- sitemap.xml 생성기 (REQ-CONTENT-007-D)

**4-B. Caffeine 캐시 도입 (REQ-CONTENT-007-D-3)**
- 메뉴 트리 (TTL 5분), 페이지 본문 (TTL 10분), sitemap.xml (TTL 1시간)

**4-C. Frontend (SPEC-CMS-004)**
- Menu Tree UI (드래그앤드롭 + 권한 매핑 모달)
- Page Editor (Tiptap + 콘텐츠 블록 렌더러 + 미리보기 토큰)
- Page History 비교/롤백 UI
- Template Manager
- Popup/Banner Manager
- i18n Editor

### 세션 시작 명령

다음 세션 시작 시: `/moai run SPEC-CMS-004` (잔여 도메인) 또는 `/moai run SPEC-CMS-005` (묶음 4: 통계·시스템).

## 누적 상태

- 32 commits
- ~628 files
- ~52,500 LOC
- Backend tests: 461+ GREEN (기존 429 + Bundle C 32)
