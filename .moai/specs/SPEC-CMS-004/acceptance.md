# SPEC-CMS-004 인수 기준 (Acceptance Criteria)

## 형식

각 sub-REQ에 대해 Given-When-Then(G/W/T) 시나리오를 정의한다. 통과 기준은 자동화 테스트(JUnit 5 + Testcontainers, Vitest, Playwright + axe-core) 실행 가능 형태로 작성한다.

---

## A. 사이트 (REQ-CONTENT-003-D)

### AC-003-1 단일 사이트 시드 (REQ-CONTENT-003-D-1)
- **Given** Flyway V1 마이그레이션 직후 site 테이블 상태에서
- **When** `SELECT COUNT(*) FROM site WHERE code='MAIN'` 을 실행하면
- **Then** 결과는 정확히 1행이고, default_language='ko', status='ACTIVE'.

### AC-003-2 호스트 기반 사이트 조회 (REQ-CONTENT-003-D-2)
- **Given** site.domain='www.example.go.kr'
- **When** Host: www.example.go.kr 헤더로 `GET /api/v1/content/sites/current` 호출
- **Then** 200 OK + 본문에 site.code='MAIN'.

### AC-003-3 호스트 미일치 시 기본 사이트 (REQ-CONTENT-003-D-2)
- **Given** site에 매칭 도메인 없음
- **When** Host: www.unknown.kr 으로 호출
- **Then** 200 OK + 본문에 default site(MAIN).

### AC-003-4 멀티사이트 비활성 시 생성 거부 (REQ-CONTENT-003-D-3)
- **Given** SYSADMIN 인증 + 멀티사이트 옵션 OFF(1차 기본)
- **When** `POST /api/v1/content/sites` 호출
- **Then** 409 Conflict + `{"code":"SITE_MULTI_DISABLED"}`.

---

## B. 메뉴 트리 (REQ-CONTENT-001-D)

### AC-MENU-1 메뉴 생성 (001-D-1)
- **Given** CONTENT_ADMIN 인증 + parent 메뉴 존재(depth=2)
- **When** `POST /api/v1/content/menus` (parent_id=2, code='SUB1', name='하위', sort_order=10)
- **Then** 201 + 응답 본문에 depth=3, path='/{root_id}/2/{new_id}', sort_order=10.

### AC-MENU-2 깊이 5 초과 거부 (001-D-1)
- **Given** depth=5 메뉴 존재
- **When** 그 자식으로 새 메뉴 생성 요청
- **Then** 400 + `{"code":"MENU_DEPTH_EXCEEDED"}`.

### AC-MENU-3 site 내 code 중복 거부 (001-D-1)
- **Given** site_id=1에 code='ABOUT' 메뉴 존재
- **When** 동일 site_id, code='ABOUT' 로 신규 생성
- **Then** 409 + `{"code":"MENU_CODE_DUPLICATE"}`.

### AC-MENU-4 트리 응답 정렬 (001-D-2)
- **Given** 동일 parent에 sort_order=20, 10, 30 인 자식 3개
- **When** `GET /api/v1/content/menus/tree`
- **Then** children 배열이 [10, 20, 30] 순서.

### AC-MENU-5 순서 변경 (001-D-3)
- **Given** 자식 [A:10, B:20, C:30]
- **When** B를 sort_order=5로 PATCH
- **Then** 응답 트리에서 [B, A, C] 순서.

### AC-MENU-6 순환 참조 거부 (001-D-4)
- **Given** A(parent=null), B(parent=A), C(parent=B)
- **When** A를 C의 자식으로 이동(`PATCH .../move parent_id=C`)
- **Then** 400 + `{"code":"MENU_CYCLE_DETECTED"}`.

### AC-MENU-7 메뉴 이동 후 자손 path 갱신 (001-D-4)
- **Given** A(/1)의 자식 B(/1/5), 손자 C(/1/5/9)
- **When** B를 새 부모 D(/2)의 자식으로 이동
- **Then** B.path='/2/5', C.path='/2/5/9' 로 일괄 갱신.

### AC-MENU-8 가시성 토글 후 캐시 무효화 (001-D-5)
- **Given** menu:tree:1 캐시 활성 상태
- **When** 메뉴 가시성 PATCH
- **Then** 캐시 키 menu:tree:1 제거 + 다음 트리 조회는 DB 재조회.

### AC-MENU-9 삭제 시 자손 cascade (001-D-6)
- **Given** A에 자손 3개
- **When** `DELETE /content/menus/A`
- **Then** A 및 자손 모두 삭제, 연결된 page.menu_id=NULL.

---

## C. 메뉴 권한 (REQ-CONTENT-002-D)

### AC-PERM-1 매핑 (002-D-1)
- **Given** menu_id=10, permission_code 배열 ['MENU:ADMIN','BOARD:ADMIN']
- **When** `POST /api/v1/content/menus/10/permissions` 호출
- **Then** menu_permissions 테이블에 2행 존재.

### AC-PERM-2 사용자 트리 필터 (002-D-2)
- **Given** 메뉴 X에 'BOARD:ADMIN' 매핑, 사용자는 BOARD:READ만 보유
- **When** `GET /content/menus/tree?context=USER` 호출
- **Then** 응답 트리에 메뉴 X 미포함 또는 accessible:false.

### AC-PERM-3 권한 미정의 메뉴 인증자 공개 (002-D-3)
- **Given** menu_permissions 미정의 메뉴 + 인증된 USER
- **When** USER가 트리 조회
- **Then** 해당 메뉴 포함 + accessible:true.

### AC-PERM-4 권한 미정의 메뉴 익명 차단 (002-D-3)
- **Given** menu_permissions 미정의, metadata.public != true
- **When** 익명 사용자 트리 조회
- **Then** 응답에 미포함.

---

## D. 템플릿 (REQ-CONTENT-004-D)

### AC-TPL-1 등록 + 슬롯 검증 (004-D-1)
- **Given** html_template에 `{{CONTENT}}` 슬롯 누락
- **When** 등록 호출
- **Then** 400 + `{"code":"TEMPLATE_CONTENT_SLOT_MISSING"}`.

### AC-TPL-2 외부 자산 차단 (004-D-2)
- **Given** css_assets에 `https://evil.example.com/x.css`
- **When** 등록
- **Then** 400 + `{"code":"TEMPLATE_ASSET_NOT_WHITELISTED"}`.

### AC-TPL-3 사용 중 비활성화 거부 (004-D-3)
- **Given** template_id=1을 page 1건이 사용 중
- **When** `PATCH /content/templates/1/status status=INACTIVE`
- **Then** 409 + `{"code":"TEMPLATE_IN_USE"}`.

---

## E. 페이지 CRUD/발행/이력 (REQ-CONTENT-005-D)

### AC-PAGE-1 페이지 생성 (005-D-1)
- **Given** CONTENT_ADMIN, slug='about', site_id=1
- **When** `POST /content/pages`
- **Then** 201 + page.status='DRAFT', current_version=1.

### AC-PAGE-2 슬러그 패턴 거부 (005-D-1)
- **Given** slug='About!Now'
- **When** 생성 요청
- **Then** 400 + `{"code":"SLUG_INVALID_PATTERN"}`.

### AC-PAGE-3 슬러그 중복 거부 (005-D-1)
- **Given** site_id=1, slug='about' 페이지 존재
- **When** 동일 (1,'about')로 생성
- **Then** 409 + `{"code":"SLUG_DUPLICATE"}`.

### AC-PAGE-4 수정 시 이력 누적 (005-D-2)
- **Given** page current_version=1
- **When** `PUT /content/pages/{id}` (제목 변경)
- **Then** page_history에 version=1 스냅샷 1행, page.current_version=2.

### AC-PAGE-5 즉시 발행 (005-D-3)
- **Given** page.status='DRAFT'
- **When** `POST .../publish`
- **Then** page.status='PUBLISHED', published_at != null, scheduled_at=null + page:slug:{slug} 캐시 무효화 확인.

### AC-PAGE-6 예약 발행 미래 검증 (005-D-4)
- **Given** scheduled_at = now - 1m
- **When** `POST .../schedule`
- **Then** 400 + `{"code":"SCHEDULED_AT_NOT_FUTURE"}`.

### AC-PAGE-7 예약 → 자동 발행 (005-D-4)
- **Given** scheduled_at = now + 60s, status='SCHEDULED'
- **When** 배치 잡(매분) 실행 후 60s+ 경과
- **Then** status='PUBLISHED', published_at = scheduled_at 이상.

### AC-PAGE-8 철회 후 시민 404 (005-D-5)
- **Given** status='PUBLISHED' → retract 호출
- **When** 익명 `GET /content/pages/by-slug/{slug}`
- **Then** 404.

### AC-PAGE-9 이력 비교 (005-D-6)
- **Given** version 1, 3 page_history 존재
- **When** `GET .../history?compare=1,3`
- **Then** 응답에 added/removed/changed 필드 단위 diff.

### AC-PAGE-10 롤백 (005-D-7)
- **Given** page version 5(현재), version 2 스냅샷 존재
- **When** `POST .../rollback/2`
- **Then** page 본문이 v2로 복원, page_history에 version=6 'ROLLBACK_FROM_v2' 기록, status='DRAFT' 강제.

### AC-PAGE-11 슬러그 변경 시 리다이렉트 자동 추가 (005-D-8)
- **Given** page slug='/old-name'
- **When** PUT 으로 slug='/new-name' 변경
- **Then** seo_redirect에 (from='/old-name', to='/new-name', http_status=301) INSERT.

### AC-PAGE-12 DRAFT 시민 차단 (005-D-9)
- **Given** status='DRAFT'
- **When** 익명 by-slug 조회
- **Then** 404. CONTENT_ADMIN의 `?preview=true&token=...` 만 200.

### AC-PAGE-13 RETRACTED 시민 차단 (005-D-9)
- **Given** status='RETRACTED'
- **When** 익명 by-slug 조회
- **Then** 404.

### AC-PAGE-14 SCHEDULED 시민 차단 (005-D-9)
- **Given** status='SCHEDULED', scheduled_at 미래
- **When** 익명 by-slug 조회
- **Then** 404.

---

## F. 콘텐츠 블록 (REQ-CONTENT-006-D)

### AC-BLK-1 RICH_TEXT sanitize (006-D-1)
- **Given** payload.html='<script>alert(1)</script><p>ok</p>'
- **When** 블록 저장
- **Then** 저장된 payload.html='<p>ok</p>' (script 제거).

### AC-BLK-2 HTML 블록 SYSADMIN 한정 (006-D-1)
- **Given** CONTENT_ADMIN(비 SYSADMIN)
- **When** block_type='HTML' 저장
- **Then** 403 + `{"code":"BLOCK_HTML_REQUIRES_SYSADMIN"}`.

### AC-BLK-3 블록 정렬 트랜잭션 (006-D-2)
- **Given** 블록 [A:10, B:20]
- **When** PATCH order [{A:30},{B:5}]
- **Then** 응답 후 조회 시 [B:5, A:30] 정확히 반영, 실패 시 둘 다 롤백.

### AC-BLK-4 IMAGE alt NOT NULL (006-D-3)
- **Given** payload={url:'/img.jpg', alt:''}
- **When** 저장
- **Then** 400 + `{"code":"IMAGE_ALT_REQUIRED"}`.

### AC-BLK-5 EMBED provider 화이트리스트 (006-D-4)
- **Given** payload={provider:'tiktok', id:'x'}
- **When** 저장
- **Then** 400 + `{"code":"EMBED_PROVIDER_NOT_ALLOWED"}`.

---

## G. SEO 메타 + sitemap.xml (REQ-CONTENT-007-D)

### AC-SEO-1 메타 길이 권고 경고 (007-D-1)
- **Given** seo_title 70자
- **When** 저장
- **Then** 200 + 응답 헤더 `X-Warning: SEO_TITLE_TOO_LONG`, 데이터는 저장.

### AC-SEO-2 sitemap.xml 발행 페이지만 (007-D-2)
- **Given** PUBLISHED 3건, DRAFT 2건, RETRACTED 1건, deleted 1건
- **When** `GET /sitemap.xml`
- **Then** `<url>` 요소 3개만 (PUBLISHED && deleted_at IS NULL).

### AC-SEO-3 sitemap.xml 캐시 (007-D-3)
- **Given** 첫 호출 후 TTL 1시간 윈도우 내
- **When** 동일 호출
- **Then** DB SELECT 미발생(캐시 hit), 동일 응답.

### AC-SEO-4 발행 시 sitemap 캐시 무효화 (007-D-3)
- **Given** sitemap 캐시 적재 상태
- **When** 신규 페이지 publish
- **Then** sitemap.xml 캐시 키 무효화 + 다음 호출 시 재생성.

### AC-SEO-5 robots.txt 정적 (007-D-4)
- **When** `GET /robots.txt`
- **Then** 200 + 본문에 `Sitemap: https://www.example.go.kr/sitemap.xml`.

### AC-SEO-6 canonical 자동 출력 (007-D-5)
- **Given** page.canonical_url 미설정
- **When** SSR 또는 SPA가 페이지 메타 응답
- **Then** `<link rel="canonical" href="현재 요청 URL">` 자동 포함.

---

## H. 팝업 (REQ-CONTENT-008-D)

### AC-POP-1 등록 시 기간 검증 (008-D-1)
- **Given** show_from=2026-05-01, show_until=2026-04-30 (역전)
- **When** 등록
- **Then** 400 + `{"code":"POPUP_PERIOD_INVALID"}`.

### AC-POP-2 활성 팝업 조회 (008-D-2)
- **Given** show_from=어제, show_until=내일, status=ACTIVE
- **When** `GET /content/popups/active`
- **Then** 응답에 포함.

### AC-POP-3 비활성 팝업 제외 (008-D-2)
- **Given** show_until=어제 (만료)
- **When** active 조회
- **Then** 응답에 미포함.

### AC-POP-4 ROLE 타겟 필터 (008-D-3)
- **Given** target_type='ROLE', target_role_codes=['SYSADMIN']
- **When** USER 역할 사용자가 active 조회
- **Then** 응답에 미포함. SYSADMIN 사용자는 포함.

### AC-POP-5 MEMBER 타겟 익명 차단 (008-D-3)
- **Given** target_type='MEMBER'
- **When** 익명 active 조회
- **Then** 응답에 미포함.

### AC-POP-6 오늘 그만 보기 쿠키 메타 (008-D-4)
- **Given** popup_id=7, show_today_close=true
- **When** active 응답
- **Then** 응답 본문에 cookie_key='popup_close_7'.

### AC-POP-7 한도 5개 헤더 (008-D-5)
- **Given** 활성 팝업 7개
- **When** active 조회
- **Then** 응답 본문 5개 + 응답 헤더 `X-Popup-Limit: 5`.

---

## I. 배너 (REQ-CONTENT-009-D)

### AC-BAN-1 alt_text 강제 (009-D-1)
- **Given** alt_text=''
- **When** 등록
- **Then** 400 + `{"code":"BANNER_ALT_REQUIRED"}`.

### AC-BAN-2 그룹별 활성 배너 정렬 (009-D-2)
- **Given** group=HOME_HERO, sort_order=[20,10,30] 활성 3건
- **When** `GET /content/banners?group=HOME_HERO`
- **Then** 응답 [10,20,30] 순서.

### AC-BAN-3 시간 윈도우 외 배너 제외 (009-D-2)
- **Given** display_until=어제
- **When** 그룹 조회
- **Then** 미포함.

### AC-BAN-4 클릭 카운트 원자적 증가 (009-D-3)
- **Given** click_count=100, 동시 호출 10건
- **When** `POST /content/banners/{id}/click` 10회 병렬
- **Then** 최종 click_count=110, 손실 없음(SQL `UPDATE ... SET click_count=click_count+1`).

### AC-BAN-5 클릭 audit_log 기록 (009-D-3)
- **When** 클릭 호출
- **Then** audit_log에 행 1건 (banner_id, ip, user_agent 포함).

---

## J. 다국어 (REQ-CONTENT-010-D)

### AC-I18N-1 다국어 저장 키 무결성 (010-D-1)
- **Given** namespace=page, resource_id=5, language=ko, field_name=title 행 존재
- **When** 동일 키 INSERT
- **Then** 409 + 또는 upsert 시 UPDATE.

### AC-I18N-2 폴백 체인 (010-D-2)
- **Given** page id=5, en에 title 없음, ko에 title='소개', site.default_language='ko'
- **When** Accept-Language: en 으로 페이지 조회
- **Then** 응답 title='소개', Content-Language: ko (폴백).

### AC-I18N-3 폴백 우선순위 (010-D-2)
- **Given** en/ko 모두 description 없음, default_language='en'
- **When** Accept-Language: ja 로 조회
- **Then** description='' (빈 문자열), audit 리포트에 누락 기록.

### AC-I18N-4 lang 속성 출력 (010-D-4)
- **Given** 응답 언어=en
- **When** SSR/메타 응답
- **Then** `<html lang="en">` 출력.

### AC-I18N-5 OG locale (010-D-5)
- **Given** 응답 언어=en
- **When** SEO 메타 응답
- **Then** `<meta property="og:locale" content="en_US">`.

### AC-I18N-6 언어 prefix 라우팅 (010-D-3, optional)
- **Given** supported_languages=["ko","en"], page slug='/about'
- **When** `GET /ko/about` 또는 `GET /en/about`
- **Then** 동일 페이지 + 응답 언어가 prefix 일치, Content-Language 헤더 일치.

---

## K. SEO 리다이렉트 (REQ-CONTENT-005-D-8 보강)

### AC-RDR-1 슬러그 변경 자동 등록 (005-D-8)
- **Given** page slug='/news/2026' 발행 상태
- **When** PUT 으로 slug='/notices/2026' 변경
- **Then** seo_redirect에 (from='/news/2026', to='/notices/2026', http_status=301, is_active=true) 1행 INSERT.

### AC-RDR-2 리다이렉트 동작 (005-D-8)
- **Given** seo_redirect 활성 행 존재
- **When** `GET /news/2026` (구 경로)
- **Then** HTTP 301 + Location: /notices/2026.

---

## L. 캐시 정책 (§10)

### AC-CACHE-1 메뉴 트리 캐시 hit
- **Given** 첫 트리 조회 후 5분 이내
- **When** 동일 조회
- **Then** DB SELECT 미발생.

### AC-CACHE-2 페이지 발행 시 다중 키 무효화
- **Given** menu:tree:1, page:slug:about, sitemap.xml 적재
- **When** 페이지 publish
- **Then** 3개 키 모두 무효화.

---

## M. 권한 매트릭스 검증 (§8)

### AC-MX-1 USER의 PAGE:WRITE 차단
- **Given** USER 역할
- **When** `POST /content/pages`
- **Then** 403.

### AC-MX-2 익명의 PAGE:READ 시 PUBLISHED만
- **Given** 익명
- **When** by-slug 조회 — DRAFT 페이지
- **Then** 404. PUBLISHED 페이지는 200.

### AC-MX-3 CONTENT_ADMIN의 SEO:REDIRECT:WRITE 차단
- **Given** CONTENT_ADMIN
- **When** `POST /content/seo/redirects`
- **Then** 403 (SYSADMIN만 가능).

### AC-MX-4 BLOCK HTML 신뢰 블록 SYSADMIN 한정
- **Given** CONTENT_ADMIN
- **When** block_type=HTML 작성
- **Then** 403.

---

## 통합 시나리오

### AC-INT-1 사이트 첫 방문 흐름
- **Given** 익명 시민
- **When** GET /
- **Then** (1) menu/tree 응답 < 50ms (캐시 hit) (2) banner HOME_HERO 정렬 응답 (3) popup active 응답 + X-Popup-Limit 헤더 (4) 페이지 본문 PUBLISHED만 노출 (5) `<html lang="ko">`.

### AC-INT-2 발행 → 검색엔진 노출
- **Given** 신규 페이지 publish
- **When** /sitemap.xml 호출
- **Then** 새 페이지 URL 포함, lastmod=published_at, 캐시 갱신.

### AC-INT-3 슬러그 변경 → SEO 보존
- **Given** PUBLISHED 페이지
- **When** slug 변경
- **Then** 구 경로 GET → 301 리다이렉트, 신 경로 GET → 200, sitemap 갱신.

---

## Quality Gates

### QG-C-1 보안
- 페이지 콘텐츠 RICH_TEXT/MARKDOWN 본문에 대해 OWASP XSS 페이로드 50종 자동 테스트 0건 통과
- 권한 매트릭스 §8 모든 행에 대해 negative 테스트(미권한 사용자 → 403) 자동화
- HTML 블록은 SYSADMIN 한정 강제(integration test)
- DOMPurify 동등 sanitize 라이브러리 서버측 적용 검증
- CSP 헤더(Content-Security-Policy: default-src 'self' ...) 응답 헤더에 출력

### QG-C-2 성능
- p95 페이지 응답 < 200ms (캐시 hit) — k6 부하 테스트 200 RPS 5분
- p95 메뉴 트리 조회 < 50ms — 동일 조건
- p95 sitemap.xml < 1s (10k 페이지 시드) — 단발 회귀 테스트
- 활성 팝업/배너 조회 < 100ms

### QG-C-3 접근성 (KWCAG 2.2 AA)
- axe-core 자동 검사 critical/serious 0건 (메뉴/페이지/팝업/배너 SPA 화면 전부)
- 메뉴 키보드 네비게이션 ↑↓→← Tab 100% 커버 (Playwright)
- 페이지 시맨틱 HTML(header/main/footer/article/section, h1 정확 1회) 자동 검증
- 모든 IMAGE 블록·배너의 alt_text NOT NULL DB 제약
- 응답 `<html lang="...">` 정확

### QG-C-4 SEO
- robots.txt 응답 200, Sitemap 라인 포함
- sitemap.xml `<urlset>` 스키마 W3C validator 통과
- 모든 PUBLISHED 페이지의 canonical URL 응답에 포함
- seo_title 60자 / seo_description 160자 권고치 검증 + 경고 헤더

### QG-C-5 데이터
- page_history 누적 무손실 (수정 100회 시 100 스냅샷 보존, version 단조 증가)
- i18n_resource UNIQUE(namespace, resource_id, language, field_name) DB 제약 검증
- 다국어 폴백 정확성 — 영어/한국어/누락 3 케이스 매트릭스 100%
- 슬러그 변경 시 seo_redirect 자동 INSERT 100% 트랜잭션 보장
- 멀티사이트 비활성 시 site_id 누락 쿼리 0건(Repository 베이스 클래스 강제)

---

## N. 알림 템플릿 (REQ-CONTENT-011-D, v0.2 추가)

### AC-NOTIF-1 템플릿 등록 + 채널 검증 (011-D-1)
- **Given** CONTENT_ADMIN 인증
- **When** `POST /api/v1/content/notification-templates` body=`{code:'WELCOME', channel:'KAKAO', category:'AUTH', title:'환영', body_template:'{{user.name}}님 환영합니다', variables:[{name:'user.name',type:'string',required:true}], locale:'ko'}`
- **Then** 201 + status='DRAFT', version=1, (code, channel, locale, version) 유일성 보장.

### AC-NOTIF-2 미선언 변수 거부 (011-D-2)
- **Given** body_template='{{user.name}}님 {{policy.title}} 신청', variables=[{name:'user.name',...}] (policy.title 미선언)
- **When** 등록 호출
- **Then** 400 + `{"code":"TEMPLATE_VARIABLE_UNDECLARED", "missing":["policy.title"]}`.

### AC-NOTIF-3 미사용 변수 경고 (011-D-2)
- **Given** body_template='{{user.name}} 환영', variables=[{name:'user.name'},{name:'policy.title'}]
- **When** 등록
- **Then** 201 + 응답 헤더 `X-Warning: TEMPLATE_VARIABLE_UNUSED` (policy.title 미사용 알림), 데이터는 저장.

### AC-NOTIF-4 미리보기 렌더링 (011-D-3)
- **Given** template body='{{user.name}}님 {{policy.title}} 안내'
- **When** `POST .../{id}/preview` body=`{user:{name:'홍길동'}, policy:{title:'공공정책'}}`
- **Then** 200 + 본문 `{"rendered":"홍길동님 공공정책 안내"}`.

### AC-NOTIF-5 미리보기 변수 누락 보고 (011-D-3)
- **Given** template body='{{user.name}} {{policy.title}}'
- **When** preview body=`{user:{name:'홍길동'}}` (policy 누락)
- **Then** 200 + rendered='홍길동 {{policy.title}}' + 응답 헤더 `X-Preview-Missing: policy.title`.

### AC-NOTIF-6 카카오 검수 잠금 (011-D-4)
- **Given** channel='KAKAO', status='PENDING_REVIEW'
- **When** `PUT .../{id}` body 변경
- **Then** 409 + `{"code":"TEMPLATE_REVIEW_LOCKED"}`.

### AC-NOTIF-7 검수 결과 등록 (011-D-4)
- **Given** status='PENDING_REVIEW'
- **When** `POST .../{id}/review-result` body=`{result:'APPROVED', reviewed_at:'2026-05-01T00:00:00Z', reason:'OK'}`
- **Then** 200 + status='APPROVED', notification_template_history에 새 version 기록.

### AC-NOTIF-8 APPROVED 본문 변경 시 새 version 분기 (011-D-5)
- **Given** version=1, status='APPROVED'
- **When** body_template 변경 PUT
- **Then** 새 row INSERT (code 동일, version=2, status='DRAFT'), 기존 version=1 행은 그대로 유지.

### AC-NOTIF-9 카카오 발급 운영 매뉴얼 참조 안내 (011-D-4, v0.2.1 사용자 결정 2026-04-29 Q-6 적용)
- **Given** channel='KAKAO' template_id=42, status='DRAFT' 상태에서 admin 인증
- **When** admin이 `POST /api/v1/content/notification-templates/42/submit-for-review`를 호출
- **Then** 200 + status='PENDING_REVIEW' 전환되고 응답 body에 `{"operationsManual":"docs/operations/kakao-template.md","message":"카카오 비즈센터 검수 신청은 운영 매뉴얼을 따라 진행하세요"}` 안내가 포함된다.
- **And** SPEC 본문은 시스템 인터페이스만 정의하며, 사람 검수 단계의 시스템 자동화는 본 SPEC v0.2.1 범위 외임이 매뉴얼 본문에 명시되어 있다.

### AC-NOTIF-10 notification_send.integration_log_id FK 적재 (v0.2.1 사용자 결정 2026-04-29 Q-7 적용)
- **Given** channel='KAKAO' template 발송 요청이 큐에 enqueue되고 외부 카카오 비즈메시지 API 호출이 성공한 상태에서
- **When** `IntegrationLogInterceptor`가 `integration_log` row(integration_type='KAKAO_NOTI', status='SUCCESS')를 적재하고 동일 트랜잭션(또는 후속 콜백)에서 `notification_send` row가 INSERT된 직후
- **Then** `notification_send.integration_log_id = integration_log.id` 정합성이 만족되고
- **And** SPEC-CMS-005 §14.2 `v_notification_history` 뷰에 해당 row가 1건 노출된다(INNER JOIN 매칭).

### AC-NOTIF-11 INAPP 채널 integration_log_id NULL 정상 (v0.2.1 사용자 결정 2026-04-29 Q-7 적용)
- **Given** channel='INAPP' 알림 발송(외부 호출 없음)
- **When** notification_send INSERT
- **Then** `integration_log_id IS NULL`이 정상 상태이며
- **And** SPEC-CMS-005 §14.2 `v_notification_history` 뷰는 INTEGRATION_TYPE 필터(KAKAO_NOTI/MAIL_SEND)와 INNER JOIN 조건으로 본 row를 제외한다(INAPP은 view 대상 아님).

---

## O. 메타데이터 표준 (REQ-CONTENT-012-D, v0.2 추가)

### AC-META-1 page 메타 컬럼 NOT NULL (012-D-1)
- **Given** page 신규 INSERT (classification_code 미지정)
- **When** 직접 SQL INSERT
- **Then** DB 제약(NOT NULL)에 의해 거부, default 'GENERAL' 적용 시 통과.

### AC-META-2 사전 미등록 코드 거부 (012-D-2)
- **Given** metadata_dictionary에 entry_type='CLASSIFICATION', code='POLICY' 등록 / 'UNKNOWN' 미등록
- **When** page 등록 시 classification_code='UNKNOWN'
- **Then** 400 + `{"code":"META_CLASSIFICATION_UNKNOWN"}`.

### AC-META-3 메타데이터 변경 이력 (012-D-3)
- **Given** page id=10, classification_code='GENERAL'
- **When** PUT 으로 classification_code='POLICY'로 변경
- **Then** metadata_history에 (target_namespace='page', target_id=10, before.classification_code='GENERAL', after.classification_code='POLICY') 1행 INSERT.

### AC-META-4 외부 메타데이터 export JSON (012-D-4)
- **Given** since='2026-04-01T00:00:00Z'
- **When** `GET /api/v1/content/metadata/export?since=2026-04-01T00:00:00Z&format=json`
- **Then** 200 + `application/json`, 페이지당 1000건 페이지네이션, 각 항목에 (namespace, id, s_meta_id, da_sharp_id, classification_code, retention_period, metadata_extra) 포함.

### AC-META-5 외부 메타데이터 export XML (012-D-4, optional)
- **Given** since 동일
- **When** `?format=xml`
- **Then** 200 + `application/xml`, 동일 데이터셋의 XML 직렬화.

---

## P. 다국어 강화 (REQ-CONTENT-013-D, v0.2 추가)

### AC-I18N-PLUS-1 양쪽 누락 검증 (013-D-1)
- **Given** site.supported_languages=['ko','en'], page id=5에 ko title 존재, en title 누락
- **When** `POST .../pages/5/publish`
- **Then** 200 + status='PUBLISHED' (차단 없음) + missing_translation 1행(en, page, 5, title) INSERT + 응답 헤더 `X-Missing-Translation-Count: 1`.

### AC-I18N-PLUS-2 일괄 export XLIFF (013-D-2)
- **Given** namespace='page', language='en' 리소스 5건
- **When** `GET /api/v1/content/i18n/export?namespace=page&language=en&format=xliff`
- **Then** 200 + `application/x-xliff+xml`, 본문이 XLIFF 1.2 스키마 통과.

### AC-I18N-PLUS-3 일괄 import 트랜잭션 (013-D-2)
- **Given** XLIFF 파일 100행 (95건 정상, 5건 namespace 잘못)
- **When** `POST /api/v1/content/i18n/import` (multipart)
- **Then** 200 + 리포트 `{success:95, failed:5, skipped:0}`, 정상 95건은 i18n_resource UPSERT, 실패 5건은 응답 본문에 사유 명시.

### AC-I18N-PLUS-4 다국어 검색 인덱스 분리 (013-D-3)
- **Given** page 본문 ko='공공정책 안내', en='Public Policy Guide'
- **When** PUT 후 `SELECT tsv_ko, tsv_en FROM page WHERE id=...`
- **Then** tsv_ko는 to_tsvector('simple', '공공정책 안내') 결과, tsv_en은 to_tsvector('english', 'Public Policy Guide') 결과 (서로 다른 lexeme 집합).

---

### QG-C-6 RFP 비기능 (v0.2 추가)
- 알림 템플릿 미리보기 p95 < 300ms (1KB 본문, 변수 10개) — k6 측정
- 메타데이터 export 변경 1만 건 p95 < 5s (페이지네이션 1000건 단위)
- 다국어 import XLIFF 5천 행 p95 < 30s (청크 500건)
- 모든 콘텐츠 등록 시 classification_code NOT NULL DB 제약 검증
- 메타데이터 변경 시 metadata_history 100% 기록 (트랜잭션 검증)
- 카카오 APPROVED 템플릿 본문 변경 시 항상 새 version 분기, 기존 row 보존(integration test)
- ko/en 별도 tsvector 인덱스 EXPLAIN으로 사용 확인 (검색 쿼리 시 GIN 인덱스 hit)

---

_총 인수기준: 75건 (A:4, B:9, C:4, D:3, E:14, F:5, G:6, H:7, I:5, J:6, K:2, L:2, M:4, INT:3) + Quality Gate 5건_
_v0.2 추가: 17건 (N:8, O:5, P:4) + Quality Gate 1건(QG-C-6)_
_v0.2 누계: 92건 + Quality Gate 6건_
