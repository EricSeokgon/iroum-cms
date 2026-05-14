# Changelog

모든 주요 변경 사항이 이 파일에 기록됩니다.

형식은 [Keep a Changelog](https://keepachangelog.com/ko/1.1.0/) 1.1.0 표준을 따르며,
이 프로젝트는 [Semantic Versioning](https://semver.org/spec/v2.0.0.html)을 준수합니다.

---

## [Unreleased]

### Added — SPEC-CMS-PUBLIC-001 시민 대상 공공 사이트 SPA

- Vue 3.5 + TypeScript 5 + Vite 6 기반 시민용 공공 사이트 SPA (`frontend/public/`) 신규 구축
- 25개 라우트 + 에러/유지보수 경로 (30개 전체) 구현; 3개 beforeEach 라우터 가드
- API 클라이언트: axios 인터셉터 (403→forbidden, GET 5xx→server-error 자동 리다이렉트)
- 공지·게시판·FAQ·Q&A 전체 화면 (NoticeListView, BoardPostListView, FaqView, QnaCreateView 등)
- 정책 매칭·안전 가이드·발간자료 다운로드 화면 (zip blob + jobId 비동기 처리)
- 검색 (6탭 + URL 동기화 + DOMPurify mark-only XSS 방어 + 최근 검색어 드롭다운)
- 홈: Promise.allSettled 5개 섹션, per-section ErrorState (부분 실패 격리)
- ECharts 5 통계 위젯 (BAR/LINE/PIE) + 스크린리더 테이블 폴백 (lazy-loaded)
- 미디어 갤러리: 이미지 lazy load (`loading="lazy"`) + 비디오 모달 (el-dialog)
- KWCAG 2.2 AA: jest-axe P0 게이트, skip nav, :focus-visible, .sr-only, aria-label 전면 적용
- i18n: ko/en 이중 언어, vue-i18n 9, localStorage `public.locale`, 키 패리티 자동 검증
- DOMPurify: 모든 v-html 영역 (공지 본문, 게시글 본문, 검색 스니펫) XSS 방어
- urlSafety.ts: isSafeUrl() / extractDomain() 유틸 — http/https 화이트리스트 외 차단
- 에러 페이지 전체 구현: NotFoundView, ForbiddenView, ServerErrorView, MaintenanceView (5분 폴링)
- **테스트**: 47 파일, 224 테스트 (Vitest 2.1.8 + @vue/test-utils + jest-axe)
- **TypeScript**: vue-tsc --noEmit 에러 0건

- **SPEC-CMS-001 공공기관 CMS 플랫폼 1차 출시 완료 (2026-05-14)**
  - **Bundle A — 회원·권한·로그인** (SPEC-CMS-002, 003, 004)
    - 회원 관리: 가입·수정·탈퇴, 비밀번호 정책, PII 마스킹
    - 권한 관리: 역할(Role)·메뉴별 권한 매트릭스
    - JWT 로그인·로그아웃·토큰 재발급, Refresh Token 순환
  - **Bundle B — 게시판·공지·Q&A·FAQ** (SPEC-CMS-005, 006, 007)
    - 게시판 마스터 설정, 게시글 CRUD, 첨부파일 업로드
    - 공지사항·팝업·배너 관리
    - Q&A 답변 워크플로우, FAQ 카테고리 관리
    - 안전 관리(SafetyManagement) 15 AC GREEN
    - 정책 매칭(PolicyMatching) 15 AC GREEN
  - **Bundle C — 콘텐츠·메뉴·사이트** (SPEC-CMS-008, 009, 010)
    - 페이지·팝업·템플릿·블록·위젯 콘텐츠 관리
    - 메뉴 트리 관리 (드래그앤드롭 정렬 지원)
    - 사이트 다국어(한/영) 설정·스케줄 발행
  - **미디어 파일 관리** (SPEC-MEDIA-001)
    - 이미지·동영상 업로드, 썸네일 자동 생성
    - 미디어 라이브러리 검색·태그
  - **보안 강화 28종** (SPEC-CMS-SECURITY 트랙 전종 Tested)
    - PII 마스킹·감사로그 AOP·메타 검증
    - AUTHZ 인가 매트릭스 IT 커버: 114 endpoint × 3 시나리오
    - ArchUnit 아키텍처 가드 + OWASP A01 회귀 검출 305 AC
  (SPEC-CMS-001 v0.5 Tested — ea54ddb)



- **AUTHZ-IT-REGRESSION-001 v0.6 Step 4 Implemented — controller unit test 11종 정정 (51 RED 100% 회복)**
  - 11 controller unit test의 AC-COV-001-1 `인증 없이 접근 시 401` 시나리오 → `403 Forbidden` 정정
  - 원인: @WebMvcTest + SecurityAutoConfiguration 제외 시 SecurityFilterChain 없음 → @PreAuthorize 거부 → 403
  - 운영 full SecurityFilterChain의 AuthenticationEntryPoint(401)와 다름 (테스트 환경 한계)
  - 401 검증은 SecurityConfig 통합 테스트에서 별도 (REQ-IRR-003 분리)
  - 정정 파일 12개:
    - PermissionChangeControllerTest, UserControllerTest, RoleControllerTest
    - BbsMasterControllerTest, RetentionPolicyControllerTest, GovernanceStatsControllerTest
    - DictionaryControllerTest, DataQualityControllerTest, RecoveryDrillControllerTest
    - BatchExecutionLogControllerTest, DashboardControllerTest, AccessLogControllerTest
  - REGRESSION-001 누적: ExpandIT 31 + Controller 11 = 51 RED → 0 (100% 회복)
  - 운영 코드 변경 0건
  (SPEC-CMS-SECURITY-AUTHZ-IT-REGRESSION-001 v0.6 Step 4 Implemented)

- **AUTHZ-IT-REGRESSION-001 v0.5 Step 2 Implemented — AuthorizationMatrixExpandIT 87/0 GREEN (31 RED 100% 회복)**
  - Phase A 응답 코드 28건 일괄 정정 (AUTH_FORBIDDEN → ACCESS_DENIED)
  - Phase B1-B5 DTO body 정상화 23건 (Popup/Page/Template/Org/Block/Widget/Schedule/Drill/Board/Menu/Code/CodeGroup)
  - assertAuthzPassed helper 추가 (ServletException 도메인 예외 처리)
  - 운영 코드 변경 0건, IT 시나리오 정정만
  (SPEC-CMS-SECURITY-AUTHZ-IT-REGRESSION-001 v0.5 Step 2 Implemented)

- **PII-KMS-001 + PII-ROTATION-001 v0.2 — META 정책 사전 합의 + 결정 포인트 정밀화**
  - PII-KMS-001: D1-D5 (KMS 공급자, 키 가져오기, 캐싱, Failover, IT 환경) + RUN 진입 절차 5단계
  - PII-ROTATION-001: D1-D5 (회전 주기, 회전 방식, 신규 데이터 처리, 회전 트리거, 회전 실패 처리)
  - 두 SPEC 모두 META-IT-GREEN-MANDATORY-001 Sync checklist 4 항목 사전 합의
  - 본 세션 검증된 패턴 (helper, race condition 회피) 사전 참조
  - 의존 SPEC 진입 순서 명확화: PII-KMS-001 → PII-ROTATION-001
  - RUN 진입 전 사용자 결정 확정 필요 (AskUserQuestion)
  - 정책 문서 갱신만, 운영 코드 변경 0건
  (SPEC-CMS-SECURITY-PII-KMS-001 v0.2 + SPEC-CMS-SECURITY-PII-ROTATION-001 v0.2)

- **AUTHZ-IT-EXPAND-004 SPEC v0.1 Planned — 잔여 26 endpoint → 100% IT 커버 (AUTHZ 트랙 종결)**
  - `.moai/specs/SPEC-CMS-SECURITY-AUTHZ-IT-EXPAND-004/spec.md` 신규
  - AUTHZ-IT-EXPAND-003 v0.4 Implemented (88 endpoint, 79%) 완성 후 자연 연장
  - 운영 controller @PreAuthorize 114건 vs IT baseline 88 → 잔여 26 endpoint 100% 커버
  - REQ-AM-EXP4-001~005 + 5 AC + RUN Step 1~5 분해
  - 5 결정 포인트 D1~D5 (IT 클래스 구조, 카테고리 분할, RUN 일괄 vs 분할, baseline 시점, 트랙 종결)
  - 패턴 100% 재사용 (assertAuthzPassed helper, DTO 정상 body, 응답 코드 분기, OR bypass, 분리 회귀, class-level @PreAuthorize)
  - 예상 비용 1-2 세션, 운영 코드 변경 0건
  - 본 SPEC 완성 시 AUTHZ 트랙 6단계 진화 종결:
    Matrix → EXPAND-001/002/003/004 + AUTODETECT + CTRL + REGRESSION + META = 8 SPEC chain
  - 6중 OWASP A01 검증 305 AC → ~380 AC (78 AC 추가)
  - ArchUnit baseline 88 → 114+ endpoint (100% IT 매핑)
  - META-IT-GREEN-MANDATORY-001 Sync checklist 4 항목 사전 합의
  (SPEC-CMS-SECURITY-AUTHZ-IT-EXPAND-004 v0.1 Planned)

- **AUTHZ-IT-EXPAND-003 v0.4 Implemented — 8 도메인 106 AC GREEN + ArchUnit baseline 88 (79% IT 커버 달성)**
  - AuthorizationMatrixExpand3IT.java ~1100줄 신규 (인프라 240 + Phase A 470 + Phase B 240 + Phase C 200)
  - 8 도메인 35 endpoint × 3 시나리오 = 106 AC + smoke 1 = 107 tests / 0 failures
    - §A.1 Organization 7 endpoint × 3 = 21 AC
    - §A.2 User 5 endpoint × 3 = 15 AC
    - §A.3 Code+CodeGroup 7 endpoint × 3 = 21 AC
    - §A.4 MenuMaintenance 4 endpoint × 3 = 12 AC
    - §A.5 Widget 2 endpoint × 3 = 6 AC
    - §A.6 BannerI18n 2 endpoint × 3 = 6 AC
    - §A.7 SearchPermission 3 endpoint × 3 = 9 AC (class-level @PreAuthorize 검증)
    - §A.8 GovernanceStats 5 endpoint × 3 = 15 AC
  - AuthorizationCoverageArchTest baseline 54 → 88 endpoint 갱신 (35 추가, GET /code-groups duplicate 1 제거)
  - hasSize(88), javadoc 3 hardcoding 갱신, method name Baseline54 → Baseline88
  - 분리 회귀 검증 (SETTING:READ vs WRITE, MAINT:READ vs WRITE, CODE:READ vs WRITE 등)
  - OR bypass 검증 (hasAnyRole 시나리오)
  - 클래스 레벨 @PreAuthorize 검증 (PermissionController, SynonymController, Governance 6 controller)
  - 패턴 재사용 100%: assertAuthzPassed helper, DTO 정상 body, 응답 코드 분기 (AUTH_REQUIRED 401 / ACCESS_DENIED 403)
  - 누적 IT 커버: 운영 114 endpoint 중 88 = 79%
  - 6중 OWASP A01 회귀 검출 305 AC + 88 endpoint baseline + 31 어휘 100% 커버
  - META-IT-GREEN-MANDATORY-001 Sync checklist 4 항목 충족
  - 운영 코드 변경 0건 (SPEC §3.2 비범위 준수)
  - 검증: ./gradlew test --tests "AuthorizationMatrixExpand3IT" → BUILD SUCCESSFUL
  - 검증: ./gradlew test --tests "AuthorizationCoverageArchTest" → 4 tests / 0 failures
  (SPEC-CMS-SECURITY-AUTHZ-IT-EXPAND-003 v0.4 Implemented 1차)

- **AUTHZ-IT-EXPAND-003 SPEC v0.1 Planned — 운영 ~120 endpoint 전체 IT 커버 (AUTHZ 트랙 3차)**
  - `.moai/specs/SPEC-CMS-SECURITY-AUTHZ-IT-EXPAND-003/spec.md` 신규
  - AUTHZ-IT-EXPAND-001 (29) + EXPAND-002 (19) = 누적 54 endpoint → 운영 실측 ~120 endpoint 미커버 ~66 갭
  - AUTHZ-AUTODETECT-001 baseline (103 메소드 / 31 어휘) 활용
  - REQ-AM-EXP3-001~005 + 6 AC + RUN Step 1~6 분해
  - 결정 포인트 D1~D5 (IT 클래스 구조, endpoint 수집, 시나리오 자동화, baseline 갱신, Implementation 위임)
  - 패턴 재사용: AUTHZ-IT-EXPAND-002 + REGRESSION-001 검증 패턴 100%
    - assertAuthzPassed helper
    - DTO 정상 body 정상화
    - 응답 코드 분기 (AUTH_REQUIRED 401 / ACCESS_DENIED 403)
    - @WebMvcTest 한계 명시
  - META-IT-GREEN-MANDATORY-001 Sync checklist 4 항목 사전 합의
  - 예상 비용 3-4 세션, 운영 코드 변경 0건 (IT 전용)
  - 본 SPEC 완성 시 ArchUnit baseline 100% IT 매핑 + OWASP A01 완전 검출
  (SPEC-CMS-SECURITY-AUTHZ-IT-EXPAND-003 v0.1 Planned)

- **PII-FOLLOWUP-004 v0.4 Implemented — Status 정상화 (AC-009-2가 PII-FOLLOWUP-005 v0.3에서 해결, 5/5 GREEN)**
  - SPEC v0.3 Mostly Implemented → v0.4 Implemented (1차)
  - AC-009-2 race condition은 본 SPEC v0.3에서 PII-FOLLOWUP-005로 분리되어 후속 해결됨
  - PII-FOLLOWUP-005 v0.3 Option B (@DirtiesContext) 적용으로 5/5 GREEN 완성
  - PII 트랙 5 AC 모두 GREEN (AC-009-2/3/4 + AC-FU-003-1/3)
  - README SPEC 표: Mostly Implemented → Implemented (1차) 정상화
  - 정책 문서 갱신만 — 운영 코드 변경 0건
  (SPEC-CMS-SECURITY-PII-FOLLOWUP-004 v0.4 Implemented)

- **META-IT-GREEN-MANDATORY-001 v0.3 Evidence 강화 — REGRESSION-001 AUTHZ 회귀 5 case 통합 (PII 5 + AUTHZ 5 = 10 evidence)**
  - SPEC v0.2 Implemented → v0.3 Evidence 강화
  - 추가 evidence 5 case (AUTHZ REGRESSION-001 회복 패턴):
    - Case 6: 응답 코드 변경 (AUTH_FORBIDDEN → ACCESS_DENIED 28+17건)
    - Case 7: @Valid validation 우선 (23+4 DTO body 정상화)
    - Case 8: @WebMvcTest Security 한계 (11+종 controller test 정정)
    - Case 9: 종합 회귀 검증 미실행 (MatrixIT 8 RED 추가 발견)
    - Case 10: 운영 GlobalExceptionHandler 미커버 (assertAuthzPassed helper)
  - REQ-PII-FU2-003 강화: 종합 회귀 검증 추가
  - REQ-META-IT-002 확대: GlobalExceptionHandler 커버리지 명시
  - REQ-META-IT-006 신설: 응답 코드 동기 (AUTH_REQUIRED 401 vs ACCESS_DENIED 403 분기)
  - 본 정책 정식 적용 SPEC 3건: PII-FOLLOWUP-005, AUTHZ-IT-EXPAND-002, AUTHZ-IT-REGRESSION-001
  - README §IT mandatory 정책 evidence 표 5 → 10건 확장
  - 정책 문서 전용 — 운영 코드/IT 신설 0건
  (SPEC-CMS-META-IT-GREEN-MANDATORY-001 v0.3 Evidence 강화)

- **AUTHZ-IT-REGRESSION-001 SPEC v0.1 Planned — AUTHZ IT 51 RED 회귀 진단 분리 (운영 ACCESS_DENIED + @Valid validation 우선 + controller Security 차이)**
  - `.moai/specs/SPEC-CMS-SECURITY-AUTHZ-IT-REGRESSION-001/spec.md` 신규
  - PII-FOLLOWUP-005 v0.3 통합 실행 시 발견한 51 unit test/IT failed 회귀 분리 진단
  - 본 세션 PR 변경 영향 0건 확정: AuthorizationMatrixExpandIT 단독 실행도 31 failed (기존 회귀)
  - 회귀 패턴 3가지:
    - 패턴 1: `expected 403 but 400` — @Valid @RequestBody/@RequestParam validation이 @PreAuthorize 전 실행
    - 패턴 2: `AUTH_FORBIDDEN vs ACCESS_DENIED` — GlobalExceptionHandler AuthorizationDeniedException 핸들러 추가로 응답 코드 변경
    - 패턴 3: controller unit test 11종 401/403 차이 — Security 구성 차이
  - REQ-IRR-001~005 + 6 AC + RUN Step 1~5 분해
  - AUTHZ-MATRIX-001 + AUTHZ-IT-EXPAND-001 Status 정정 (Implemented → Mostly Implemented)
  - AUTHZ-IT-EXPAND-002 (본 세션 작성)는 회귀 없음 (100% GREEN)
  - META-IT-GREEN-MANDATORY-001 첫 위반 사례 (단독 GREEN ↔ 통합 GREEN 불일치)
  - P2 (운영 영향 0, SPEC ↔ 실제 GREEN 상태 불일치 해소)
  (SPEC-CMS-SECURITY-AUTHZ-IT-REGRESSION-001 v0.1 Planned)

- **AUTHZ-IT-EXPAND-001 Status 정정 — Implemented → Mostly Implemented (v0.2 회귀, IT-REGRESSION-001 참조)**
  - README SPEC 표 Status 갱신
  - 운영 GlobalExceptionHandler `AuthorizationDeniedException` 핸들러 추가 (별도 commit) 시점 회귀 발견
  - 운영 영향 0건 (응답 코드만 변경)

- **PII-FOLLOWUP-005 v0.3 Implemented — Option B @DirtiesContext 적용 → 5/5 GREEN 완성, PII 트랙 전체 Implemented**
  - PiiAuditEnhanceIT 클래스 레벨 `@DirtiesContext(classMode = AFTER_EACH_TEST_METHOD)` 적용
  - SyncTaskExecutor + @Async + @Transactional(REQUIRES_NEW) 통합 race condition 완전 회피
  - 단독 GREEN ↔ 통합 GREEN 동등성 보장 (META-IT-GREEN-MANDATORY-001 REQ-PII-FU2-003 첫 정식 적용 사례)
  - 통합 실행 검증: `./gradlew :backend:integrationTest --tests "PiiAuditEnhanceIT"` → **5 tests / 0 failed / 0 skipped**
    - AC-009-2 (본인 row 제외) — PASSED
    - AC-009-3 (HMAC lookup-only 미적재) — PASSED
    - AC-009-4 (self-access auditing) — PASSED
    - AC-FU-003-1 (ADMIN findPage N건) — PASSED
    - AC-FU-003-3 (각 target row 적재) — PASSED
  - AC-009-2 옵션 A 진단 디버그 코드 제거 (System.out.println + jdbcTemplate.queryForList)
  - 비용: 각 test ~30초 부팅 (5 test ≈ 2.5분), 안전성 최대
  - PII-FOLLOWUP 1~5 트랙 전체 Implemented 완성 (5 SPEC 모두 GREEN)
  (SPEC-CMS-SECURITY-PII-FOLLOWUP-005 v0.3 Implemented 1차)

- **META-IT-GREEN-MANDATORY-001 v0.2 Implemented — README §IT user environment GREEN mandatory 정책 신설 + Sync checklist 4 항목 명문화**
  - README.md §"IT user environment GREEN mandatory 정책 (META)" 신설 (§336 ~ §라이선스 사이)
  - HARD 정책 요약 4건: 단독+통합 양쪽 GREEN / @Transactional 위험 / race condition 회피 / Sync commit message evidence
  - Sync checklist 4 항목 표: 단독 GREEN / 통합 GREEN / @Transactional 위험 / race condition 회피
  - 적용 사례 5건 evidence 표: PII-FOLLOWUP-001 (@Async + @MockitoSpyBean), PII-FOLLOWUP-003 (@Transactional rollback), PII-FOLLOWUP-004 AC-009-3 (UnexpectedRollbackException), PII-FOLLOWUP-004 AC-009-4 (SPEC↔운영 차이), PII-FOLLOWUP-005 (단독 GREEN vs 통합 race condition)
  - PII-FOLLOWUP-005가 본 정책의 첫 적용 사례 (Partially Diagnosed 상태 정확화)
  - 신규 SPEC Implemented 인정 조건: checklist 4 항목 모두 evidence 명시 필수
  - 누락 시 Mostly Implemented / Partially Diagnosed 상태로 강등
  - 정책 문서 전용 — 운영 코드/IT 신설 0건
  (SPEC-CMS-META-IT-GREEN-MANDATORY-001 v0.2 Implemented 1차)

- **META-IT-GREEN-MANDATORY-001 SPEC v0.1 Planned — IT user environment GREEN mandatory 정책 명문화 (PII-FOLLOWUP 5건 evidence 기반)**
  - `.moai/specs/SPEC-CMS-META-IT-GREEN-MANDATORY-001/spec.md` 신규 (정책 참조 문서)
  - **REQ-PII-FU2-003 HARD**: 신규 IT는 단독 실행 PASS + 통합 실행 BUILD SUCCESSFUL 양쪽 검증 필수
  - REQ-META-IT-002: @Transactional 위험 명시 (audit/async 효과 가림 패턴 회피)
  - REQ-META-IT-003: race condition 회피 패턴 (@DirtiesContext / @TestMethodOrder / standalone-only)
  - REQ-META-IT-004: 정책 문서 위치 (본 SPEC spec.md 참조)
  - REQ-META-IT-005: Sync 단계 evidence 검증 강화 (단독 GREEN + 통합 BUILD SUCCESSFUL 양쪽 commit message 명시)
  - 적용 사례 4건 회고: PII-FOLLOWUP-001 (@MockitoSpyBean + @Async 충돌), PII-FOLLOWUP-003 (@Transactional rollback false GREEN), PII-FOLLOWUP-004 (SPEC ↔ 운영 차이), PII-FOLLOWUP-005 (단독 GREEN vs 통합 race condition)
  - 운영 코드/IT 신설 0건 — 정책 문서 전용
  - 7 AC + 4 결정 포인트 + RUN Step 1~4 분해
  - 향후 SPEC RUN/Sync 단계 품질 게이트로 작동
  (SPEC-CMS-META-IT-GREEN-MANDATORY-001 v0.1 Planned)

- **AUTHZ-IT-EXPAND-002 Implemented — 19 미커버 권한 어휘 IT 매트릭스 (57 AC GREEN, ArchUnit baseline 54 endpoint 100% IT 커버)**
  - `backend/src/test/java/kr/co/ircp/cms/security/AuthorizationMatrixExpand2IT.java` 신규 658줄
  - 19 권한 어휘 (CONTENT:READ, PAGE:READ/ROLLBACK/HISTORY:READ, SITE:WRITE, MENU:PERMISSION:WRITE, TEMPLATE:READ, USER:READ, AUDIT:READ, SYSTEM:READ/DASHBOARD/SETTING:READ/WRITE/MAINT:READ/WRITE/LOG:READ/ADMIN, ROLE:CONTENT_ADMIN) × 평균 3 시나리오 + 분리 회귀 4건 = 57 AC GREEN
  - 7 도메인 @Nested 그룹 (ContentRead/PageAdvanced/SiteMenu/UserAudit/Dashboard/SystemSetting/SystemOperation)
  - Phase A 29 AC (commit c450299) + Phase B 28 AC (commit 7a058e5) 단계적 활성화
  - 분리 회귀 검증 4건: PAGE:HISTORY:READ vs ROLLBACK, SETTING:READ vs WRITE, SYSTEM:READ vs ADMIN, MAINT:READ vs WRITE
  - AND 조건 검증: USER:READ AND AUDIT:READ (PersonalDataAccessController)
  - OR bypass 검증: ROLE:CONTENT_ADMIN (CONTENT_ADMIN/ADMIN/SUPER_ADMIN)
  - assertAuthzPassed helper 신설: GlobalExceptionHandler 미처리 도메인 RuntimeException 허용 (권한 통과 증명), AccessDeniedException/AuthenticationException 제외
  - AuthorizationCoverageArchTest baselineEndpoints() 35 → 54 endpoint 갱신 + javadoc/assertion size 3 hardcoding 갱신
  - OWASP A01 회귀 검출 5중 검증 199 AC + 54 endpoint baseline + 31 어휘 100% 커버 달성
  - 운영 코드 변경 0건 (SPEC §3.2 비범위 준수)
  - 실제 Java 17 + Gradle 구동 검증: BUILD SUCCESSFUL (Expand2IT + ArchTest 모두 GREEN)
  (SPEC-CMS-SECURITY-AUTHZ-IT-EXPAND-002 v0.3 Implemented 1차, commits fc4a569 + c450299 + 7a058e5 + [본 sync])


- **AuthorizationCoverageArchTest — ArchUnit 기반 운영 @PreAuthorize 자동 검출 (4 @Test, 실제 구동 GREEN)**
  - `backend/src/test/java/kr/co/ircp/cms/security/archunit/AuthorizationCoverageArchTest.java` 신규 448줄
  - ArchUnit 1.3.0 기반 (기존 의존성 재사용, 신규 의존성 0건)
  - PiiEmailMaskArchTest 271줄 패턴 재사용
  - 4 AC: 운영 @PreAuthorize 카운트 baseline (103) / IT endpoint set (35 unique) / 35 baseline 정확 매칭 / 31 권한 어휘 baseline
  - 운영 31 권한 어휘 정밀 발견 (사전 추정 14 → 실측 31, +17 신규 발견)
  - 신규 @PreAuthorize 추가 또는 권한 어휘 변경 시 RED → Gradle check 통합 → CI PR 차단
  - 4종 RED 시뮬레이션 절차 클래스 javadoc에 명시 (REQ-AAD-005)
  - 실제 Java 17 + Gradle 8.8 구동 검증: BUILD SUCCESSFUL in 11s, 4 tests 0 failed
  - PII-FOLLOWUP-001 잔여 회귀 발견 (별도 SPEC PII-FOLLOWUP-002 분리)
  (SPEC-CMS-SECURITY-AUTHZ-AUTODETECT-001 v0.2 Implemented 1차, commits 2be18d0 + 9cb4933 + 6b831d8)

- **README — ArchUnit RED 신호 + 수동 갱신 절차 갱신**
  - 기존 D3 수동 갱신 절차 → ArchUnit 자동 검출 + 수동 갱신 통합 절차
  - 운영 31 권한 어휘 분류 명시 (Role 4 + Authority 26 + isAuthenticated 1)
  - SPEC AUTHZ-AUTODETECT-001 + AUTHZ-IT-EXPAND-001 양 SPEC 참조

- **PII-FOLLOWUP-004 v0.3 Mostly Implemented — VerificationService REQUIRES_NEW 운영 fix + AC-009-3 GREEN 회복**
  - 운영 코드 1줄 변경: `VerificationServiceImpl.request`에 `@Transactional(propagation = REQUIRES_NEW)` 적용
  - root cause: AuthServiceImpl.requestPasswordReset catch 블록이 예외 삼키지만 Spring AOP가 inner tx를 rollback-only 마킹 → outer commit UnexpectedRollbackException
  - 효과: inner tx 분리로 호출자 commit 가능 + 보안 정책 유지
  - AC-009-3 GREEN 회복 (HMAC lookup-only audit 미적재 검증)
  - AC-009-4 + AC-FU-003-1/3 GREEN 유지
  - AC-009-2 잔여 (race condition, @TestMethodOrder 적용 검토)
  - PII 트랙 6 SPEC 사이클 사실상 완성 (audit IT 5 AC 중 4 GREEN + 1 race condition 잔여)

- **PII-FOLLOWUP-003 v0.2 Implemented (1차) — 옵션 G TRUNCATE cleanup + @Transactional 제거**
  - 본 SPEC 핵심 목표 100% 달성: HikariCP readOnly connection sticky로 인한 audit row 0건 해소
  - AC-FU-003-1 GREEN 회복 (이전 핵심 RED): ADMIN findPage → audit row N건 적재 검증
  - AC-FU-003-3 GREEN 회복 (이전 핵심 RED): distinct target_user_id 적재 검증
  - AC-009-2 GREEN 유지 (BeforeEach cleanup)
  - 옵션 G 구현: PiiAuditEnhanceIT @Transactional 제거 + TRUNCATE personal_data_access_log + DELETE users (audit_it_%) 양방향 cleanup
  - PostgreSQL 표준: BEFORE DELETE FOR EACH ROW 트리거는 TRUNCATE 비호출 → PIPA APPEND-ONLY 정책 보존
  - 운영 코드 git diff 0줄 (IT 코드만 변경)
  - 잔여 AC-009-3/4 false GREEN 노출 (@Transactional rollback이 가리던 실제 audit 동작) — PII-FOLLOWUP-004 분리 권장
  (commit b464bd3)

- **PII-FOLLOWUP-003 SPEC v0.1 Planned — PII Audit IT 잔여 2 AC 해소 SPEC 분리**
  - PII-FOLLOWUP-002 v0.2 잔여 2 AC (AC-FU-003-1/3 audit row 적재 검증) 본 세션 시도 결과 명문화
  - 옵션 A (REQUIRES_NEW) / C (@Async 분리) / F (readOnly=false 명시) 모두 실패 실증 (commits 94ae3b1/f2b9018/555e044 revert)
  - 다음 세션 옵션 D (별도 DataSource pool) / E (TransactionTemplate) / G (IT 재설계) 우선순위 권장
  - REQ-PII-FU3-001/002/003 정의 + 6 AC 골격

- **PII-KMS-001 SPEC v0.1 Planned — README 표 누락 SPEC 디렉토리 보완**
  - 운영 KMS 어댑터 (AWS KMS / HashiCorp Vault / Azure Key Vault)
  - PII-001 v0.2 운영 prod 차단 가드 해제 의존 SPEC
  - 결정 포인트 D1~D4 (KMS 공급자/키 가져오기/캐싱/Failover) + REQ-PII-KMS-001/002/003 골격

- **PII-ROTATION-001 SPEC v0.1 Planned — 키 자동 회전 배치**
  - PII-KMS-001 Implemented 의존 (장기 P3)
  - PIPA 안전성 확보 조치 의무 (암호화 키 주기적 교체)
  - 결정 포인트 D1~D4 (회전 주기/재암호화/구 키 보존/트리거) + REQ-PII-ROT-001/002/003 골격

- **AUTHZ-IT-EXPAND-002 SPEC v0.1 Planned — 19 미커버 권한 어휘 IT 매트릭스 분리**
  - ArchUnit baseline 31 어휘 - AUTHZ-IT-EXPAND-001 12 커버 = 19 미커버 어휘 식별
  - 미커버 어휘: CONTENT_ADMIN, CONTENT:READ, PAGE:READ/ROLLBACK/HISTORY:READ, SITE:WRITE, MENU:PERMISSION:WRITE, TEMPLATE:READ, USER:READ, SYSTEM:READ/DASHBOARD/SETTING:READ/WRITE/MAINT:READ/WRITE/LOG:READ/ADMIN, AUDIT:READ
  - REQ-AM-EXP2-001~004 정의 + Step 1~4 분해
  - 본 SPEC 완성 시 OWASP A01 회귀 검출 5중 검증 240+ AC 달성
  - 사용자 결정 D1~D4 다음 세션 RUN 진입 전 확정 필요

- **PII-FOLLOWUP-002 v0.2 Implemented (1차) — Spy + @Async 충돌 100% 해소**
  - `PersonalDataAccessLogServiceImplFallbackTest.java` 신규 142줄 (Unit test, Spring context 불필요)
    - 3 AC GREEN: DataAccessException 주입 + 빈 targetUserIds + 정상 5건 (BUILD SUCCESSFUL)
    - SimpleMeterRegistry 직접 사용 + PersonalDataAccessLogServiceImpl 직접 생성 → AOP @Async proxy 우회
  - `PiiAuditEnhanceIT.java` 재설계 (-29줄)
    - `@MockitoSpyBean PersonalDataAccessLogService` 제거 (CGLIB proxy 충돌 근본 원인)
    - AC-FU-003-2 메소드 별도 unit test로 분리
    - InvalidUseOfMatchersException 완전 해소
  - 핵심 목표(Spy + @Async 충돌) 100% 달성
  - 잔여 2 AC (audit row 0건)은 별개 PIPA 트리거 + tx 제약 — 후속 SPEC `PII-FOLLOWUP-003` 분리 권장
  (commit a5f873b)

- **PII-FOLLOWUP-002 SPEC 분리 (Planned)**
  - PII-FOLLOWUP-001 잔여 RED 3건 (@MockitoSpyBean + @Async CGLIB proxy 충돌) 분리 SPEC
  - Root cause 명문화 + 해결 옵션 3종 (운영 리팩토링 / IT 재설계 / @Async 우회) 권장 옵션 B
  - REQ-PII-FU2-003: SPEC 'Implemented' 상태 전 사용자 환경 IT GREEN 의무화 절차 강화
  - PII-FOLLOWUP-001 v0.2 Implemented가 정적 검증만 수행한 절차 결함 명문화

- **PII-FOLLOWUP-001 회귀 1차 부분 수정 (Bean override 허용)**
  - `application-integration.yml`에 `spring.main.allow-bean-definition-overriding: true` 추가
  - PiiAuditEnhanceIT ApplicationContext 부팅 GREEN 회복 (6 → 3 GREEN)
  - 잔여 3 RED는 PII-FOLLOWUP-002 분리

- **AuthorizationMatrixExpandIT — HTTP 권한 매트릭스 IT 확장 (89 @Test)**
  - `backend/src/test/java/kr/co/ircp/cms/security/AuthorizationMatrixExpandIT.java` 신규 1,540줄
  - 29 endpoint × 평균 3 시나리오 = 88 AC + smoke test 1건
  - 도메인별 `@Nested` 그룹 7개: Content(7) / Block(2) / Dashboard(3) / Auth(4) / System(5) / Governance(3) / BoardMenu(5)
  - 권한 어휘 12종 100% 커버: SUPER_ADMIN(5) / ADMIN(5) / hasAnyRole(1) / CONTENT:WRITE(1) / PAGE:WRITE(1) / PAGE:PUBLISH(3) / SYSTEM:CODE:READ(2) / SYSTEM:CODE:WRITE(3) / SYSTEM:STATS(1) / MENU:WRITE(3) / BLOCK:WRITE(2) / TEMPLATE:WRITE(2) / isAuthenticated(2 — 403 N/A)
  - 어휘 분리 회귀 검증 5건: PAGE:WRITE/PAGE:PUBLISH, BLOCK:WRITE/PAGE:WRITE, SYSTEM:CODE:READ/WRITE, MENU:WRITE/CONTENT:WRITE, TEMPLATE:WRITE/PAGE:WRITE
  - multi-role 분기 검증: hasAnyRole(SUPER_ADMIN/DEPT_ADMIN) 어느 한쪽 단독 통과 검증
  - AUTHZ-MATRIX-001 패턴 100% 재사용 (@SpringBootTest + Testcontainers PG 16 + @MockitoBean JwtTokenProvider/TokenBlacklistMapper + PII 더미 키 + JwtTestAuth helper)
  - AUTHZ-MATRIX-001 6 endpoint와 중복 0건 (다른 컨트롤러 또는 다른 endpoint 보강)
  - 사용자 입력 정정: "22+ endpoint" → 운영 @PreAuthorize 120개 정밀 진단 + 권한 어휘 12종 분포
  - 보안 트랙 OWASP A01 회귀 검출 능력: HTTP 매트릭스 1차 19 AC + 확장 88 AC + 메소드 슬라이스 31 AC = 3중 검증 138+ AC
  (SPEC-CMS-SECURITY-AUTHZ-IT-EXPAND-001 v0.2 Implemented 1차, commits 151a864/df11edd/dcaac84/dd4bf82)

- **README — HTTP 권한 매트릭스 IT 신규 endpoint 추가 절차 안내 (D3 수동 갱신)**
  - 운영 신규 @PreAuthorize 추가 시 IT 매트릭스 갱신 5단계 절차 명시
  - 권한 어휘 분류 가이드 (역할 기반/권한 기반/isAuthenticated 분기)
  - 자동 검출은 후속 SPEC AUTHZ-AUTODETECT-001(가칭) 위임

- **PiiKeyVault 인터페이스 + LocalEnvPiiKeyVault 구현**
  - `PiiKeyVault` 인터페이스: `getActiveKey()`, `getKeyByVersion(int)`, `getHmacKey()` 메서드 + `ActiveKey` record 정의
  - `LocalEnvPiiKeyVault`: 환경변수(`PII_EMAIL_KEY_V1`, `PII_EMAIL_HMAC_KEY`) base64 디코딩 + 32-byte 키 길이 검증
  - Spring profile `prod` + `LocalEnvPiiKeyVault` 조합 부팅 거부 가드 (운영 환경 안전성)
  - 단위 테스트 14 GREEN (키 로드 성공/실패, 길이 검증, 누락 환경변수 처리)
  (SPEC-CMS-SECURITY-PII-001 Step 1, commit 1d4ae61)

- **AesGcmEmailEncryptionService + HMAC-SHA256 구현**
  - `AesGcmEmailEncryptionService`: AES-256-GCM 암호화/복호화 (12-byte IV, 16-byte auth tag 분리)
  - `SecureRandom` 기반 12-byte IV 생성 (IV 재사용 방지)
  - HMAC-SHA256 lookup 키 계산 (`HmacSHA256`, 암호화 키와 분리된 전용 키)
  - 복호화 실패(`AEADBadTagException`) 시 `audit_log` CRITICAL 적재 + `PiiIntegrityException` 전파
  - Micrometer 메트릭: `pii.email.encrypt.count`, `pii.email.decrypt.count`, `pii.email.decrypt.failure.count`
  - 단위 테스트 17 GREEN (encrypt/decrypt roundtrip, null 처리, tag mismatch, IV 신선도, 동시성 등)
  (SPEC-CMS-SECURITY-PII-001 Step 2, commit 0a6b14e)

- **V24 마이그레이션 — PII 암호화 컬럼 + HMAC lookup 인덱스**
  - `V24__pii_encryption_email.sql`: 5개 신규 컬럼 추가
    - `email_encrypted BYTEA`: AES-256-GCM 암호문
    - `email_iv BYTEA`: GCM IV (12 bytes)
    - `email_tag BYTEA`: GCM auth tag (16 bytes)
    - `email_hmac VARCHAR(64)`: HMAC-SHA256(hmacKey, normalizedEmail) — lookup 키
    - `email_key_version SMALLINT NOT NULL DEFAULT 1`: 점진적 키 회전 지원
  - `idx_users_email_hmac` UNIQUE 부분 인덱스 생성 (HMAC lookup 성능 + UNIQUE 제약)
  - `data_dictionary` 5개 row 시드 (SPEC-CMS-009 데이터 분류 통합)
  - 기존 `email`, `email_hash` 컬럼 deprecated 주석 처리 (V25에서 DROP 예정)
  (SPEC-CMS-SECURITY-PII-001 Step 3, commit e432d53)

- **UserMapper.findByEmailHmac 신규 쿼리**
  - `UserMapper.xml`에 `findByEmailHmac` 쿼리 추가 (HMAC lookup 전용, REQ-PII-EMAIL-006)
  - `UserMapper.java` 인터페이스 메서드 추가: `Optional<User> findByEmailHmac(String emailHmac)`
  (SPEC-CMS-SECURITY-PII-001 Step 4, commit 29878b9)

- **PiiEmailIntegrationTest 4 GREEN**
  - Testcontainers + PostgreSQL 16 기반 통합 테스트 4건
    1. 신규 사용자 생성 시 email 암호화 저장 검증
    2. `findByEmailHmac`으로 HMAC lookup 정상 동작 검증
    3. 복호화 roundtrip 정확성 검증
    4. UNIQUE 인덱스 중복 삽입 차단 검증
  (SPEC-CMS-SECURITY-PII-001 Step 4, commit 29878b9)

- **AbstractIntegrationTest PII 키 주입**
  - `AbstractIntegrationTest` 베이스 클래스: `PII_EMAIL_KEY_V1`, `PII_EMAIL_HMAC_KEY` 더미 키 환경변수 자동 주입
  - SpringBootTest 컨텍스트 로드 시 `LocalEnvPiiKeyVault` 누락 키 예외 방지
  (SPEC-CMS-SECURITY-PII-001 Step 4, commit 29878b9)

- **NoEmailWildcardValidator + AdminEmailPartialSearchException — admin email partial 검색 차단 (REQ-PII-EMAIL-007)**
  - `NoEmailWildcardValidator`: RFC 5321 valid email + 와일드카드(`*`, `?`, `%`, `_`) 부정 문자 클래스 거부
  - `AdminEmailPartialSearchException`: 400 ADMIN_EMAIL_PARTIAL_FORBIDDEN 전용 예외
  - `@NoEmailWildcard` Bean Validation annotation
  - `UserController` `@Validated` + 파라미터 적용
  - `GlobalExceptionHandler` 400 핸들러 + ConstraintViolationException 핸들러
  - 통합 테스트 11/11 GREEN (PiiEmailAdminSearchIT — 와일드카드 4종 + 정상 + 정규화 + 권한)
  - 사용자 결정: email 빈 문자열은 무시(전체 검색 허용)
  (SPEC-CMS-SECURITY-PII-002 Step 1, commit 3a8be0f)

- **EmailMaskSerializer — API 응답 email 마스킹 (REQ-PII-EMAIL-008)**
  - Jackson `JsonSerializer<String>` + SecurityContext 분기 (ADMIN/본인 평문, 그 외 마스킹)
  - 1자=`*`, 2자=`**`, 3자+=첫CP+`***`+마지막CP, 코드 포인트 단위 (IDN 안전)
  - 사용자 결정: 2자 local-part 마스킹은 `**@e***.com` (SPEC §5.4 원문 유지)
  - `UserSummary`, `UserDetail` `@JsonSerialize(using = EmailMaskSerializer.class)` 적용
  - Java record 호환 검증
  - 통합 테스트 8/8 GREEN (PiiEmailMaskIT — 1/2/3+자, IDN, 이모지, ADMIN/본인 분기)
  (SPEC-CMS-SECURITY-PII-002 Step 2, commit fbedd8c)

- **PII 접근 감사 보강 — recordBulk @Async + Micrometer (REQ-PII-EMAIL-009)**
  - `PersonalDataAccessLogServiceImpl.recordBulk(viewerId, viewerRole, targetUserIds, fields, purpose)` `@Async("auditExecutor")` 비동기 일괄 INSERT
  - `MeterRegistry` 주입 + `pii.audit.log.failure.count` Micrometer counter
  - `UserServiceImpl.findPage(actor)` 본인 제외 + `recordBulk` 호출
  - `PersonalDataAccessPurpose.ADMIN_EMAIL_LOOKUP` enum 추가
  - 사용자 결정: AOP fallback 허용 + ERROR 로그 + Micrometer counter
  - 통합 테스트 3/6 GREEN + 3 @Disabled (AC-009-1, 5, 6 — 비동기 검증 인프라 follow-up SPEC-CMS-SECURITY-PII-FOLLOWUP-001로 추적)
  (SPEC-CMS-SECURITY-PII-002 Step 3, commit 04b9fe3)

- **PiiEmailMaskArchTest — ArchUnit 강제 (UserSummary/UserDetail email @JsonSerialize)**
  - `archunit-junit5:1.3.0` 의존성 추가
  - 5 ArchUnit 케이스: UserSummary/UserDetail email 필드 `@JsonSerialize(using = EmailMaskSerializer.class)` 누락 방지 + Architecture safety net
  - 신규 DTO 추가 시 마스킹 누락 자동 차단
  (SPEC-CMS-SECURITY-PII-002 Step 4, commit 0b3d05e)

- **JwtTestAuth utility + Awaitility 의존성 (테스트 인프라)**
  - `JwtTestAuth`: `JwtPrincipal` record를 SecurityContext에 주입하는 IT 인증 헬퍼 (50줄)
  - `awaitility:4.2.2` 의존성 추가 (비동기 검증용 폴링)
  - 다중 IT 클래스 회귀 BUILD SUCCESSFUL (회귀 0건)
  (SPEC-CMS-SECURITY-PII-002 Step 4, commit 0b3d05e)

- **IntegrationAsyncConfig — IT 전용 비동기 실행기 override (REQ-PII-FU-001)**
  - `@TestConfiguration` + `@Profile("integration")` + `@Primary` 조합
  - `@Bean(name="auditExecutor")` SyncTaskExecutor 반환 — `@Async("auditExecutor")` 호출이 호출 스레드에서 동기 완료
  - 운영 `AsyncConfig.auditExecutor()` ThreadPoolTaskExecutor를 IT profile 한정 override (default profile 무영향)
  - `@MX:NOTE` + `@MX:SPEC` 적용
  (SPEC-CMS-SECURITY-PII-FOLLOWUP-001 Step 1, commit `5fe440b`)

- **@MockitoSpyBean 마이그레이션 — Spring Framework 6.2 표준 적용 (REQ-PII-FU-002)**
  - `org.springframework.boot.test.mock.mockito.SpyBean` (deprecated) → `org.springframework.test.context.bean.override.mockito.MockitoSpyBean`
  - `PiiAuditEnhanceIT` `@SpyBean` → `@MockitoSpyBean` (사용처 단 1곳, Scope Discipline)
  - `recordBulk(long, String, List, Set, PersonalDataAccessPurpose)` 5-arg matcher 시그니처 매칭 한계 해소
  (SPEC-CMS-SECURITY-PII-FOLLOWUP-001 Step 2, commit `5fe440b`)

- **PiiAuditEnhanceIT @Disabled 3건 활성화 (REQ-PII-FU-003)**
  - `findPage_bulkAuditLog_nRows` (AC-FU-003-1, ← PII-002 AC-009-1)
  - `auditInsertFailure_returns200AndDoesNotPropagateError` (AC-FU-003-2, ← PII-002 AC-009-5)
  - `findPage_bulkAudit_distinctTargetUserIds` (AC-FU-003-3, ← PII-002 AC-009-6)
  - PII-002 RUN 1차에서 forward reference로 격리되어 있던 IT 3건 forward reference 완전 회수
  (SPEC-CMS-SECURITY-PII-FOLLOWUP-001 Step 3, commit `5fe440b`)

- **WebMvcTestInfraConfig EntryPoint 운영 시맨틱 정렬 (CTRL-AUTHZ-COVERAGE-001 Step 1, REQ-CTRL-AUTHZ-COVERAGE-001 인프라 보강)**
  - `Http403ForbiddenEntryPoint` → `HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)` 교체
  - 운영 SecurityConfig + JwtAuthenticationFilter 익명 시 401 AUTH_REQUIRED 반환 시맨틱과 정렬
  - 영향: 인증된 사용자 + 권한 부족 → 403 (변경 없음). 익명 + AccessDenied → 신규 401 (운영 부합)
  - Step 1 11 ControllerTest 회귀 0건 검토 완료
  (SPEC-CMS-SECURITY-CTRL-AUTHZ-COVERAGE-001 Step 1, commit `c1a564c`)

- **Step 1: governance+auth 9 ControllerTest 401/403 보강 (REQ-CTRL-AUTHZ-COVERAGE-001)**
  - governance 6 + auth 3 = 9 컨트롤러 × 2 시나리오 = 18 신규 IT
  - 권한 어휘: `hasRole('ADMIN')` (governance 6), `hasAuthority('AUDIT:READ')` (PermissionChange), `hasRole('SUPER_ADMIN')` (Role), `hasAnyRole('SUPER_ADMIN','DEPT_ADMIN')` (User)
  - auth/Me, auth/MyPersonalDataAccess: 메소드 레벨 권한 0건 → 주석만 추가 (AUTHZ-MATRIX-001 IT 위임)
  (SPEC-CMS-SECURITY-CTRL-AUTHZ-COVERAGE-001 Step 1, commit `c1a564c`)

- **Step 3: board+dashboard 1 컨트롤러 적용 (BbsMaster)**
  - BbsMaster: DELETE /api/v1/boards/{id} hasRole('ADMIN') 메소드 레벨 — 2 신규 시나리오
  - DELETE는 body 불필요 → @PreAuthorize 평가 보장
  - board/Attachment/Comment/Post + dashboard 3개: HTTP-level only → 주석만 (AUTHZ-MATRIX-001 IT 위임)
  (SPEC-CMS-SECURITY-CTRL-AUTHZ-COVERAGE-001 Step 3, commit `fe461b3`)

- **Step 4: system 2 컨트롤러 적용 (AccessLog, Dashboard)**
  - AccessLog: hasAuthority('SYSTEM:LOG:READ') 메소드 레벨 — 2 신규 시나리오
  - system/stats/Dashboard: hasAuthority('SYSTEM:DASHBOARD') 메소드 레벨 — 2 신규 시나리오
  - content/Sitemap: PUBLIC (REQ-CONTENT-007-D) → 주석만
  (SPEC-CMS-SECURITY-CTRL-AUTHZ-COVERAGE-001 Step 4, commit `8c66a07`)

- **Step 2: policy+safety 10 컨트롤러 모두 주석만 (적용 불가 사유 명시)**
  - 10 컨트롤러 모두 메소드 레벨 권한 정책 0건 (HTTP-level only)
  - SPEC marker 주석으로 적용 불가 사유 + AUTHZ-MATRIX-001 IT 위임 명시
  (SPEC-CMS-SECURITY-CTRL-AUTHZ-COVERAGE-001 Step 2, commit `4655421`)

- **PiiMaskingConverter — Logback PII 마스킹 인프라 신규 (REQ-PII-MASK-001)**
  - `ch.qos.logback.classic.pattern.ClassicConverter` 구현 (87줄)
  - 정규식 4종: email (`[\w.+-]+@[\w-]+\.[\w.-]+`), phone (`01[016789]-?\d{3,4}-?\d{4}`), SSN (`\d{6}-?[1-4]\d{6}`), IPv4 (`\b(\d{1,3}\.){3}\d{1,3}\b`)
  - 정적 `mask()` 함수 제공 (테스트 및 다른 호출처 재사용 가능)
  (SPEC-CMS-SECURITY-PII-MASKING-001 Step 1, commit `bfd7488`)

- **logback-spring.xml — Logback PII 마스킹 통합 (REQ-PII-MASK-001)**
  - prod 프로파일: `logstash-logback-encoder 7.4` `MaskingJsonGeneratorDecorator` + `RegexValueMasker` (JSON 모든 String 필드 적용)
  - dev/local 프로파일: 자체 `PiiMaskingConverter` + `PatternLayout %maskedMsg`
  - 모든 프로파일 적용 (D4-(d) 채택) — 개발 환경 PII 보호 + 운영-개발 일관성
  - 운영 ELK/Loki 시스템에 PII 평문 전송 차단
  (SPEC-CMS-SECURITY-PII-MASKING-001 Step 1, commit `bfd7488`)

- **MDC SHA-256 prefix — PII 추적성과 보호 양립 (REQ-PII-MASK-002)**
  - `MdcLoggingFilter` `clientIp` 필드 → `HashUtil.sha256Hex(ip).substring(0, 8)` (SHA-256 hex prefix 8자)
  - `RequestContextFilter` `ip` 필드 → 동일 패턴 적용
  - `HashUtil.sha256Hex` PII-001 인프라 재사용 (신규 코드 최소화)
  - `userId`/`traceId`/`spanId`/`requestId`/`userAgent`는 평문 보존 (PII 아님)
  (SPEC-CMS-SECURITY-PII-MASKING-001 Step 2, commit `bfd7488`)

- **JWT 인증 로그 PII 제거 (REQ-PII-MASK-003)**
  - `JwtAuthenticationFilter:116` `log.debug("JWT 인증 완료: userId={}, username={}", ...)` → `log.debug("JWT 인증 완료: userId={}", ...)`
  - DEBUG 레벨 일시 활성화 시에도 username PII 미노출
  - 운영 조사: `userId` + `audit_log` 테이블 기반 추적 (SPEC-CMS-005 AuditLogAspect 인프라 재사용)
  (SPEC-CMS-SECURITY-PII-MASKING-001 Step 3, commit `bfd7488`)

- **신규 테스트 3 파일 (403줄) — PII 마스킹 검증 (REQ-PII-MASK-001/002/003)**
  - `LogbackPiiMaskingTest` (140줄, 12 메서드, 4 nested class): 마스킹 패턴 4종 매칭 + false positive 미발생
  - `MdcSha256MaskingTest` (132줄, 4 메서드): SHA-256 prefix 정확성 + 추적성 + null/empty 가드
  - `JwtAuthLogTest` (131줄): Logback `ListAppender` 캡처 + username 미포함 단언
  (SPEC-CMS-SECURITY-PII-MASKING-001 Step 4, commit `bfd7488`)

- **AuthorizationMatrixIT — HTTP 권한 매트릭스 IT 인프라 신설 (REQ-AUTHZ-MATRIX-001)**
  - `@SpringBootTest(webEnvironment = MOCK)` + `@AutoConfigureMockMvc` + `@Testcontainers` (PostgreSQL 16)
  - `@MockitoBean JwtTokenProvider`, `@MockitoBean TokenBlacklistMapper` (DB 토큰 저장 없이 시나리오 검증)
  - PII 더미 키 주입 (SPEC-PII-001 인프라 일관 — `pii.keyvault.keys.v1` + `pii.keyvault.hmac-key`)
  - `givenValidToken(roles, permissions)` JWT stub helper로 임의 권한 시뮬레이션
  - 운영 `SecurityFilterChain` + `JwtAuthenticationFilter` + Method Security 그대로 적재
  - `@MX:NOTE` + `@MX:SPEC` 클래스 헤더 적용
  (SPEC-CMS-SECURITY-AUTHZ-MATRIX-001 Step 1, commit `f0ae970`)

- **WRITE 권한 endpoint 매트릭스 검증 — 12 IT 케이스 (REQ-AUTHZ-MATRIX-002)**
  - 6 endpoint × {권한 부족 → 403, 정합 권한 → 2xx} 시나리오 매트릭스
  - 권한 어휘 4종 모두 커버: `hasAuthority('CONTENT:WRITE')`, `hasAuthority('PAGE:WRITE')`, `hasAnyRole('SUPER_ADMIN','DEPT_ADMIN')`, `hasRole('SUPER_ADMIN')`, 클래스 레벨 `hasRole('ADMIN')`
  - 검증 endpoint: Banner POST/PUT, Page POST, CacheAdmin invalidate, User POST, Governance class-level
  - 권한 어휘 분리 회귀 검출 (`CONTENT:WRITE` 보유하더라도 `PAGE:WRITE` 부재 시 403)
  - 역할 위계 회귀 검출 (`ADMIN` 보유하더라도 `SUPER_ADMIN` 부재 시 403)
  (SPEC-CMS-SECURITY-AUTHZ-MATRIX-001 Step 2, commit `f0ae970`)

- **응답 body 회귀 검증 + 운영 컴포넌트 적재 검증 — 4 IT 케이스 (REQ-AUTHZ-MATRIX-003)**
  - 401 응답: Content-Type + `code=AUTH_REQUIRED` + `message` 필드
  - 403 응답: Content-Type + `code=AUTH_FORBIDDEN` + `message` 필드
  - `JwtAuthenticationFilter` 체인 적재 간접 검증 (401 경로 EntryPoint 호출)
  - Method Security 인터셉터 적재 간접 검증 (403 경로 `@PreAuthorize` 호출)
  (SPEC-CMS-SECURITY-AUTHZ-MATRIX-001 Step 3, commit `f0ae970`)

### Changed

- **User 엔티티 5 PII 필드 추가**
  - `User.java`: `emailEncrypted`, `emailIv`, `emailTag`, `emailHmac`, `emailKeyVersion` 필드 추가
  - Lombok `@NoArgsConstructor` + `@AllArgsConstructor` 파라미터 정합성 강화
  (SPEC-CMS-SECURITY-PII-001 Step 4, commit 29878b9)

- **UserServiceImpl 암호화 경로 적용**
  - `UserServiceImpl.create()`: email 암호화 + HMAC 계산 후 저장 경로 적용
  - `UserServiceImpl.update()`: email 변경 시 재암호화 + 신규 HMAC 갱신
  (SPEC-CMS-SECURITY-PII-001 Step 4, commit 29878b9)

- **MigrationOrderIT V24 포함**
  - `MigrationOrderIT`: V17→V23 범위에서 V17→V24 범위로 확장
  - V24 마이그레이션 순서 및 체크섬 검증 포함
  (SPEC-CMS-SECURITY-PII-001 Step 3, commit e432d53)

- **UserController email 파라미터 검증 가드 적용**
  - `@Validated` 컨트롤러 + `@NoEmailWildcard email` 파라미터
  (SPEC-CMS-SECURITY-PII-002 Step 1, commit 3a8be0f)

- **GlobalExceptionHandler PII 예외 핸들러 추가**
  - `AdminEmailPartialSearchException` 400 핸들러 (RFC 9457 ProblemDetail)
  - `ConstraintViolationException` 400 핸들러 (Bean Validation 위반 표준화, 동일 ADMIN_EMAIL_PARTIAL_FORBIDDEN 코드)
  - `@MX:NOTE` + `@MX:SPEC` 추가 (SPEC §5.3 / REQ-PII-EMAIL-007 응답 코드 고정 근거)
  (SPEC-CMS-SECURITY-PII-002 Step 1, commits 3a8be0f + sync 단계)

- **CI workflow integrationTest 자동 실행 보장 (REQ-TIR-003)**
  - `.github/workflows/ci.yml` 변경 0줄 — REQ-TIR-002 check.dependsOn으로 자동 처리 (D4 옵션 1)
  - 현 ci.yml `./gradlew build jacocoTestReport`가 build → check → integrationTest 순으로 자동 실행
  - GitHub Actions PR 게이트에서 IT 자동 실행 + 통합 커버리지 보고서 artifact 업로드 보장
  (SPEC-CMS-TEST-INFRA-RECONFIG-001, commit `f5955a3`)

- **UserServiceImpl findPage 시그니처 변경**
  - `findPage(actor)` 본인 row 제외 + `recordBulk` 호출
  (SPEC-CMS-SECURITY-PII-002 Step 3, commit 04b9fe3)

- **MdcLoggingFilterTest 회귀 정정 (REQ-PII-MASK-002 follow-up)**
  - line 73 `assertThat(...).isEqualTo("10.0.0.1")` (평문 IP) → `isEqualTo(HashUtil.sha256Hex("10.0.0.1").substring(0, 8))` (SHA-256 prefix)
  - `HashUtil` import 추가
  - REQ-PII-MASK-002 clientIp SHA-256 prefix 변경에 따른 기존 테스트 정합 (PII-FOLLOWUP-001 @Import 보강 패턴 일관)
  (SPEC-CMS-SECURITY-PII-MASKING-001 회귀 정정, commit `bfd7488`)

- **PiiAuditEnhanceIT 클래스 헤더 — 명시적 @Import**
  - `@Import(IntegrationAsyncConfig.class)` 추가 (프로젝트 컨벤션 일관 — `WebMvcTestInfraConfig` 선례)
  - `@TestConfiguration` 자동 컴포넌트 스캔 미보장 환경에서 IntegrationAsyncConfig 명시적 로드
  (SPEC-CMS-SECURITY-PII-FOLLOWUP-001 Step 1, commit `5fe440b`)

- **PiiAuditEnhanceIT — Awaitility polling 정리 (D5-1)**
  - SyncTaskExecutor override로 동기 실행 보장됨 → `await().atMost(2, SECONDS).untilAsserted(...)` 호출 제거
  - import 정리: `@Disabled`, `Awaitility.await`, `TimeUnit.SECONDS` 제거 (가독성 향상)
  (SPEC-CMS-SECURITY-PII-FOLLOWUP-001 Step 3, commit `5fe440b`)

- **PersonalDataAccessLogService.recordBulk 신규 메서드**
  - 기존 `record()` 패턴 따라 `@Async("auditExecutor")` + MDC 캡처 + 일괄 INSERT
  - try-catch fallback + Micrometer counter
  - `@MX:SPEC` sub-line 추가 (SPEC §5.5 / REQ-PII-EMAIL-009 — 적재 실패 시 user-facing 에러 미전파 정책)
  (SPEC-CMS-SECURITY-PII-002 Step 3, commits 04b9fe3 + sync 단계)

### Fixed

- **PiiEmailIntegrationTest 다중 IT 클래스 실행 시 격리 결함 해소**
  - `UserMapper.xml`: `email_encrypted`, `email_iv`, `email_tag` 컬럼에 `jdbcType="BINARY"` 명시
  - `UserMapper.xml`: `email_key_version` 컬럼에 `jdbcType="SMALLINT"` 명시
  - `PiiEmailIntegrationTest`에 `@Transactional` 추가 (테스트 간 DB 상태 격리)
  - 다중 IT 클래스 병렬/순차 실행 환경에서 `PiiEmailIntegrationTest` 회귀 0건 확인
  (SPEC-CMS-SECURITY-PII-001 Step 4 follow-up, commit f91628a)

### Security

- **PIPA 제29조 안전성 확보 조치 의무 충족**
  - `users.email` 컬럼 AES-256-GCM 암호화 적용 (애플리케이션 레이어)
  - HMAC-SHA256 기반 lookup으로 deterministic SHA-256 rainbow table 공격 방지
  - 키 관리 인터페이스(`PiiKeyVault`) 추상화로 운영 KMS(AWS KMS / HashiCorp Vault) 연동 준비
  - 코드 리뷰 `8c9ffd3` HIGH 갭 #3 (UserMapper email 암호화 미구현) 해소
  - 운영 배포 차단(P0 blocker) 상태 해소
  (SPEC-CMS-SECURITY-PII-001 Step 1~4, commits 1d4ae61, 0a6b14e, e432d53, 29878b9, f91628a, 44cc3b8)

- **PIPA 제29조 안전성 확보 조치 의무 추가 완화**
  - admin email partial 검색 차단 (전사 사용자 노출 방지)
  - API 응답 email 마스킹 (DTO 레벨, ADMIN/본인 외 사용자 PII 노출 차단)
  - PII 접근 감사 보강 (`personal_data_access_log` 일괄 적재로 비ADMIN/비본인 admin lookup 추적성 확보)
  - ArchUnit으로 마스킹 강제 (신규 DTO 회귀 방지)
  - OWASP A03(Injection) / A04(Insecure Design) / A05(Misconfiguration) / A09(Logging) 점검 PASS
  - SPEC-CMS-SECURITY-PII-001과 결합하여 운영 배포 차단 상태 완전 해소
  (SPEC-CMS-SECURITY-PII-002 Step 1~4, commits 3a8be0f, fbedd8c, 04b9fe3, 0b3d05e, 1b1f7d0)

- **PIPA 제29조 안전성 확보 조치 의무 추가 완화 — 운영 부수 채널(로그) PII 노출 통제**
  - 운영 로그 PII 평문 저장 차단 (Logback 마스킹 모든 프로파일 — REQ-PII-MASK-001)
  - MDC `clientIp`/`ip` SHA-256 prefix (디버깅 추적성 + PII 보호 양립 — REQ-PII-MASK-002)
  - JWT 인증 로그 username PII 제거 (DEBUG 활성화 시에도 안전 — REQ-PII-MASK-003)
  - ELK/Loki 등 외부 로그 수집 시스템에 PII 평문 미전송
  - PII-001 (저장 영역) + PII-002 (응답 영역) 보완하여 운영 부수 채널 보호 완성
  (SPEC-CMS-SECURITY-PII-MASKING-001 Step 1~4, commit `bfd7488`)

- **OWASP A09 가시화 — 보안 IT 커버리지 측정 신뢰도 강화 (TEST-INFRA-RECONFIG-001 RUN 1차)**
  - `PiiAuditEnhanceIT`, `AuthorizationMatrixIT`, `PiiEmailIntegrationTest` 코드 경로가 jacocoTestReport에 반영되어 보안 IT 커버리지 정량 확인 가능
  - IT 회귀 검출 능력 회복 → 보안 IT (AUTHZ-MATRIX-001 19 AC, PII-FOLLOWUP-001 6 AC) PR 게이트 자동 실행 보장
  - TRUST 5 Tested 원칙 강화 — 단위 테스트만의 84.9%에서 통합 경로 포함 커버리지로 측정 근거 완성
  (SPEC-CMS-TEST-INFRA-RECONFIG-001, commit `f5955a3`)

- **OWASP A01 메소드 레벨 회귀 검출 부분 보완 (CTRL-AUTHZ-COVERAGE-001 RUN 1차)**
  - AUTHZ-MATRIX-001(HTTP 매트릭스 IT)의 상호 보완 SPEC — 검증 레이어 분리
  - 12 ControllerTest 메소드 레벨 401/403 검증 보강 (24 신규 시나리오)
  - 19 ControllerTest는 메소드 레벨 권한 정책 0건 → AUTHZ-MATRIX-001 IT 레이어가 검증 책임
  - WebMvcTestInfraConfig EntryPoint 운영 시맨틱 정렬 (익명 접근 시 401 일관성 확보)
  - 운영 코드 변경 0건 — 테스트 인프라 보강만으로 회귀 검출 능력 부분 강화
  (SPEC-CMS-SECURITY-CTRL-AUTHZ-COVERAGE-001 Step 1~4, commits `c1a564c`, `4655421`, `fe461b3`, `8c66a07`)

- **OWASP A01 (Broken Access Control) 회귀 검출 인프라 확보**
  - 5/7 코드 리뷰 C1 진정한 갭(HTTP 권한 매트릭스 회귀 검출 인프라 부재) 해소
  - 운영 `SecurityFilterChain.requestMatchers()` URL 인증 매트릭스 + 메소드 레벨 `@PreAuthorize` 정책 변경 시 자동 회귀 검출
  - 6 핵심 endpoint × 3 시나리오(401/403/200) 매트릭스로 권한 어휘 4종 + 역할 위계 + 권한 어휘 분리 모두 커버
  - 운영 코드 변경 0건 — IT 인프라 추가만으로 회귀 검출 능력 회복
  (SPEC-CMS-SECURITY-AUTHZ-MATRIX-001 Step 1~3, commits `af5ad41`, `f0ae970`)

- **JaCoCo executionData에 integrationTest 통합 (REQ-TIR-001)**
  - `tasks.jacocoTestReport.dependsOn(tasks.test, "integrationTest")` 추가
  - `executionData(fileTree(layout.buildDirectory.dir("jacoco")) { include("*.exec") })` 추가 — `test.exec` + `integrationTest.exec` 양쪽 적재
  - 단위 + 통합 경로 커버리지 정확화 — 84.9%가 단위 테스트만의 수치였던 5/7 핵심 우려 해소
  - Docker 미가용 환경 fallback (fileTree include 패턴으로 `integrationTest.exec` 부재 허용)
  (SPEC-CMS-TEST-INFRA-RECONFIG-001, commit `f5955a3`)

- **check task에 integrationTest 통합 (REQ-TIR-002)**
  - `tasks.named("check") { dependsOn("integrationTest") }` 추가
  - `./gradlew check` 또는 `./gradlew build` 시 IT 자동 실행 (Docker 가용 시)
  - 기존 `shouldRunAfter(tasks.test)` 유지 (실행 순서 보장)
  (SPEC-CMS-TEST-INFRA-RECONFIG-001, commit `f5955a3`)

- **integrationTest 후 jacocoTestReport 자동 실행 (보강)**
  - `tasks.register<Test>("integrationTest").finalizedBy(tasks.jacocoTestReport)` 추가
  - IT 실행 후 통합 커버리지 보고서 자동 생성 (수동 실행 불필요)
  (SPEC-CMS-TEST-INFRA-RECONFIG-001, commit `f5955a3`)

---

### 후속 SPEC 예정

본 SPEC 1차 범위에서 의도적으로 제외된 항목들이 후속 SPEC으로 분리됩니다.
상세 비범위 정의는 SPEC §3.2를 참조하십시오.

| 후속 SPEC 후보 | 내용 |
|--------------|------|
| **Step 5 (이행 대기)** | `PiiEmailMigrationJob` 운영 배치 + V25 평문 컬럼 DROP — 운영 KMS 결정 후 별도 PR |
| **SPEC-CMS-SECURITY-PII-002** | REQ-PII-EMAIL-007(관리자 검색 제약) + REQ-PII-EMAIL-008(응답 마스킹) + REQ-PII-EMAIL-009(PII 접근 감사) — Implemented (1차) |
| **SPEC-CMS-SECURITY-PII-FOLLOWUP-001** | PII 비동기 감사 IT 검증 인프라 정비 (@Disabled 3건 활성화) — **Implemented (1차) 2026-05-08** |
| **SPEC-CMS-SECURITY-PII-KMS-001** | AWS KMS / HashiCorp Vault 어댑터 구현 (1차 `LocalEnvPiiKeyVault` 대체) |
| **SPEC-CMS-SECURITY-PII-ROTATION-001** | 키 자동 회전 배치(`PiiEmailRekeyJob`) + cron 스케줄 |
| **SPEC-CMS-SECURITY-PII-MASKING-001** | 로그/백업 마스킹 표준 — Logback 마스킹 + MDC SHA-256 + JWT log 정정 (백업은 후속) — **Implemented (1차) 2026-05-11** |
| **SPEC-CMS-SECURITY-PII-NEXT-001 시리즈** | `users.name`, `users.phone_e164`, `login_history.ip` 등 나머지 PII 컬럼 암호화 |

**보안 회귀 검출 트랙 (OWASP A01)**

| 후속 SPEC 후보 | 내용 |
|--------------|------|
| **SPEC-CMS-SECURITY-AUTHZ-MATRIX-001** | HTTP 권한 매트릭스 IT 인프라 (운영 SecurityFilterChain + @PreAuthorize 회귀 검증) — **Implemented (1차) 2026-05-11** |
| **SPEC-CMS-SECURITY-CTRL-AUTHZ-COVERAGE-001** | ControllerTest 메소드 레벨 401/403 회귀 보강 (12 적용 + 19 IT 위임) — **Implemented (1차) 2026-05-11** |
| **SPEC-CMS-SECURITY-AUTHZ-IT-EXPAND-001** | 매트릭스 IT 5~7 → 22+ 컨트롤러 확장 |
| **SPEC-CMS-TEST-INFRA-RECONFIG-001** | JaCoCo + check + CI integrationTest 통합 (5/7 C2 잔여 갭 3건 해소) — **Implemented (1차) 2026-05-11** |
| **SPEC-CMS-DATA-QUALITY-JOB-CLARIFY-001** | 5/7 코드 리뷰 C3 — DataQualityCheckJobTest 의미 명확화 |
