# Changelog

모든 주요 변경 사항이 이 파일에 기록됩니다.

형식은 [Keep a Changelog](https://keepachangelog.com/ko/1.1.0/) 1.1.0 표준을 따르며,
이 프로젝트는 [Semantic Versioning](https://semver.org/spec/v2.0.0.html)을 준수합니다.

---

## [Unreleased]

### Fixed

- **어드민 네비게이션 라우팅 버그 수정 및 SUPER_ADMIN 권한 동기화** (커밋 5777076)
  - `AdminLayout.vue`: `el-menu :router="true"` 제거 → `@select="handleMenuSelect"` 명시적 라우팅으로 변경 (Element Plus el-menu 내장 라우터 충돌 해결)
  - `router/index.ts`: 대시보드 기본 라우트 중복 경로 정리
  - `MenuController` / `MenuService` / `MenuServiceImpl`: `AdminMenu` 분리 API 구현 (`admin_menu` 테이블 독립화)
  - `MenuUpdateRequest` DTO 신규 추가
  - **V50 마이그레이션**: `admin_menu_permissions` 시드 데이터
  - **V51 마이그레이션**: `menu` 공공 테이블에서 `ADMIN_*` 항목 정리
  - **V52 마이그레이션**: `SUPER_ADMIN` 역할 누락 권한 동기화 (V13/V14에서 추가된 `SYSTEM:CODE:READ` 등 11개 권한 일괄 추가)

### Added

- **페이지 버전 이력 관리 고도화** (SPEC-CMS-PAGE-HISTORY-001)
  - **이력 보존 정책 배치** (`PageHistoryRetentionService`): 페이지당 최대 50건 유지, `currentVersion` 항목 보호. `PageHistoryMapper`에 `countByPageId` / `deleteOldestByPageId` 추가. `application.yml: cms.history.max-versions: 50`
  - **롤백 실제 복원** (`PageServiceImpl.rollbackPage()`): snapshot JSON 파싱하여 `title` / `slug` 실제 복원 (이전: version 번호만 업데이트)
  - **changeSummary 자동 생성** (`PageChangeSummaryGenerator`): title/slug 변경 감지 기반 diff 자동 요약, 사용자 입력 우선·'변경 없음' fallback 지원
  - **롤백 감사 로그** (`AuditLogService.record(action="UPDATE")`): 롤백 시 `afterValue`에 from/to version 포함하여 수동 호출 (`@AuditLog` aspect 미사용 — 실패 시 FAILURE 이중 기록 방지)
  - **PageListView 이력 진입점** (`PageListView.vue`): 이력 버튼 + `PageHistoryDialog` 연동 + 롤백 후 목록 새로고침. `ko.json: content.page.action.history` 메시지 추가
  - **테스트**: `PageChangeSummaryGeneratorTest` 5건, `PageServiceTest` 17건, `PageIT` (Retention 3 / RollbackRestore 2 / RollbackAudit 2) 7건, `PageListView.spec` 4건 — 전 19 AC GREEN

- **RBAC 관리자 권한 제어 시스템** (SPEC-CMS-RBAC-001)
  - ADMIN 역할 시드 및 5단 권한 계층 (`SUPER_ADMIN > ADMIN > DEPT_ADMIN > EDITOR > VIEWER`) — V48 Flyway 마이그레이션
  - 어드민 메뉴 접근 권한 카탈로그 (`admin_menu` / `admin_menu_permissions` 테이블) — V49 Flyway 마이그레이션
  - `GET /api/v1/me/permissions` — 현재 사용자 유효 권한 집합 조회 API (프론트 단일 진실 소스)
  - `GET /api/v1/admin/menus/accessible` — 접근 가능 메뉴 트리 API
  - Vue Router `beforeEach` 권한 가드 (`meta.permissions`/`meta.roles` 기반 → `/forbidden` 리디렉션)
  - `usePermission` 컴포저블 (`hasPermission`/`hasRole`/`canAccessMenu`) 및 Pinia `permissionStore`
  - `AdminLayout.vue` 하드코딩 `hasPermission` 스텁 제거 → 동적 메뉴 렌더링
  - `/forbidden` 403 접근 거부 전용 페이지 (`ForbiddenView.vue`)

- **AdminNotificationController 통합 테스트** (`AdminNotificationControllerIT`, SPEC-CMS-NC-IT-001)
  - `AbstractIntegrationTest` 상속, `pgvector/pgvector:pg16` TestContainers 기반
  - AC-NC-IT-001: `GET /api/v1/admin/notifications` — 200 OK + `PageResponse<AdminNotificationDto>` 구조 검증
  - AC-NC-IT-002: `?status=UNREAD/ARCHIVED` 파라미터 필터 검증 — 본인 알림만 반환
  - AC-NC-IT-003: `PATCH /{id}/read` — 204 No Content, DB `status=READ`, `read_at IS NOT NULL`
  - AC-NC-IT-004: `PATCH /read-all` — 200 OK, `{"updatedCount": N}`, UNREAD 전체 READ 전환
  - AC-NC-IT-005: `PATCH /{id}/archive` — 204 No Content, DB `status=ARCHIVED`, `archived_at IS NOT NULL`
  - AC-NC-IT-006: `GET /unread-count` — 200 OK, `{"unreadCount": N}`
  - AC-NC-IT-007: 권한 가드 — 비인증 401, USER 403, CONTENT_ADMIN 200
  - AC-NC-IT-008: 사용자 격리 — 타 사용자 알림 읽음 시도 시 403 (서비스 레이어 `AdminNotificationNotFoundException` → `GlobalExceptionHandler`)
  - 14개 테스트 케이스 전체 PASSED (BUILD SUCCESSFUL)

- **운영 활동 지표 KPI 4종 확장** (`KpiAggregationServiceImpl`, `KpiAggregationMapper`, `KpiActivityCards.vue`, `KpiActivityTrendChart.vue`, `KpiContentViewChart.vue`, SPEC-CMS-KPI-002)
  - **V53 마이그레이션**: `kpi_definition` 테이블에 DAU, MAU, CONTENT_VIEW, AVG_SESSION_DURATION, API_ERROR_RATE 5종 시드 INSERT
  - **백엔드 집계 SQL**: `upsertDau`, `upsertMau`, `upsertContentView`, `upsertAvgSessionDuration`, `upsertApiErrorRate` — 전체 `access_log` 단일 원천, 파티션 프루닝 + NULLIF 제로 나눗셈 방지
  - `AVG_SESSION_DURATION`: LAG 윈도우 함수 CTE 기반 30분 유휴 갭 세션 분할 알고리즘
  - `API_ERROR_RATE`: `status_code >= 500` 기준 (컬럼명: `status_code`)
  - **KpiAggregationServiceImpl**: 기존 3종 이후 신규 5종 try-catch 격리 블록 추가
  - **프론트엔드**: `KpiActivityCards.vue` + `KpiActivityTrendChart.vue` + `KpiContentViewChart.vue` 신규 위젯 3종 — `KpiDashboardView.vue` "운영 활동 지표" 섹션에 통합
  - `kpi.ts` KPI_CODES 5종 상수 + `kpiStore.ts` computed getter 5종 추가, ko/en i18n 키 추가
  - **IT 테스트**: `KpiAggregationKpi002IT` AC-001~AC-019 총 19건 전체 PASSED, `MigrationOrderIT` EXPECTED_MIGRATION_COUNT 51→52 갱신

- **플랫폼 KPI 통합 관리** (`AdminKpiController`, `KpiExportController`, `KpiAggregationServiceImpl`, `KpiQueryServiceImpl`, `KpiExportServiceImpl`, `KpiDashboardView.vue`, SPEC-CMS-KPI-001)
  - **V45 마이그레이션**: `kpi_aggregation_mv` Materialized View + access_log 파티션 보강(2026-06/07) + `idx_audit_log_export_time` 부분 인덱스
  - **백엔드 API**: `GET /api/v1/admin/kpi/values` (멀티필터 조회, JSONB containment), `GET /api/v1/admin/kpi/conversion-funnel`, `POST /api/v1/admin/kpi/export` (동기/비동기 SXSSFWorkbook), `GET /api/v1/admin/kpi/export/download` — 전체 `@PreAuthorize("hasRole('ADMIN')")`
  - **서비스**: `KpiAggregationJob` — access_log → kpi_value UPSERT + kpi_value_history 아카이브. `KpiExportServiceImpl` — <10K 동기, ≥10K 비동기, >1M 멀티시트 분할
  - **IT 테스트 24건**: `AdminKpiControllerIT` 9건, `KpiAggregationServiceImplIT` 4건, `KpiExportServiceImplIT` 9건, `KpiPerformanceIT` 2건 — AC-001~016 전체 커버
  - **프론트엔드**: `KpiDashboardView.vue` + `KpiFilterPanel.vue` + `KpiSummaryCards.vue` + `KpiTrendChart.vue` + `KpiConversionFunnel.vue` + `kpiStore.ts` + `kpiStore.spec.ts` (18건 유닛 테스트), ko/en i18n 추가, 라우터 등록

- **발간자료 카테고리 관리자 CRUD** (`PublicationCategoryAdminController`, `PublicationCategoryAdminServiceImpl`, `PublicationCategoryManagerView.vue`, SPEC-CMS-PUB-CAT-001)
  - **백엔드**: `GET /api/v1/admin/publication-categories` (INACTIVE 포함 전체 트리). `POST` — 루트/자식 카테고리 생성(201). `PUT /{id}` — 이름/정렬/상태 수정(200). `DELETE /{id}` — 리프 삭제(204). 하위 카테고리 존재 또는 연결된 발간자료 존재 시 409 Conflict
  - **DB 트리거 대응**: `trg_pub_cat_depth`가 `depth`를 자동 계산 → INSERT 시 depth 컬럼 제외, 저장 후 `findById` 재조회로 depth 반영
  - **`PublicationCategoryMapper`**: `insert`, `update`, `deleteById`, `existsByCode`, `findAllForAdmin`, `countChildren`, `countLinkedPublications` 7개 메서드 추가
  - **`GlobalExceptionHandler`**: `PublicationCategoryConflictException` → 409 Conflict 핸들러 등록
  - **프론트엔드**: `PublicationCategoryManagerView.vue` — depth-first 평탄화 트리 테이블, 생성/수정 다이얼로그, 삭제 확인. `/board/publication-categories` 라우터 등록. ko/en i18n `board.publicationCategories.*` 키 추가
  - **IT**: `PublicationCategoryAdminControllerIT` AC-PCA-001~005 전체 GREEN
- **댓글 모더레이션 관리자 API** (`CommentAdminController`, `CommentAdminServiceImpl`, `BbsCommentMapper`, `CommentManagementView.vue`, SPEC-CMS-COMMENT-MODERATE-001)
  - **백엔드**: `GET /api/v1/admin/comments` — 전체 게시판 댓글 목록 조회 (boardId/status/keyword 필터 + 페이징). `PATCH /api/v1/admin/comments/{id}/status` — VISIBLE/HIDDEN 상태 변경. `DELETE /api/v1/admin/comments/{id}` — 소프트 삭제 (idempotent)
  - **`BbsCommentMapper`**: `listForAdmin`, `countForAdmin`, `findStatusById`, `updateCommentStatus`, `findAdminSummaryById`, `adminSoftDelete` 6개 쿼리 추가. MyBatis `<constructor>` resultMap으로 `CommentAdminSummary` record 직접 매핑
  - **`GlobalExceptionHandler`**: `CommentModerationException` → 400 Bad Request 핸들러 추가. DELETED 댓글 상태 변경 시도 방어
  - **프론트엔드**: `CommentManagementView.vue` Element Plus 테이블 (게시판/상태/키워드 필터, 상태 뱃지, 액션 버튼, 페이지네이션). `/board/comments` 라우터 등록. ko/en i18n `board.comments.admin.*` 키 추가
  - **IT**: `CommentAdminControllerIT` 12/12 GREEN — AC-CMTM-001~006 전체 커버
- **감사 로그 action/severity 다중값 필터** (`AuditLogController.java`, `AuditLogMapper.java`, `AuditLogMapper.xml`, `auditLog.ts`, SPEC-CMS-AUDIT-LOG-MULTI-FILTER-001)
  - **백엔드**: `AuditLogController.search` / `export` 메서드의 `action`, `severity` 파라미터를 `String` → `List<String>`으로 확장. Spring MVC 반복 파라미터(`?action=CREATE&action=UPDATE`) 자동 바인딩
  - **`AuditLogMapper`**: `search`, `countSearch`, `searchForExport` 3개 메서드의 `@Param` 시그니처를 `List<String>`으로 일괄 변경
  - **`AuditLogMapper.xml`** 공통 `whereClause`: `action`/`severity` 단일 등치 조건 → 비어있지 않은 컬렉션 검사 + `<foreach> IN (...)` 절로 교체. search/count/export 3개 경로 동시 반영
  - **프론트엔드**: `AuditLogFilter.action?`, `.severity?` 타입을 단일값에서 배열(`AuditAction[]`, `AuditSeverity[]`)로 변경. `buildFilter()` 전체 배열 전송 (첫 번째 값 절단 제거). `client.ts` `paramsSerializer: { indexes: null }` 추가로 반복 파라미터 직렬화
  - **IT**: `AuditLogMultiFilterIT` 신설 — `postgres:16-alpine` Testcontainers, AC-ALF-001~004 5건 전체 GREEN
  - CHANGELOG v2.5.0 알려진 제한 사항(백엔드 단일값 필터 제약) 해소
- **게시글 버전 히스토리 뷰어**: `GET /api/v1/board/posts/{postId}/history` 페이지네이션 API + 관리자 UI 히스토리 탭 (SPEC-CMS-POST-HISTORY-001)
- **게시글 예약 발행**: `POST /api/v1/board/posts/{postId}/schedule` API + `PostPublishJob` 배치 잡(1분 주기) + 관리자 폼 예약 picker (SPEC-CMS-POST-SCHEDULE-001)
- **알림 발송 통계 대시보드** (`NotificationStatController`, `NotificationStatServiceImpl`, `NotificationStatMapper`, `NotificationStatPanel.vue`, SPEC-CMS-NOTIFICATION-STAT-001)
  - **DB 마이그레이션**: `V46__notification_delivery_status.sql` — `user_notification_inbox`에 `delivery_status VARCHAR(10) NULL` additive 컬럼 추가 (CONSTRAINT `chk_uni_delivery_status` IN ('SENT','FAILED','PENDING')). NULL=SENT 백필 불요
  - **백엔드 5개 엔드포인트** (모두 `@PreAuthorize("hasAnyRole('SUPER_ADMIN','CONTENT_ADMIN','ADMIN')")`):
    - `GET /api/v1/admin/notifications/stats/summary` — today/7일/30일 구간 발송·읽음율·미읽음·오류 수 집계
    - `GET /api/v1/admin/notifications/stats/by-category` — type별 발송·읽음 건수 (구간 미지정 시 최근 30일 기본)
    - `GET /api/v1/admin/notifications/stats/daily-trend` — `generate_series` gap-fill 일별 추이 (구간 상한 90일 캡 적용)
    - `GET /api/v1/admin/notifications/stats/errors` — `delivery_status IN ('FAILED','PENDING')` 알림 페이지네이션 목록
    - `PATCH /api/v1/admin/notifications/stats/errors/{id}/resend` — `delivery_status=SENT` 갱신 + `@AuditLog` 감사 기록
  - **`NotificationStatMapper`** (MyBatis): LATERAL JOIN CROSS-PERIOD 요약, gap-fill `generate_series` 일별 추이, 오류 페이지네이션, delivery_status UPSERT 6개 쿼리
  - **`KpiValueMapper#upsertNotificationKpi`**: 알림 건전성 KPI `kpi_value` ON CONFLICT UPSERT — SPEC-CMS-KPI-001 미배포 시 graceful no-op (DataAccessException catch)
  - **데이터 소스 불변**: `user_notification_inbox`(V35) 단일 진실 원천. `admin_notification` 발송 모수 혼용 없음
  - **프론트엔드** (`NotificationStatPanel.vue`, `notificationStatStore.ts`, `notificationStat.ts`): 요약 4카드, vue-echarts LineChart 일별 추이, 카테고리 테이블, 오류 목록 + 재발송 버튼 + el-pagination. `DashboardView.vue` additive 통합
  - **단위 테스트**: `NotificationStatServiceTest` 9개 GREEN (getSummary, getDailyTrend_capAt90Days, getByCategory_defaultsToLast30Days, getErrors 페이지네이션, resend, refreshKpiFeed graceful degradation 등)

- **비회원 창업기업 가상 시뮬레이션 환경 확장** (`SimulationServiceImpl`, `SimulationWizardView.vue`, `SimulationResultView.vue`, SPEC-CMS-SIM-001)
  - **V45 마이그레이션**: `ai_simulation_session`에 `employee_count`, `horizon_years`(CHECK 3 또는 5), `recommended_policies`(JSONB) 컬럼 추가
  - **백엔드 DTO 확장**: `SimulationStartDto` — 직원수·투영기간(3~5년, 기본 3 보정) 추가. `SimulationResultDto` — `horizonApplied`, `recommendedPolicies` echo 필드 추가. `client_ip_hash` SHA-256 불변식 유지
  - **공개 위저드 UI** (`SimulationWizardView.vue`, `SimulationResultView.vue`, `SimulationDownloadView.vue`): 6개 입력 필드 검증, 매출 투영 표, PDF 다운로드 — 비회원 허용 라우트 3개
  - **`simulationStore.ts`**: Pinia 스토어 (currentResult/loading/error + start/getResult/downloadPdf 액션)
  - **단위 테스트**: `SimulationServiceImplExtendTest` 5개, `PdfGeneratorServiceTest` GREEN. `MigrationOrderIT` baseline 갱신. ko/en i18n simulation 네임스페이스 추가

- **Q&A 관리자 모더레이션 패널** (`QnaAdminController`, `QnaAdminServiceImpl`, `QnaMapper`, `QnaManagementView.vue`, SPEC-CMS-QNA-MODERATE-001)
  - **V44 마이그레이션**: `chk_qna_answer_set` 제약조건 — HIDDEN 상태 허용으로 확장
  - **백엔드**: `GET /api/v1/admin/qnas` (status/keyword 필터 + 페이징, HIDDEN 포함 전체 조회). `PATCH /{id}/status` — PENDING/ANSWERED/CLOSED/HIDDEN 변경. `DELETE /{id}` — 소프트 삭제
  - **`QnaMapper`**: `listForAdmin`, `countForAdmin` 메서드 추가
  - **IT**: `QnaAdminControllerIT` AC-QNA-ADM-001~004 9개 전체 GREEN
  - **프론트엔드**: `qnaAdmin.ts` API, `QnaManagementView.vue`, 라우터 등록, ko/en i18n 추가

- **게시물 별점 리뷰 시스템** (`BbsPostReview`, `BbsPostReviewMapper`, `ReviewController`, `ReviewAdminController`, `PostReviewSection.vue`, `ReviewManagementView.vue`, SPEC-CMS-REVIEW-001)
  - **V55 마이그레이션**: `bbs_post_review` 테이블 DDL + `bbs_post`에 `average_rating`/`review_count` additive 컬럼 추가 + `REVIEW:READ/WRITE/DELETE` 권한 시드 + `admin_menu` "리뷰 관리" 시드 (`/admin/reviews`)
  - **백엔드 엔티티/DTO**: `BbsPostReview` 엔티티 (status: VISIBLE/HIDDEN/DELETED, rating 1-5 CHECK), `BbsPostReviewCreateRequest`, `BbsPostReviewResponse`, `BbsPostReviewAdminListResponse` Java records
  - **공개 API**: `GET /api/v1/posts/{postId}/reviews` (비인증 허용, VISIBLE만 반환), `POST /api/v1/posts/{postId}/reviews` (인증 필요, 401 방어)
  - **관리자 API**: `GET /api/v1/admin/reviews` (REVIEW:READ 권한, 페이지네이션 + 상태 필터), `PATCH /api/v1/admin/reviews/{id}/hide` (HIDDEN 전환), `DELETE /api/v1/admin/reviews/{id}` (DELETED, idempotent, 비가역)
  - **집계 로직**: 리뷰 생성/숨김/삭제 시 `BbsPost.average_rating`, `review_count` 실시간 재집계 (VISIBLE 리뷰만 모수, 서비스 계층 갱신)
  - **프론트엔드**: `ReviewManagementView.vue` (268행) 관리자 리뷰 목록/숨김/삭제 + `PostReviewSection.vue` (205행) 공개 별점 표시/작성 UI + `reviewApi.ts` API 클라이언트
  - **테스트**: `ReviewManagementView.spec.ts` (160행) + 관리자 컨트롤러 IT 포함 전체 인수 기준 커버

- **게시글 관리자 모더레이션 패널** (`PostAdminController`, `PostAdminServiceImpl`, `BbsPostMapper`, `PostManagementView.vue`, SPEC-CMS-POST-MODERATE-001)
  - **백엔드**: `GET /api/v1/admin/posts` (bbsId/status/keyword 필터 + 페이징, HIDDEN 포함 교차 게시판 조회). `PATCH /{id}/status` — 상태 변경. `DELETE /{id}` — 강제 삭제
  - **`BbsPostMapper`**: `listForAdmin`, `countForAdmin`, `updateStatusByAdmin`, `findAdminSummaryById` 4개 메서드 추가. `bbs_master` JOIN + 동적 WHERE 필터
  - **IT**: `PostAdminControllerIT` AC-PA-001~004 10개 전체 GREEN
  - **프론트엔드**: `postAdmin.ts` API, `PostManagementView.vue`, 라우터 등록, ko/en i18n 추가

### Fixed

- **게시글/공지 목록 API `?lang=en` 파라미터 처리** — 영어 제목 반환 (SPEC-CMS-NOTICE-I18N-002)

---

## [2.6.1] - 2026-06-09

### Fixed

- **백엔드 IT Spring 컨텍스트 로드 복구** (`MlServiceClientTestStub.java`, `MailTestStubConfig.java`, SPEC-CMS-TEST-INFRA-CONTEXT-RESTORE-001)
  - 근본 원인: `MlServiceClient`(`@Profile("!test")`)와 `JavaMailSender`(`spring.mail.host` 부재)가 CI test 프로파일에서 미생성 → `@SpringBootTest` 컨텍스트 로드 실패 (349/2007 테스트 연쇄 실패)
  - `MlServiceClientTestStub` — `@Profile("test") @Primary @Component` test stub (실제 ML 서비스 미호출, 빈 stub 응답 반환)
  - `MailTestStubConfig` — `@Profile("test") @TestConfiguration` stub (JavaMailSender NoOpMailSender 등록)
  - 운영 프로파일 동작 불변 확인 (두 stub 모두 `@Profile("test")` 스코프 한정) (커밋 54c3d01, d9e8a26)
  - CI GREEN 확인: origin/main 6dc5e24 — 349건 컨텍스트 연쇄 실패 해소

- **신규 엔드포인트 인가 IT 커버리지 복원** (`AuthorizationMatrixExpand5IT.java`, `AuthorizationCoverageArchTest.java`, SPEC-CMS-SECURITY-AUTHZ-IT-EXPAND-005)
  - `AuthorizationMatrixExpand5IT` 신설 — IT 미커버 운영 `@PreAuthorize` 엔드포인트 28건의 401/403 인가 시나리오 88건 추가 (BoardDomain/ContentDomain/SystemDomain/AuthUserDomain/DashboardPreferenceDomain 분류)
  - `AuthorizationCoverageArchTest` baseline 의도적 갱신: 메서드 카운트 113→124 (+11), IT endpoint set 110→138 (+28)
  - `@Disabled` 격리 3개 메서드 재활성화: `operational_preAuthorize_baselineCount`, `it_displayName_endpointBaselineCount`, `it_endpointSet_matchesBaseline88` — 4개 메서드 모두 GREEN
  - CI GREEN 확인: origin/main 6dc5e24 (커밋 6dc5e24)

---

## [2.6.0] - 2026-06-02

### Added

- **대시보드 자동 새로고침 주기 개인화** (`useDashboardAutoRefresh.ts`, `DashboardRefreshIndicator.vue`, SPEC-CMS-DASHBOARD-REFRESH-001)
  - **DB 스키마**: `user_dashboard_preference.refresh_interval_seconds INT DEFAULT NULL` 컬럼 추가 (V42 마이그레이션) — CHECK 제약으로 허용값(30/60/300/900/1800) 강제
  - **백엔드**: `PreferenceUpdateRequest`에 presence flag 패턴 (`hasRefreshIntervalSeconds`) 추가로 null(OFF) vs 필드 미전송 모호성 해결. 기존 `PATCH /api/v1/dashboard/preference` 엔드포인트 재사용 (신규 엔드포인트 0개)
  - **서비스 검증**: 허용값 화이트리스트(30,60,300,900,1800) + DB CHECK 이중 방어 — 비허용값 요청 시 400 반환
  - **`useDashboardAutoRefresh` 컴포저블** (`frontend/admin/src/composables/useDashboardAutoRefresh.ts`) — Page Visibility API 기반 탭 비가시 시 일시정지/재개, 언마운트 시 타이머·이벤트리스너 정리, 비가시 중 주기 경과 시 즉시 갱신
  - **`DashboardRefreshIndicator` 컴포넌트** — 카운트다운(초) 표시 + "지금 새로고침" 수동 트리거
  - **`DashboardPreferencePanel.vue`** — 자동 새로고침 주기 선택 라디오 그룹 추가 (끄기/30초/1분/5분/15분/30분)
  - **`DashboardMainView.vue`** — 컴포저블 + 인디케이터 헤더 영역 연결 완료. 위젯 부분 갱신 시 `Promise.allSettled` 사용으로 일부 실패가 다른 위젯에 영향 없음
  - **단위/통합 테스트**: 백엔드 15건(ServiceTest 11+4, IT 2+MigrationOrderIT 1) + 프론트엔드 22건 GREEN

---

## [2.5.0] - 2026-06-01

### Added

- **공지사항 다국어 지원 (i18n)** (`PostTranslationController.java`, `PostFormView.vue`, SPEC-CMS-NOTICE-I18N-001)
  - **DB 스키마**: `bbs_post_i18n` 테이블 신규 (V41 마이그레이션) — `post_id`, `language`, `title`, `content_html`, `content_text`, `updated_at` 컬럼 (커밋 2ecc4d8)
  - **백엔드 번역 CRUD API** (`/api/v1/board/posts/{postId}/translations`):
    - `GET /translations` — 전체 번역 목록 조회 (CONTENT_ADMIN+ 권한)
    - `PUT /translations` — 번역 등록/수정 upsert (CONTENT_ADMIN+ 권한)
    - `GET /translations/{language}` — 단건 번역 조회
    - `DELETE /translations/{language}` — 번역 삭제 (SUPER_ADMIN 전용) (커밋 2ecc4d8)
  - **`?lang` 파라미터 지원** (`PostController.java`) — `GET /api/v1/board/posts/{postId}?lang=en` 요청 시 번역 존재하면 en 버전 오버레이, 없으면 ko 원본 반환 + `Content-Language` 응답 헤더 (커밋 2ecc4d8)
  - **`PostService.upsertTranslation()` / `getTranslation()` / `deleteTranslation()` / `listTranslations()`** — `BbsPostI18nMapper`를 통한 번역 CRUD 서비스 메서드 (커밋 2ecc4d8)
  - **`PostFormView.vue` 언어 탭** — `el-tabs` 한국어(필수)/English(선택) 탭, 영어 번역 저장·삭제 버튼 (커밋 9c8864e)
  - **`PostListView.vue` EN 배지** — 번역 존재 시 목록 행에 EN 뱃지 표시 (커밋 9c8864e)
  - **`boardApi` 번역 함수** (`frontend/admin/src/api/board.ts`) — `upsertTranslation`, `deleteTranslation`, `getTranslation`, `listTranslations` 신규 (커밋 9c8864e)
  - **단위 테스트**: 백엔드 Mockito 기반 `PostTranslationServiceTest.java` + 프론트엔드 3건 GREEN (`tests/api/postTranslation.spec.ts`, AC-NI-003/AC-NI-008)

> **알려진 제한 사항**: 게시글 목록 API(`?lang=en`)는 현재 한국어 원본 목록을 반환합니다. 목록 번역 오버레이는 후속 작업으로 진행 예정입니다.

---

## [2.4.0] - 2026-06-01

### Added

- **사용자 일괄 상태 변경** (`BulkStatusRequest.java`, `BulkStatusResult.java`, `UserListView.vue`, SPEC-CMS-USER-BULK-STATUS-001)
  - **`PATCH /api/v1/users/bulk-status` 엔드포인트** — SUPER_ADMIN/DEPT_ADMIN 권한, 최대 100건, 부분 실패 허용 (커밋 ca356e8)
  - **백엔드 DTO**: `BulkStatusRequest.java` (userIds 최대 100건 + targetStatus), `BulkStatusResult.java` (successCount/failureCount/failures 목록)
  - **`UserServiceImpl.bulkUpdateStatus()`** — 건별 독립 처리(한 건 실패 시 나머지 계속 진행), SUPER_ADMIN만 DELETED 전환 가능, LOCKED→ACTIVE 전환은 기존 unlock 로직 재사용, DELETED 전환은 softDelete 적용 (커밋 ca356e8)
  - **`UserController.java`** — `PATCH /api/v1/users/bulk-status` 라우트 등록 (커밋 ca356e8)
  - **`UserListView.vue` UI** — `el-table-column type="selection"` 체크박스 다중선택, 일괄 작업 툴바 (선택 수 표시 + 상태 셀렉터 + 변경/해제 버튼), `executeBulkStatusChange` 핸들러: ElMessageBox 확인 다이얼로그 + ElMessage 결과 토스트, 최대 100건 초과 시 경고 (커밋 97f6e26)
  - **`usersApi.bulkUpdateStatus()`** — `src/api/users.ts` API 함수 신규 + `BulkStatusResult` 인터페이스 정의 (커밋 97f6e26)
  - **단위 테스트**: 백엔드 Mockito 6건 GREEN (`BulkUpdateStatusTest.java`) + 프론트엔드 2건 GREEN (`tests/api/usersBulkStatus.spec.ts`, AC-UBS-003/AC-UBS-006) (커밋 8b57c5d)

---

## [2.3.0] - 2026-06-01

### Added

- **감사 로그 화면** (`auditLog.ts`, `AuditLogView.vue`, SPEC-CMS-AUDIT-LOG-VIEW-001)
  - **Pinia 스토어** (`frontend/admin/src/stores/auditLog.ts`) 신규 — `fetchLogs`, `fetchCritical`, `applyFilter`, `resetFilter`, `exportCsv`, `dismissCritical` 액션 (커밋 e4bb9ec)
  - **AuditLogView.vue 완성** — 기존 스텁을 스토어에 연결, 다중 선택 action/severity 필터, actorId 필터, 세션 단위 CRITICAL 패널 dismiss, 페이지 크기 선택기(20/50/100), 행 클릭 상세 드로어(before/after JSON), 빈 상태 화면, CSV 내보내기 (커밋 e4bb9ec + 3cfaf73)
  - **단위 테스트** (`frontend/admin/tests/stores/auditLog.spec.ts`) 신규 — 9건 전체 GREEN (커밋 3cfaf73)
  - **i18n 키 추가** (`frontend/admin/src/locales/ko.json`, `en.json`) — `filter.actorId`, `criticalPanel.dismiss`, `empty`, `pageSize` 등 (커밋 e4bb9ec)
  - **기존 API 재사용**: `api/system.ts` auditLogs 변경 없음

> **알려진 제한 사항**: 백엔드 action/severity 필터가 단일 값만 지원하므로, 프론트엔드 다중 선택 시 첫 번째 선택값만 전송됩니다. 백엔드 다중 값 지원은 별도 후속 작업으로 진행 예정입니다.

---

## [2.2.1] - 2026-06-01

### Added

- **알림 센터 Playwright E2E 테스트** (`frontend/admin/tests/e2e/notification-center.spec.ts`, SPEC-CMS-NOTIFICATION-CENTER-001)
  - AC-NC-006 / AC-NC-007 / AC-NC-008 시나리오 11건 전체 GREEN (커밋 044d0c7)

---

## [2.2.0] - 2026-06-01

### Added

- **관리자 알림 센터** (`AdminNotificationController`, `NotificationCenterView.vue`, SPEC-CMS-NOTIFICATION-CENTER-001)
  - **DB 스키마**: `admin_notification` 테이블 신규 (V40 마이그레이션) — severity (INFO/WARN/ERROR), status (UNREAD/READ/ARCHIVED), ref_type/ref_id 딥링크 컬럼, 부분 인덱스 3개 (커밋 cb856e1)
  - **백엔드 REST API** 5개 엔드포인트:
    - `GET /api/v1/admin/notifications` — 페이지네이션·필터(severity/status/date) 목록 조회
    - `GET /api/v1/admin/notifications/unread-count` — 미읽음 수 배지 데이터
    - `PATCH /api/v1/admin/notifications/{id}/read` — 개별 읽음 처리 (멱등)
    - `PATCH /api/v1/admin/notifications/read-all` — 일괄 읽음 처리 (필터 선택 적용)
    - `PATCH /api/v1/admin/notifications/{id}/archive` — 보관 처리 (UNREAD→ARCHIVED 직접 전이 지원)
  - **백엔드 레이어**: `AdminNotification.java` 엔티티, `AdminNotificationDto.java` (목록/상세/미읽음 수 응답), `AdminNotificationMapper.java` + `AdminNotificationMapper.xml` (MyBatis), `AdminNotificationService.java`, `AdminNotificationNotFoundException.java`, `GlobalExceptionHandler.java` 수정 (커밋 cb856e1)
  - **프론트엔드**: `adminNotifications.ts` API 클라이언트, `notificationCenter.ts` Pinia 스토어 (fetch/markRead/markAllRead/archive/setFilter/fetchUnreadCount 액션) (커밋 cb856e1)
  - **30초 폴링**: `useUnreadCountPolling.ts` 컴포저블 — 탭 비활성(visibilityState='hidden') 시 폴링 일시 중지, 탭 복귀 시 즉시 1회 호출 후 재개
  - **딥링크 라우팅**: `notificationDeepLink.ts` — ref_type (POST/COMMENT/POLICY_PROGRAM/NOTIFICATION_SEND/INTEGRATION_LOG) → 관리자 라우트 매핑
  - **NotificationCenterView.vue**: severity 다중 선택 필터, status 라디오, 날짜 범위 필터, "모두 읽음" 버튼, 빈 상태 일러스트
  - **헤더 배지**: `AdminLayout.vue` 종 아이콘에 미읽음 수 배지 (0개 시 미표시, 99+개 이상 '99+' 표시)
  - **라우트**: `/admin/notifications` 신규 등록 (`router/index.ts`)
  - **i18n**: `ko.json` / `en.json` 알림 관련 키 추가 (severity/type/status 표시명, 필터·버튼·빈 상태 라벨)
  - **접근성**: `aria-live="polite"` 신규 알림 통지, 헤더 배지 `aria-label="미읽음 알림 N개"` 동적 갱신 (KWCAG 2.2 AA)
  - **단위 테스트**: `notificationCenter.spec.ts` 8/8 GREEN, `notificationDeepLink.spec.ts` 8/8 GREEN (총 16건) (커밋 cb856e1)
  - SPEC 상태 Implemented 갱신 (커밋 b581ee6)

---

## [2.1.0] - 2026-05-29

### Added

- **대시보드 개인화 설정** (`DashboardPreferenceStore`, `DashboardPreferencePanel.vue`, SPEC-CMS-DASHBOARD-PERSONALIZE-001)
  - `GET/PATCH /api/v1/dashboard/preference` 엔드포인트 연동 — 테마·밀도·폰트 크기·색상 팔레트·사이드바 접힘 여부 저장
  - `DashboardPreferencePanel.vue` 슬라이드오버 패널: Element Plus 컴포넌트 기반 UI (커밋 ba26f74)
  - `useDashboardPreferenceApply()` 컴포저블: `<html data-theme="dark|light">` 속성 자동 반영 (커밋 738a075)
  - `DashboardPreferenceStore` Pinia 스토어: `fetch()`, `setTheme()`, `toggleVisibility()`, `showAllWidgets()`, `reset()` 액션
  - 디바운스 300ms 자동 저장 — 설정 변경 즉시 반영, 네트워크 요청 최소화
  - `hidden_widget_instance_ids` 레이아웃별 위젯 숨김 관리, `layoutId` 미선택 시 전체 레이아웃 합산 표시
  - Playwright E2E 3건 (AC-DP-001 테마, AC-DP-002 위젯 숨김, AC-DP-003 초기화) GREEN (커밋 8ddebea)

### Fixed

- **`DashboardMainView` 설정 패널 열림 버그** (`DashboardMainView.vue`)
  - `v-model:visible` → `v-model` 수정: `DashboardPreferencePanel`이 `modelValue` prop을 사용하므로 `v-model:visible`은 `visible` prop을 바인딩하여 패널이 열리지 않던 문제 해결 (커밋 738a075)

- **`DashboardPreferencePanel` 위젯 숨김 목록 미표시 버그**
  - `layoutId` prop 미전달 시 `hiddenInstances`가 항상 빈 배열 반환하던 문제 — 전체 레이아웃 키를 순회해 합산하도록 수정 (커밋 738a075)

---

## [1.7.0] - 2026-05-27

### Added

- **공개 사이트 시민 회원가입 API** (`AuthController`, `AuthServiceImpl`, `PublicRegisterRequest.java`)
  - `POST /api/v1/auth/register` 엔드포인트 구현 (anonymous 허용)
  - 중복 검사 → 비밀번호 정책 → PII 암호화 → users INSERT → MEMBER 역할 부여 → 토큰 발급
  - V36 마이그레이션: MEMBER 역할 시드 (커밋 98d2447)

- **안전정보 공개 API** (`SafetyGuidelineController`, `GuidelineTemplateMapper`)
  - `GET /api/v1/safety/guidelines` — 공개 가이드라인 목록·상세 엔드포인트 (인증 불필요)
  - `GET /api/v1/safety/incidents` — 공개 사고사례 목록 엔드포인트 공개 접근 허용
  - `GuidelineSummaryResponse`, `GuidelineDetailResponse` DTO 추가 (커밋 18fd1f2)

- **알림 수신 설정 GET 엔드포인트** (`MeController`, `QnaNotificationService`)
  - `GET /api/v1/me/notifications/preferences` — 현재 이메일 수신 여부 반환
  - 어드민 `meApi.getQnaNotificationPreference()` 추가 및 `NotificationSettingsView` onMounted 연동 (커밋 5c40863)

- **내 Q&A 목록 API** (`QnaController`, `QnaMapper`)
  - `GET /api/v1/qnas?mine=true` — 로그인 사용자 본인 Q&A만 조회
  - `QnaMapper.xml` mine=true 시 `questioner_id` 조건 분기 추가 (커밋 82d36ff)

- **HashiCorp Vault Transit PII 키 볼트 어댑터** (`VaultTransitPiiKeyVault.java`, SPEC-CMS-SECURITY-PII-KMS-001)
  - Spring Cloud Vault `VaultTemplate.opsForTransit().decrypt()` 기반 어댑터 구현
  - `@ConditionalOnProperty` vault-transit 조건부 활성화
  - `ConcurrentHashMap` 부팅 시 1회 로딩, fail-fast (Vault 미가용 시 `PiiKeyVaultException` 부팅 차단)
  - Testcontainers vault:1.17 통합 테스트 3 AC, 단위 테스트 4 GREEN (커밋 2a9e532)

- **공개 프론트엔드 완성** (회원가입·로그인·내 정보·내 Q&A·정책 알림 구독)
  - `RegisterView.vue`: 이메일/이름/비밀번호 입력, 409 중복·비밀번호 불일치 오류 분기
  - `MeView.vue`: 프로필 표시·수정 + 비밀번호 변경 폼, 변경 후 자동 로그아웃
  - `MyQnaListView.vue`: mine=true로 본인 Q&A만 조회, 상태 필터 제공
  - `PolicySubscriptionView.vue`: 채널×카테고리 구독 체크박스 그리드
  - `PublicHeader`: 인증 시 사용자명+로그아웃, 비인증 시 로그인 버튼 표시
  - (커밋 1cdd396, b464efb, 82d36ff, 70dee61)

- **공개 프론트엔드 API 매핑 및 페이지 완성** (커밋 a4da59e)
  - `noticeApi`, `policyApi`, `faqApi` 백엔드 응답 타입 매핑 추가
  - `FaqView` 아코디언 펼칠 때 답변 lazy 로드
  - `AboutView` 기관소개 페이지 신규 생성

- **관리자 프론트엔드 기능 추가**
  - `TiptapEditor` 이미지 업로드 연동 (`uploadImage` prop, 툴바 추가) (커밋 c23bdf4)
  - FAQ `answerHtml`, Q&A `answerInput`, 발간자료 `contentHtml` TiptapEditor 교체
  - FAQ 답변 HTML 조회: 편집 다이얼로그 열기 전 `getFaq()` 호출로 `answerHtml` 정확히 로드
  - Q&A 답변 수정: ANSWERED 상태에서도 관리자 답변 수정 가능 (커밋 4f993d1)
  - 팝업 활성/비활성 PATCH 엔드포인트 추가 (`PopupController`, `PopupMapper`) (커밋 8c96b2d)
  - 메뉴별 방문 통계에 메뉴명 컬럼 추가 (`MenuStatsView`, `MenuPageStatsResponse`)

- **미디어 컬렉션 상세 편집** (`MediaCollectionView.vue`, `media.ts`, SPEC-CMS-003 연계)
  - `getCollection`, `addToCollection`, `removeFromCollection`, `deleteCollection` API 4개 추가
  - 상세 편집 다이얼로그: 아이템 그리드 + 제거 버튼 + 삭제 popconfirm
  - 미디어 피커 다이얼로그: 검색 + 다중 선택 + 일괄 추가 (커밋 0e0625a)

- **Tiptap WYSIWYG 에디터 통합** (`TiptapEditor.vue`, SPEC-CMS-003)
  - StarterKit + Underline + Link + Image + Table 확장
  - 한국어 aria-label 툴바 19개 요소, WCAG AA 4.5:1 포커스 인디케이터
  - v-model HTML 문자열 바인딩, 외부값 변경 시 무한루프 방지 watch (커밋 906abf4)

- **DB 마이그레이션 추가**
  - V37: 사고사례 테스트 시드 8건 (건설·제조·화학·물류·전기 업종별 FATAL/SEVERE/MINOR)
  - V38: 안전 가이드라인 템플릿 5건 + 체크리스트 36건 시드 (커밋 5489ac8)

- **공개 프론트엔드 ESLint 9 flat config + Vitest 커버리지 품질 게이트** (커밋 e4b04ad)

### Fixed

- **Q&A 공개 조회 허용** (`QnaController`, `SecurityConfig`)
  - `@PreAuthorize("isAuthenticated()")` 제거, `GET /api/v1/qnas/**` permitAll 추가
  - `BbsViewLogMapper`: PostgreSQL JDBC null 파라미터 타입 추론 실패 수정 — MyBatis `<choose>` 분기로 null 비교 파라미터 제거 (커밋 ef0a350)

- **안전 가이드라인 공개 API PageResponse 반환** (`SafetyGuidelineController`)
  - 목록 API 단건 반환 → `PageResponse` 형식 통일 (커밋 1b32261)

- **PostController 권한 완화 및 관련 IT URL 수정** (커밋 d2a1eaf)
  - 공개 게시물 조회 시 불필요 권한 요구 제거
  - 관련 통합 테스트 URL 경로 수정

- **Spring Cloud Vault 자동 설정 IT 컨텍스트 로딩 실패** (커밋 fdacf16)
  - `application-local.yml` Spring Cloud Vault 비활성화 설정 추가로 로컬 IT 환경 컨텍스트 로드 정상화

- **관리자 프론트엔드 버그 수정**
  - 공통코드 조회 파라미터 `group_code` → `groupCode` 수정 (`system.ts`) (커밋 8c96b2d)
  - 팝업 목록 필드 매핑 오류 수정 (`PopupManagerView`)
  - `PopupResponse` `isActive` 파생 필드 추가
  - ESLint flat config 적용 및 미사용 변수 정리 (커밋 b4de1f6)

- **공개 프론트엔드 API 경로 수정** (커밋 acc9e6e)
  - 여러 API 호출 경로를 실제 백엔드 경로로 수정

- **통합/단위 테스트 5+4건 실패 수정** (커밋 b48cfcd)

### Changed

- **관리자 프론트엔드 버전 0.1.2 → 0.1.3** (커밋 8497293)

- **미디어 갤러리·대시보드 위젯 공개 허용** (`SecurityConfig`)
  - `GET /api/v1/media`, `GET /api/v1/dashboard/widgets/**` permitAll 추가 (커밋 18fd1f2)

### Tests

- **백엔드 테스트 baseline 갱신 — 336건 전체 PASS** (커밋 ee9446f)
  - `QnaControllerTest`, `QnaServiceTest`: mine 파라미터 추가
  - `PopupControllerTest`: `PopupResponse` 생성자 필드 추가 (name, yOffset, height, isActive)
  - `SafetyManagementIT`: 공개 API 변경에 따른 기대값 401→200
  - `MigrationOrderIT`: V37/V38 추가로 기대 마이그레이션 수 35→37
  - `AuthorizationCoverageArchTest`: QnaController close/delete 추가로 baseline 112→113
  - `DashboardView·SystemDashboardView` 테스트 API 모킹 정합성 확보 (커밋 bbb972b)

---

## [1.6.3] - 2026-05-21

### Changed

- **단위 테스트 전체 GREEN 달성** — 1963건 0 실패 (커밋 e6526e6, 2ae6d48, cd32f16)
  - 프로덕션 코드 변경에 따른 단위 테스트 불일치 수정 (11개 파일)
    - `SearchServiceTest`: `searchLogMapper.insert()` → `searchLogAsyncService.insertSync()` 검증 경로 수정
    - `SearchControllerTest`: 실제 DTO에 없는 `zeroResultRatio`, `avgResponseMs` 필드 단언 제거
    - `PostControllerTest`, `BbsMasterControllerTest`, `CommentControllerTest`, `BoardExceptionHandlerTest`: 컨트롤러 실제 URL에 맞게 수정 (`/boards/**` → `/board/masters/**`, `/board/posts/**`)
    - `DashboardServiceTest`: healthStatus `"UP"` → `"HEALTHY"`, errorRate `5.0` → `0.05` (소수 비율)
    - `AuthorizationCoverageArchTest`: @PreAuthorize 기준선 `103L` → `114L` 동기화
    - `QnaNotificationServiceImplTest`: 누락 @Mock 5개 추가
    - `AuthServiceTest`: `generateAccessToken` 4인자 시그니처 스텁 수정
    - `CodeGroupControllerTest`: URL `/system/code-groups` → `/system/codes/groups` 수정
  - pgvector 없는 로컬 테스트 환경 ApplicationContext 로드 오류 해결
    - `src/test/resources/application.properties` 신설: `spring.flyway.target=32` (V33 pgvector 마이그레이션 우회)
    - `src/test/resources/application-integration.yml`: `spring.flyway.target=latest` 명시 (Docker CI 환경에서 V33 정상 적용 유지)
  - AuthorizationMatrix IT URL 불일치 및 @WebMvcTest 누락 MockBean 수정 (8개 파일)
    - `AuthorizationMatrixExpand3IT`, `AuthorizationMatrixExpand4IT`, `AuthorizationMatrixExpandIT`: 컨트롤러 실제 URL에 맞게 수정
    - `DashboardControllerTest`, `RetentionPolicyControllerTest`, `I18nControllerTest`, `StatsControllerTest`: 누락 @MockBean 추가
    - `AuditLogExportIT`: `StreamingResponseBody` async dispatch 시 SecurityContext 유실 우회

---

## [1.6.2] - 2026-05-20

### Fixed

- **접속로그 검색 결과 없음 버그** (`AccessLogSearchRequest.java`)
  - Java record `offset()` 커스텀 메서드가 MyBatis `#{req.offset}` 바인딩 실패 → LIMIT/OFFSET 0으로 전체 데이터 미조회
  - `getOffset()` 표준 빈 프로퍼티 추가로 MyBatis `RecordWrapper` 정상 해석 (커밋 b9c2059)

- **공개 홈 공지사항 오류 버그** (`SecurityConfig.java`, `noticeApi.ts`)
  - `SecurityConfig` `permitAll` 경로가 `/api/v1/boards/**` (복수형)로 잘못 설정 → 실제 경로 `/api/v1/board/**` (단수형)와 불일치, 익명 GET 401 반환
  - `noticeApi.ts` 호출 경로 `/boards/code/NOTICE`, `/boards/{id}/posts` → 실제 백엔드 경로 `/board/masters/code/{code}`, `/board/posts?bbsId={id}`로 수정 (커밋 b9c2059)

### Security

- **SurveyRespondView XSS 방어 강화** (`SurveyRespondView.vue`) — S1
  - `v-html="survey.descriptionHtml"` 원시 HTML 바인딩 → `useSafeHtml` 컴포저블의 DOMPurify `sanitize()` 래핑으로 교체 (커밋 e169930)

- **BannerController 권한 우회 차단** (`BannerController.java`) — S3
  - `GET /api/v1/banners?siteId=*` 요청 시 `CONTENT:READ` 권한 없는 인증 사용자도 전체 배너 목록 접근 가능 → `principal.permissions()` 프로그래밍적 검사 후 `AccessDeniedException` 추가 (커밋 e169930)

- **PostController 쓰기 API 권한 어노테이션 추가** (`PostController.java`) — W2
  - `POST/PUT/DELETE /api/v1/board/posts/**` 메서드에 `@PreAuthorize` 미적용 → `anyRequest().authenticated()` 만으로 방어, CONTENT:WRITE 검증 누락
  - `hasAuthority('CONTENT:WRITE') or hasRole('ADMIN') or hasRole('SUPER_ADMIN') or hasRole('CONTENT_ADMIN')` 어노테이션 추가 (커밋 e169930)

---

## [1.6.1] - 2026-05-20

### Added

- **감사 로그 CSV 스트리밍 내보내기** (`AuditLogController.export()`, SPEC-CMS-SECURITY-PII-FOLLOWUP-002)
  - `GET /api/v1/audit-logs/export` — ADMIN/SUPER_ADMIN 또는 `SYSTEM:AUDIT` 권한 필요 (401/403 반환)
  - 7개 필터 파라미터: `action`, `entity_type`, `severity`, `result`, `actorId`, `fromTime`, `toTime`
  - MyBatis `ResultHandler<AuditLog>` 커서 스트리밍 (`fetchSize=1000`, `FORWARD_ONLY`) — OOM 없이 대용량 처리
  - Spring MVC `StreamingResponseBody` 비동기 청크 전송
  - RFC 4180 CSV 이스케이핑 (콤마·큰따옴표·개행 포함 필드 처리)
  - `Content-Disposition: attachment; filename="audit-logs-{ISO_LOCAL_DATE}.csv"`
  - 통합 테스트 `AuditLogExportIT` 추가 (401/403/200 인증·인가 검증)

---

## [1.6.0] - 2026-05-20

### Added

- **Python FastAPI ML 추론 서비스** (`ml-service/`, 30개 파일, SPEC-CMS-ML-SERVICE-001)
  - 런타임: Python 3.11, FastAPI, Pydantic v2, sentence-transformers
  - 7개 엔드포인트 구현 (OpenAPI 계약 `docs/ai-ml-service-openapi.yaml` 준수):
    - `POST /ml/v1/growth-stage` — SEED/STARTUP/GROWTH/EXPANSION/MATURITY 성장단계 예측
    - `POST /ml/v1/risk-score` — GREEN/YELLOW/RED 위험 등급 분류
    - `POST /ml/v1/simulation` — 매출·직원수 결정적 규칙 기반 프로젝션
    - `POST /ml/v1/policy-match` — top-K 정책 시맨틱 매칭 (코사인 유사도)
    - `POST /ml/v1/embed` — 384차원 문장 임베딩 (paraphrase-multilingual-MiniLM-L12-v2, SHA-256 해시 폴백)
    - `POST /ml/v1/rag` — 환각 가드 포함 RAG 답변 생성 (빈 컨텍스트 → 고정 메시지)
    - `GET /ml/v1/health` — 로드된 모델 목록 포함 헬스 체크
  - 보안: 모든 Pydantic 스키마 `extra="forbid"` (PII 가드), 내부 Docker 네트워크 전용 (외부 포트 미노출), IP/session_ref SHA-256 해시 처리, 기업 프로필 5개 필드만 허용 (`ksic_code`/`employee_count`/`growth_stage`/`region_code`/`annual_revenue`)
  - Docker: `python:3.11-slim` 이미지, 빌드 시 sentence-transformers 모델 사전 다운로드
  - `deploy/docker-compose.prod.yml`: ml-service 내부 네트워크 추가, `ML_SERVICE_URL` 환경변수 주입
  - 테스트: 34/34 pytest 통과 (embed, growth_stage, health, policy_match, rag, risk_score, simulation)

---

## [1.5.0] - 2026-05-18

### Changed

- **DB 문서 V27~V33 전면 갱신** (`.moai/project/db/`)
  - `schema.md`: manifest_hash `v26` → `v33`, pgvector 확장 추가, `bbs_master` soft-delete 컬럼 반영, `policy_program` 임베딩 컬럼(embed_vector/embedded_at/embed_model_version) 반영, AI 도메인 6개 테이블 신규 섹션 추가
  - `migrations.md`: 최종 적용 V26 → V33, V27~V33 Applied Migrations 행 추가, Rollback Notes·Security Migration Notes 갱신
  - `erd.mmd`: last generated 2026-05-18로 갱신, ~37 엔티티 AI 도메인 포함, `bbs_master` soft-delete 마커 추가, `policy_program` pgvector 컬럼 추가, AI 도메인 6개 엔티티 블록 추가, AI 관계 라인 추가

### Added (DB 마이그레이션 문서화)

- **V27** `bbs_master` soft-delete: `deleted_at TIMESTAMPTZ` + `idx_bbs_master_active` 부분 인덱스
- **V28** `ai_prediction_log`: 예측 결과 비동기 로그 (GROWTH_STAGE/RISK_SCORE/SIMULATION), PII 제외 입력 특징 JSONB
- **V29** `ai_simulation_session`: 비회원 시뮬레이션 세션, `client_ip_hash` SHA-256 전용, TTL 24h
- **V30** `ai_model_metric`: RMSE·정확도·지연시간 집계, `drift_detected` 불리언, UNIQUE upsert 제약
- **V31** `ai_retrain_queue`: QUEUED→ACKNOWLEDGED→IN_PROGRESS→DONE→CANCELED 상태 흐름
- **V32** `ai_policy_recommendation_log`: session_ref SHA-256 해시, company_profile PII 화이트리스트(5개 필드)
- **V33** pgvector 확장(`CREATE EXTENSION IF NOT EXISTS vector`) + `policy_program.embed_vector vector(384)` + IVFFlat cosine 인덱스(lists=100) + `ai_rag_query_log`

---

## [1.4.0] - 2026-05-18

### Added

- GitHub Actions CD 워크플로 (`.github/workflows/cd.yml`): `main` push → CI 완료 후 운영 서버 SSH 자동 배포
  - `appleboy/ssh-action` 기반 `docker compose` 롤링 업데이트
  - concurrency guard (`deploy-production`) — 동시 배포 방지
  - 헬스 체크 60초 폴링 (DEPLOY_HEALTH_URL Secret 선택)
  - Slack 배포 알림 (SLACK_WEBHOOK_URL Secret 선택)
- `deploy/README.md`: GitHub Secrets 등록 방법 + 운영 서버 사전 준비 절차 추가

### Fixed

- `deploy/docker-compose.yml`, `deploy/docker-compose.prod.yml`: `postgres:16-alpine` → `pgvector/pgvector:pg16` (SPEC-CMS-AI-003 RAG pgvector 확장 지원)
- `.github/workflows/ci.yml`: CI postgres 서비스 이미지 동일 적용
- `deploy/.env.example`: `ML_SERVICE_URL` 환경변수 추가 (Python ML 서비스 내부망 URL, 외부 노출 금지)
- `deploy/docker-compose.prod.yml`: 백엔드 서비스에 `ML_SERVICE_URL` 주입

---

## [1.3.0] - 2026-05-18

### Added

- RAG 질의응답 — 자연어 질문 기반 정책 검색·생성형 답변 (SPEC-CMS-AI-003) — 옵션 트랙 P1 완전 구현
  - **RAG 질의응답** (`POST /api/v1/ai/rag/query`): 자연어 질문 → embed(384차원) → pgvector cosine 유사도 검색 → FTS 하이브리드 재랭킹 → LLM 생성형 답변, `degraded=true` 폴백 지원
  - **pgvector 코사인 검색**: PostgreSQL `vector(384)`, IVFFlat 인덱스(lists=100, probes=10), `policy_program` 임베딩 컬럼 3개(`name_embedding`, `summary_embedding`, `combined_embedding`)
  - **CircuitBreaker 폴백**: Resilience4j ml-service OPEN 시 FTS 단독 검색으로 자동 폴백 — 503 미반환, 200 + `degraded=true` 반환
  - **RAG 쿼리 캐시**: Caffeine ragQueryCache (TTL 15분) — 동일 질문 SHA-256 해시 기반 캐시 키, degraded 응답 미캐싱
  - **질의 로그 비동기 적재**: `@Async("aiLogExecutor")` — query_ref·cache_hit·latency_ms·degraded 플래그 기록
  - **사용자 피드백** (`POST /api/v1/ai/rag/feedback`): HELPFUL/NOT_HELPFUL/INCORRECT 피드백 비동기 적재
  - **DB 마이그레이션 V33**: pgvector 확장 활성화 + `policy_program` 임베딩 컬럼 3개 + IVFFlat 인덱스 + `ai_rag_query_log` 테이블
  - **관리자 모니터링** (`GET /api/v1/admin/ai/rag/metrics`, ROLE=ADMIN 전용): 만족도 비율·캐시 히트율·평균 응답시간·degraded 비율 시계열 집계
  - **OpenAPI 3.1 계약** (`docs/ai-ml-service-openapi.yaml`): `POST /ml/v1/embed`, `POST /ml/v1/rag` 엔드포인트 추가
  - **Vue 3 SPA**: `PolicyRagView.vue` (질문 입력·답변·출처·피드백) + `RagMetrics.vue` (어드민 대시보드) + i18n(ko/en)
  - **AbstractIntegrationTest 컨테이너**: `postgres:16-alpine` → `pgvector/pgvector:pg16` (pgvector 확장 공식 지원 이미지)

### Security

- `session_ref` 평문 미저장 — SHA-256 해시만 `ai_rag_query_log`에 보관
- `question_hash` 평문 미저장 — SHA-256 해시만 보관, LLM 입력에서 PII 제외
- ML 서비스 내부망 한정 접근 — Spring Boot → ML 호출은 사설 네트워크 전용
- 관리자 메트릭 API `@PreAuthorize("hasRole('ADMIN')")` + audit_log AOP 자동 감사 로그

---

## [1.2.0] - 2026-05-18

### Added

- AI 정책 매칭 — 하이브리드 추천·피드백 루프·품질 모니터링 (SPEC-CMS-AI-002) — 옵션 트랙 P1 완전 구현
  - **하이브리드 정책 추천** (`POST /api/v1/ai/policy-match`): SPEC-CMS-007 규칙 점수(0~100) + Python ML 시맨틱 점수(0~1) 가중 결합 (`hybrid = 0.4·ruleNorm + 0.6·semantic`), Top-K 랭킹·추천 설명 포함
  - **ML 장애 폴백**: Resilience4j ml-service CircuitBreaker OPEN 시 503 미반환, 규칙 단독 랭킹 + `degraded=true` 플래그로 서비스 연속성 보장
  - **추천 캐싱**: Caffeine policyMatchCache (TTL 기본 30분, 설정 가능) — 동일 세션·프로필·쿼리 재요청 시 ML 호출 없이 즉시 응답
  - **피드백 수집** (`POST /api/v1/ai/policy-match/feedback`): CLICKED·APPLIED·DISMISSED 이벤트 비동기 적재 — CTR·전환율 산출 기반
  - **추천 이벤트 비동기 로그**: `@Async("aiLogExecutor")` 기반, 응답 반환 후 비차단 적재
  - **DB 마이그레이션 V32**: `ai_policy_recommendation_log` — VIEWED/CLICKED/APPLIED/DISMISSED 혼재 단일 테이블, session_ref SHA-256 해시·company_profile JSONB PII 제외 화이트리스트
  - **관리자 모니터링** (`GET /api/v1/admin/ai/policy-match/metrics`, ROLE=ADMIN 전용): 일별 CTR·전환율·추천 커버리지 집계 차트
  - **OpenAPI 3.1 계약** (`docs/ai-ml-service-openapi.yaml`): `POST /ml/v1/policy-match` 엔드포인트 스키마 추가
  - **Vue 3 어드민 대시보드**: `PolicyMatchMetrics.vue` — CTR·전환율·커버리지 시계열 차트 + i18n(ko/en)
  - **시민 SPA 업데이트** (`PolicyMatchView.vue`): AI 하이브리드 점수 랭킹·추천 사유 표시, 클릭/신청/닫기 피드백 버튼

### Fixed

- `SimulationServiceImpl` 다중 생성자 `@Autowired` 누락 수정 — `@SpringBootTest` 컨텍스트 로딩 오류 해소 (AI-001 기존 버그)

### Security

- `session_ref` 평문 미저장 — SHA-256 해시만 `ai_policy_recommendation_log`에 보관
- ML 서비스 입력에서 PII(대표자명·주민·법인 식별정보) 완전 제외 — `ksic_code`·`employee_count`·`growth_stage`·`region_code`·`annual_revenue` 화이트리스트만 전송
- 관리자 모니터링 API `@PreAuthorize("hasRole('ADMIN')")` 적용

---

## [1.1.0] - 2026-05-18

### Added

- AI/ML 기능 도입 (SPEC-CMS-AI-001) — 옵션 트랙 P1 완전 구현
  - **성장단계 예측** (`GET /api/v1/ai/growth-stage`): Python ML 서비스 위임, Caffeine 캐시 TTL 1h, Resilience4j CircuitBreaker 적용
  - **가상 시뮬레이션** (`POST /api/v1/ai/simulation/start`): UUID 세션, PDF 보고서 생성(OpenPDF), 24시간 만료
  - **경영위험 예측** (`GET /api/v1/ai/risk-score`): GREEN/YELLOW/ORANGE/RED 4등급, 설명 API (`GET /risk-score/explain/{predictionId}`)
  - **알고리즘 품질 모니터링** 어드민 대시보드: 모델 메트릭·드리프트 경보·재학습 큐 관리 (10개 ADMIN 전용 엔드포인트)
  - DB 마이그레이션 V28–V31: `ai_prediction_log`, `ai_simulation_session`, `ai_model_metric`, `ai_retrain_queue`
  - 일일 배치(`AiModelMetricJob`) cron 02:15 — 정확도 < 0.70 또는 nRMSE > 0.20 시 드리프트 감지 + 재학습 큐 자동 등록
  - IP 평문 미저장 — SHA-256 해시만 보관(`IpHashUtil`), PII 전송 차단
  - OpenAPI 3.1 계약 문서 (`docs/ai-ml-service-openapi.yaml`)
  - Vue 3 어드민 대시보드: `ModelDashboard.vue`, `DriftAlerts.vue`, `RetrainQueue.vue` + i18n(ko/en)
  - `MlServiceClient` 인터페이스 + MockMlServiceClient (테스트 전용) — ML 부재 시 Spring Boot 독립 검증 가능

---

## [1.0.2] - 2026-05-18

### Added

- Admin SPA Playwright E2E 테스트 도입 (SPEC-CMS-ADMIN-E2E-001)
  - `@playwright/test` 1.48 + `@axe-core/playwright` 설치
  - Mock JWT `page.route()` 전략으로 Pinia 런타임 인증 시뮬레이션
  - 로그인/인증가드/대시보드/사용자/역할/공지/에러/KWCAG 2.2 AA (21개 시나리오, 20 pass)
  - CI `frontend-e2e-admin` job 추가 (backend 미의존)
- Public 시민 SPA E2E 테스트 도입 (SPEC-CMS-PUBLIC-E2E-001)
  - Chromium 기반 39개 E2E 시나리오: 홈/공지/FAQ/검색/정책매칭/에러페이지/KWCAG 2.2 AA
  - axe-core/playwright 활용 KWCAG 2.2 AA 접근성 자동 검증 (`a11y.spec.ts`)
  - CI 워크플로우에 `frontend-e2e` job 추가 (`needs: [frontend-test]`, playwright-report 아티팩트)
  - 백엔드 미기동 시 의존성 테스트 자동 스킵 처리 (`test.skip()` 패턴)

### Fixed

- `PiiAuditEnhanceIT` 비동기 race condition 해소: `AsyncConfig.auditExecutor()`에 `@ConditionalOnMissingBean` 추가 — 테스트 컨텍스트의 `SyncTaskExecutor`가 올바르게 우선 적용됨
- `SafetyTemplateServiceTest.releaseNewVersion_bumpsMinorAndArchivesPrevious` 수정: `safety_guideline_template.code` UNIQUE 제약 상 `INSERT`가 아닌 `UPDATE` 검증으로 교정
- `DashboardLayoutIT` JSON 이중 이스케이프 제거 — `MockMvc` ResultMatcher 경로에서 `\\."` → `\"` 정규화
- `GlobalExceptionHandler` 도메인 예외 누락 등록 수정 — `AccessDeniedException` / `EntityNotFoundException` 핸들러 추가

### Changed

- `RateLimitFilter` 보강: 키당 독립 슬라이딩 윈도우 + 신뢰 프록시 XFF 헤더 검증 (WARN-3 대응)
- `MimeTypeValidator` OOXML 구조 검증 + `text/html` XSS 차단 추가 (WARN-4 대응)
- `target="_blank"` 링크 전체 `rel="noopener noreferrer"` 강제 (SUG-2 reverse tabnapping 방어)
- `ensureOwnerOrAdmin` 3중 복제 코드 → `AuthorizationGuard` 공용 유틸로 통합 (SUG-1 리팩터링)
- DB 스키마 문서 완전 재생성 — V1~V26 마이그레이션 분석 반영 (`schema.md` / `erd.mmd` / `migrations.md`)

---

## [1.0.1] - 2026-05-15

### Security

- [CRITICAL] IDOR 취약점 수정: `@AuthenticationPrincipal` 타입 오류 → `JwtPrincipal` 교체 (8개 컨트롤러)
- [CRITICAL] Stored XSS 수정: `RICH_TEXT` 저장 전 Jsoup sanitize 적용 (`HtmlSanitizer`, 5개 서비스)
- [CRITICAL] JWT secret 하드코딩 기본값 제거: `changeme` 폴백 제거 + `@PostConstruct` 시작 시 유효성 검증
- [CRITICAL] Admin SPA DOMPurify 적용: `v-html` 7곳 sanitize (`useSafeHtml.ts` 컴포저블)
- [HIGH] MyBatis SQL Injection 제거: `${targetTable}` / `${targetColumn}` 취약 동적 쿼리 제거
- [HIGH] 보안 헤더 추가: CSP, HSTS, X-Frame-Options, Referrer-Policy (`SecurityConfig`)
- [HIGH] Rate Limiter 추가: IP 기반 로그인 / OTP 요청 제한 (`RateLimitFilter`)
- [HIGH] 첨부파일 MIME magic byte 검증 추가 (`MimeTypeValidator`)
- [HIGH] Admin open redirect 방지: `sanitizeRedirect()` 적용
- [HIGH] Public SPA 토큰 보안 강화: `tokenSecurity.ts` + iframe 감지
- [MEDIUM] 계정 열거 공격 방지: 단일 오류 메시지 + IP 기반 차단
- [MEDIUM] Refresh Token `SameSite=Strict` 적용
- [MEDIUM] JWT `audience` 클레임 추가 및 검증
- [MEDIUM] 운영 로그레벨 `DEBUG` → `INFO` 변경
- [LOW] `/actuator/info` 환경변수 노출 비활성화
- [LOW] `/actuator/backupStatus` ADMIN 전용 제한
- [LOW] actuator 메트릭 엔드포인트 인증 필요

---

## [1.0.0] - 2026-05-14

### Tested — SPA 전체 테스트 완료 (2026-05-14)

- **SPEC-CMS-ADMIN-TEST-001** Admin SPA 테스트 272/272 전체 통과 (v0.1.0 Draft → v0.2.0 Tested)
  - Vitest 셋업 복구, Element Plus jsdom 호환, 30+ View 단위 테스트 신규 작성
  - jsdom `el-form.validate()` 제약 해결: `$refs` 목 패턴 (`vi.spyOn(formInst, 'validate').mockRejectedValueOnce(false)`)
  - ForgotPasswordView OTP 셀렉터 수정 (`input[name="code"]`), `vi.useFakeTimers()` 범위 축소
  - RoleFormView `handleSubmit` 콜백 → Promise 패턴 전환
- **SPEC-CMS-PUBLIC-001** Public SPA 테스트 224/224 전체 통과 (v0.2.0 Implemented → v0.3.0 Tested)

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

[Unreleased]: https://github.com/EricSeokgon/iroum-cms/compare/v1.6.1...HEAD
[1.6.1]: https://github.com/EricSeokgon/iroum-cms/compare/v1.6.0...v1.6.1
[1.6.0]: https://github.com/EricSeokgon/iroum-cms/compare/v1.5.0...v1.6.0
[1.5.0]: https://github.com/EricSeokgon/iroum-cms/compare/v1.4.0...v1.5.0
[1.4.0]: https://github.com/EricSeokgon/iroum-cms/compare/v1.3.0...v1.4.0
[1.3.0]: https://github.com/EricSeokgon/iroum-cms/compare/v1.2.0...v1.3.0
[1.2.0]: https://github.com/EricSeokgon/iroum-cms/compare/v1.1.0...v1.2.0
[1.1.0]: https://github.com/EricSeokgon/iroum-cms/compare/v1.0.2...v1.1.0
[1.0.2]: https://github.com/EricSeokgon/iroum-cms/compare/v1.0.1...v1.0.2
[1.0.1]: https://github.com/EricSeokgon/iroum-cms/compare/v1.0.0...v1.0.1
[1.0.0]: https://github.com/EricSeokgon/iroum-cms/releases/tag/v1.0.0
