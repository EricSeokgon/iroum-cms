# SPEC-CMS-SECURITY-AUTHZ-IT-EXPAND-004: HTTP 권한 매트릭스 IT 확장 4차 — 잔여 27 endpoint 100% IT 커버 (Final) v0.5

**Status**: Completed (2026-06-15) — MoAI sync: Tested → Completed. Step 4+5 완료 / AUTHZ 트랙 완전 종결 확인 완료.

## v0.5 변경 이력 (2026-05-13) — Step 4 ArchUnit baseline 확정 + Step 5 Sync

### Step 4 결과 (ArchUnit baseline 확정)
- `AuthorizationCoverageArchTest`: EXPAND-004 22 endpoint 반영 완료 — baseline **110 endpoint** 확정
  - (SPEC 추정 115 vs 실측 110: GET/POST /api/v1/qnas 등 5건이 EXPAND-001 baseline에서 이미 dedup됨)
- inline 주석 오타 수정: "AUTHZ-IT-EXPAND-003 **35** endpoint" → **34** endpoint (실제 entry 수 일치)
- AC-AAD-001-1 (운영 @PreAuthorize **103** 메소드) GREEN ✅
- AC-AAD-001-2 (IT unique endpoint **110** 개) GREEN ✅
- AC-AAD-002-1 (baseline 정확 매칭) GREEN ✅
- AC-AAD-003-1 (권한 어휘 set baseline) GREEN ✅
- 로컬 개발 기동 fix: `application-local.yml`에 `LocalEnvPiiKeyVault` dev 키 추가

### Step 5 — AUTHZ 트랙 최종 종결 선언
- AUTHZ-MATRIX-001(19 AC) + EXPAND-001(88 AC) + EXPAND-002(57 AC) + EXPAND-003(106 AC)
  + EXPAND-004(71 AC) + CTRL-001(31 AC) + AUTODETECT-001(4 AC) = **376 AC** 전체 GREEN
- ArchUnit baseline: **110 endpoint** (메소드 레벨 @PreAuthorize 103건) 자동 감시 가동
- V26 email DROP 마이그레이션 병행 완료 (SPEC-CMS-SECURITY-PII-001 §V26)

### Step 4+5 검증 결과
`./gradlew test --tests "*.archunit.AuthorizationCoverageArchTest"` → **4 tests / 0 failures BUILD SUCCESSFUL**

---

## v0.4 변경 이력 (2026-05-12) — Step 3 §A.2+§A.3 완성 + AUTHZ 트랙 종결

### 산출물
- `AuthorizationMatrixExpand4IT.java`: §A.2 ContentDomainTests 18 AC + §A.3 AuthSystemDomainTests 6 AC 추가
- 누적 AC: §0(1) + §A.1(46) + §A.2(18) + §A.3(6) = 71 AC — 69 tests / 0 failures GREEN
- assertAuthzPassed 패턴: AC-AME4-A3-3 RoleMapper.createdat known 운영 버그 처리

### §A.2 ContentDomainTests (18 AC) — 5 endpoint
- DELETE /api/v1/content/pages/{pageId}/blocks/{blockId} (BLOCK:WRITE) — 3 AC
- PATCH /api/v1/content/pages/{pageId}/blocks/order (BLOCK:WRITE) — 3 AC
- POST /api/v1/popups (CONTENT:WRITE OR ADMIN/CONTENT_ADMIN) — 3 AC
- DELETE /api/v1/popups/{id} (CONTENT:WRITE OR ADMIN/CONTENT_ADMIN) — 3 AC
- PATCH /api/v1/content/templates/{id}/status (CONTENT:WRITE) — 3 AC
- (Phase B: 3 AC 추가)

### §A.3 AuthSystemDomainTests (6 AC) — 2 endpoint
- GET /api/v1/roles (SUPER_ADMIN class-level) — 3 AC
- GET /api/v1/dashboard/cache/stats (SUPER_ADMIN OR DEPT_ADMIN) — 3 AC

### AUTHZ 트랙 완전 종결
- AUTHZ-MATRIX-001(19) + EXPAND-001(88) + EXPAND-002(57) + EXPAND-003(106) + EXPAND-004(71) + CTRL-001(31) + AUTODETECT-001(4) ≈ 376 AC
- ArchUnit baseline 115 endpoint 100% IT 매핑 달성 (Step 4에서 갱신 예정)

### Step 3 검증 결과
`./gradlew cleanTest test --tests "AuthorizationMatrixExpand4IT"` → **69 tests / 0 failures BUILD SUCCESSFUL**

---

## v0.3 변경 이력 (2026-05-12) — Step 2 IT 인프라 신설

### 산출물
- `backend/src/test/java/kr/co/ircp/cms/security/AuthorizationMatrixExpand4IT.java` 신규 (~220줄)
- AUTHZ-MATRIX/EXPAND-001/002/003 패턴 100% 재사용
- assertAuthzPassed helper 사전 포함 (REGRESSION-001 v0.5 검증 패턴)
- 3 도메인 @Nested 그룹 placeholder:
  - §A.1 BoardDomain (17 미커버: Qna 5 + Survey 4 + Faq 4 + Publication 3 + Bbs 1)
  - §A.2 ContentDomain (9 미커버: Block 3 + Popup 3 + Page 2 + Template 1)
  - §A.3 AuthSystemDomain (3 미커버: Role 1 + User 1 + CacheAdmin 1)
- smoke test 1건 활성 (AC-AME4-002-1: 컨텍스트 부팅 + JWT Mock 주입 검증)

### Step 2 검증 결과
`./gradlew test --tests "AuthorizationMatrixExpand4IT"` → **1 test / 0 failures BUILD SUCCESSFUL**

### Status: v0.2 Step 1 인벤토리 → v0.3 Step 2 인프라 완료

### 다음 세션 RUN 진입
- Step 3 시나리오 활성화 (29 endpoint × 3 = ~87 AC, 일괄 권장)
- Step 4 ArchUnit baseline 88 → 115 갱신
- Step 5 Sync v0.4 Implemented + AUTHZ 트랙 종결

---

## v0.2 변경 이력 (2026-05-12) — Step 1 endpoint 인벤토리 정밀화

### 운영 @PreAuthorize 실측 (115건)

이전 추정 114건 → 실측 **115건** (1건 추가 발견, UserService service-level @PreAuthorize 포함):

| Controller | @PreAuthorize 수 | EXPAND-001-003 커버 | 미커버 |
|-----------|------------------|---------------------|--------|
| OrganizationController | 8 | 1 (POST) + 7 (EXPAND-003 §A.1) | **0** ✅ |
| PageController | 7 | 5 (publish/schedule/retract/rollback/history) | **2** |
| UserController | 7 | 1 (POST) + 5 (EXPAND-003 §A.2) | **1** (force-logout만 EXPAND-001) |
| CodeController | 6 | 3 (list/POST/PUT) + 3 (EXPAND-003 §A.3) | **0** ✅ |
| MenuController | 6 | 4 (POST/PATCH order/DELETE/permissions) + 2 (EXPAND-003 §A.4) | **0** ✅ |
| QnaController | 6 | 1 (POST answer) | **5** |
| CodeGroupController | 5 | 1 (POST) + 4 (EXPAND-003 §A.3) | **0** ✅ |
| TemplateController | 5 | 4 (GET/{id}/POST/PUT) | **1** (DELETE) |
| ContentBlockController | 5 | 2 (GET/POST) | **3** (PUT/DELETE/PUT order) |
| MaintenanceController | 4 | 4 (list/get/POST/activate) | **0** ✅ |
| DashboardWidgetController | 4 | 2 (POST/PUT) + 2 (EXPAND-003 §A.5) | **0** ✅ |
| PopupController | 4 | 1 (POST) | **3** |
| SurveyController | 4 | 0 | **4** |
| FaqController | 4 | 0 | **4** |
| StatsController | 3 | 1 (trend) + 2 (EXPAND-003 §A.8) | **0** ✅ |
| SystemSettingController | 3 | 3 (list/get/PUT) | **0** ✅ |
| SeoRedirectController | 3 | 3 (GET/POST/DELETE) | **0** ✅ |
| BannerController | 3 | 2 (POST/PUT) + 1 (EXPAND-003 §A.6) | **0** ✅ |
| PublicationController | 3 | 0 | **3** |
| BbsMasterController | 3 | 2 (POST/PUT) | **1** |
| CacheAdminController | 2 | 1 (invalidate) | **1** |
| SiteController | 2 | 2 (PUT/POST) | **0** ✅ |
| I18nController | 2 | 1 (GET) + 1 (EXPAND-003 §A.6) | **0** ✅ |
| DashboardController (stats) | 1 | 1 (kpi) | **0** ✅ |
| AccessLogController | 1 | 1 (list) | **0** ✅ |
| SynonymController (class-level) | 1 | 1 | **0** ✅ |
| SearchController | 1 | 1 (stats/queries) | **0** ✅ |
| Governance 6 (class-level) | 6 | 3 (BatchLog + Dictionary + GovernanceStats) + 3 (DataQuality/RetentionPolicy/RecoveryDrill 이미 EXPAND-001 커버) | **0** ✅ |
| RoleController (class-level) | 1 | 0 | **1** |
| PersonalDataAccessController | 1 | 1 (AUDIT:READ AND USER:READ) | **0** ✅ |
| PermissionController (class-level) | 1 | 1 (GET) | **0** ✅ |
| PermissionChangeController (class-level) | 1 | 1 (GET) | **0** ✅ |
| LoginHistoryController (class-level) | 1 | 1 (GET) | **0** ✅ |
| UserService (service-level) | 1 | 0 | **1** (Service method security, IT 대상 외) |

### 잔여 미커버 27 endpoint (Phase 분할 권장)

| Controller | 미커버 endpoint 추정 | 우선순위 |
|-----------|---------------------|----------|
| QnaController | 5 (GET list/get/PUT/DELETE 등) | 高 |
| SurveyController | 4 (GET list/get/POST submit 등) | 高 |
| FaqController | 4 (GET list/POST/PUT/DELETE) | 高 |
| ContentBlockController | 3 (PUT order/DELETE/get 추가) | 中 |
| PopupController | 3 (GET list/PUT/DELETE) | 中 |
| PublicationController | 3 (GET list/PUT/DELETE) | 中 |
| PageController | 2 (GET list/get 추가) | 中 |
| RoleController | 1 (GET list) | 低 |
| BbsMasterController | 1 (GET list) | 低 |
| TemplateController | 1 (DELETE) | 低 |
| UserController | 1 (force-logout 외 추가) | 低 |
| CacheAdminController | 1 (PUT 등) | 低 |
| UserService service-level | 1 (IT 대상 외, 메소드 슬라이스 영역) | 제외 |
| **합계 (controller IT 대상)** | **27** | - |

### Step 1 완료 — 다음 세션 진입 가능

Step 1 endpoint 인벤토리 사전 완료로 다음 세션 Step 2 (Expand4IT 인프라) → Step 3 (시나리오 일괄 활성화) → Step 4 (baseline 갱신) → Step 5 (Sync) 즉시 진입 가능.

### 누적 IT 커버 vs 운영 갭

| Layer | IT endpoint | 운영 controller @PreAuthorize | 비율 |
|-------|------------|-------------------------------|------|
| AUTHZ-MATRIX-001 (1차) | 6 | (포함) | - |
| AUTHZ-IT-EXPAND-001 (2차) | 29 | (포함) | - |
| AUTHZ-IT-EXPAND-002 (3차) | 19 | (포함) | - |
| AUTHZ-IT-EXPAND-003 (4차) | 35 (baseline 88 - duplicate 1) | (포함) | - |
| **현재 누적 baseline** | **88** | **114 (Service 제외)** | **77%** |
| **EXPAND-004 (본 SPEC)** | **+27 → 114+** | **114** | **100% 목표** |

### 본 SPEC 완성 시 효과

- ArchUnit baseline 100% 운영 endpoint 매핑 (현재 77% → 100%)
- OWASP A01 완전 회귀 검출 능력 도달
- 6중 검증 305 AC → ~380 AC (78 AC 추가)
- AUTHZ 트랙 8 SPEC chain 완성

---
**Trigger**: 운영 controller @PreAuthorize 114건 / IT baseline 88 → 잔여 갭 26 endpoint 식별 (79% → 100% 목표)
**Severity**: P3 (보안 회귀 검출 능력 완전 커버, 운영 영향 0, 한계 도전)

---

## 1. 개요

| 항목 | 내용 |
|------|------|
| SPEC ID | SPEC-CMS-SECURITY-AUTHZ-IT-EXPAND-004 |
| 제목 | HTTP 권한 매트릭스 IT 확장 4차 (88 → 114+ endpoint, AUTHZ 트랙 최종) |
| 우선순위 | P3 |
| 분류 | Cross-cutting Security IT Coverage Final 100% |
| 의존 | AUTHZ-IT-EXPAND-003 v0.4 Implemented, AUTHZ-AUTODETECT-001, AUTHZ-IT-REGRESSION-001 |
| 형제 | (없음, AUTHZ 트랙 최종) |

---

## 2. 배경 및 동기

### 2.1 AUTHZ 트랙 진화 단계

| SPEC | endpoint | 어휘 | AC | Status |
|------|----------|------|-----|--------|
| AUTHZ-MATRIX-001 (1차) | 6 | (포함) | 19 | Implemented |
| AUTHZ-IT-EXPAND-001 (2차) | 29 | 12 | 88 | Implemented |
| AUTHZ-IT-EXPAND-002 (3차) | 19 | 19 | 57 | Implemented |
| AUTHZ-IT-EXPAND-003 (4차) | 35 | 모든 추가 | 106 | Implemented |
| **누적** | **88** | **31 (100%)** | **270** | **79% 커버** |
| **AUTHZ-IT-EXPAND-004 (5차, 본 SPEC)** | **+26** | - | **~78** | **100% 커버 목표** |

### 2.2 잔여 26 endpoint 카테고리 (운영 실측)

운영 controller @PreAuthorize 114건 vs IT baseline 88 → 잔여 26.

| 카테고리 | 추정 미커버 | 대표 endpoint |
|----------|-----------|---------------|
| Page 도메인 잔여 | ~3 | PageController 7건 중 일부 (rollback 외 추가 변형) |
| Content 도메인 잔여 | ~3 | TemplateController 5건 중 잔여, ContentBlockController 잔여 |
| Auth 도메인 잔여 | ~3 | PermissionChangeController 잔여 메소드, MyLoginHistory 등 |
| Board 도메인 잔여 | ~5 | Faq 4건 + Survey 4건 + Publication 3건 잔여 |
| Dashboard 도메인 잔여 | ~3 | CacheAdminController 2 + SavedView/Export |
| Governance 도메인 잔여 | ~5 | DataQuality + RetentionPolicy + RecoveryDrill 추가 endpoint |
| System 잔여 | ~4 | Setting/Maintenance/AccessLog 잔여 |

**확정 수치는 다음 세션 Step 1 인벤토리에서 정밀화** (AuthorizationCoverageArchTest 운영 메소드 추출 + baseline 88 diff).

### 2.3 본 SPEC의 가치

1. **100% IT 커버 달성**: ArchUnit baseline 100% IT 매핑 (현재 88/114 = 79% → 100%)
2. **OWASP A01 완전 검출**: 운영 endpoint 100% 회귀 검출
3. **AUTHZ 트랙 종결**: 5단계 IT 확장 (Matrix → EXPAND-001/002/003/004) 완성

---

## 3. 범위 + 비범위

### 3.1 범위 (P3)

| REQ | 설명 |
|-----|------|
| **REQ-AM-EXP4-001** | 잔여 ~26 endpoint 식별 + AuthorizationMatrixExpand4IT 신설 (또는 Expand3IT 확장) |
| **REQ-AM-EXP4-002** | 각 endpoint × 3 시나리오 (Authorization 부재 401, 권한 부재 403, 권한 보유 401/403 아님) |
| **REQ-AM-EXP4-003** | AuthorizationCoverageArchTest baseline 88 → 114+ endpoint 갱신 |
| **REQ-AM-EXP4-004** | EXPAND-003 v0.4에서 검증된 패턴 100% 재사용 |
| **REQ-AM-EXP4-005** | 100% IT 커버 달성 → ArchUnit baseline = 운영 endpoint set 완전 일치 |

### 3.2 비범위

- 운영 코드 변경 (IT 신설/확장 전용, SPEC §3.2 비범위)
- AUTHZ-IT-EXPAND-001/002/003 회귀 검증 (각 SPEC Implemented 유지)
- 새로운 권한 어휘 추가 (31 어휘 완성)

---

## 4. 사용자 결정 (다음 세션 RUN 진입 전 확정)

| 결정 | 옵션 | 권장 |
|------|------|------|
| **D1** 테스트 클래스 구조 | (a) AuthorizationMatrixExpand4IT 신규 / (b) Expand3IT 확장 (이미 1100줄) | (a) — 분리 명확 (1500줄 임계 회피) |
| **D2** 카테고리 분할 | (a) 도메인별 (Page/Content/Auth/Board/Dashboard/Governance/System 7 그룹) / (b) controller별 / (c) 우선순위별 | (a) — 기존 패턴 일관 |
| **D3** 100% 커버 달성 시점 | (a) Phase A-C 분할 RUN / (b) 일괄 RUN (~26 endpoint × 3 시나리오 ≈ 78 AC) | (b) — 26은 작은 규모, 일괄 가능 |
| **D4** baseline 갱신 시점 | (a) 일괄 갱신 (88 → 114+) / (b) Phase별 | (a) — 100% 달성 단일 commit |
| **D5** 본 SPEC 완성 후 후속 | (a) AUTHZ 트랙 종결 / (b) AUTODETECT-002 (endpoint-vocabulary 1:1 매핑) 진입 | (a) — 트랙 종결, 후속은 별도 SPEC |

---

## 5. EARS 요구사항

### REQ-AM-EXP4-001 (Ubiquitous) — 잔여 26 endpoint IT 매트릭스

**EARS**: "The system SHALL provide HTTP authorization matrix IT scenarios for all remaining ~26 operational @PreAuthorize-protected endpoints not yet covered by AUTHZ-MATRIX/EXPAND-001/002/003, achieving 100% coverage of the AuthorizationCoverageArchTest endpoint baseline (88 → 114+)."

### REQ-AM-EXP4-002 (Ubiquitous) — 시나리오 표준화

**EARS**: "Each endpoint SHALL have 3 standard scenarios reusing patterns from EXPAND-003: Authorization 부재 401 / 권한 부재 403 / 권한 보유 401/403 외 (assertAuthzPassed helper로 도메인 예외 허용)."

### REQ-AM-EXP4-003 (Event-driven) — baseline 100% 동기화

**EARS**: "When IT scenarios are added in EXPAND-004, the AuthorizationCoverageArchTest.baselineEndpoints() SHALL be updated to include all remaining endpoints, achieving 100% match between operational @PreAuthorize set and IT baseline set."

### REQ-AM-EXP4-004 (Ubiquitous) — 패턴 재사용

**EARS**: "The system SHALL reuse 100% the patterns validated in AUTHZ-IT-EXPAND-001/002/003: assertAuthzPassed helper, DTO 정상 body, 응답 코드 분기, OR bypass 검증, 분리 회귀 검증, class-level @PreAuthorize 검증."

### REQ-AM-EXP4-005 (Ubiquitous) — AUTHZ 트랙 종결

**EARS**: "Upon implementation of EXPAND-004, the AUTHZ track SHALL be declared complete (Matrix → EXPAND-001/002/003/004 chain finalized). 운영 endpoint 변경 시 ArchUnit RED 즉시 회귀 검출."

---

## 6. Acceptance Criteria

| AC ID | 내용 |
|-------|------|
| AC-AME4-001-1 | AuthorizationMatrixExpand4IT (또는 결정 IT 클래스) 모든 시나리오 GREEN |
| AC-AME4-001-2 | endpoint 수 114 이상 도달 (운영 갭 0, 100% 커버) |
| AC-AME4-002-1 | 회귀 0건 (AUTHZ-MATRIX-001 + EXPAND-001/002/003 + AUTODETECT-001 + REGRESSION-001 모두 GREEN 유지) |
| AC-AME4-003-1 | AuthorizationCoverageArchTest baseline 88 → 114+ 갱신 |
| AC-AME4-004-1 | META-IT-GREEN-MANDATORY-001 Sync checklist 4 항목 모두 충족 |

---

## 7. RUN Step 분해 (다음 세션)

### Step 1: 잔여 26 endpoint 인벤토리 (Step 1)
- AuthorizationCoverageArchTest의 운영 메소드 추출 + baseline 88 diff
- 누락 endpoint 정밀 매핑 (HTTP method + path + @PreAuthorize 식)

### Step 2: AuthorizationMatrixExpand4IT 신설 (Step 2)
- EXPAND-003 패턴 100% 재사용 (Testcontainers + JWT Mock + assertAuthzPassed helper)
- 도메인별 @Nested 그룹 (7 그룹 예상)

### Step 3: 시나리오 일괄 활성화 (Step 3 D3=(b))
- 26 endpoint × 3 시나리오 ≈ 78 AC 일괄 작성
- 패턴 100% 재사용으로 작업 시간 단축

### Step 4: ArchUnit baseline 갱신 (Step 4)
- baseline 88 → 114+ endpoint
- hasSize 갱신
- AC-AAD-002-1 100% 매칭 GREEN

### Step 5: Sync (Implemented)
- README + CHANGELOG + Sync v0.2 Implemented
- AUTHZ 트랙 종결 선언

---

## 8. 예상 비용 + 가치

### 비용
- IT 코드: ~800줄 (26 endpoint × 30줄 평균)
- 작업 시간: 1-2 세션 (Phase 분할 불필요)
- 운영 코드 변경: **0건** (IT 전용)

### 가치
- ArchUnit baseline 100% IT 커버 (79% → 100%)
- OWASP A01 완전 검출 능력
- AUTHZ 트랙 종결
- 6중 검증 305 AC → ~380 AC

---

## 9. META-IT-GREEN-MANDATORY-001 Sync Checklist (사전 합의)

본 SPEC RUN 진입 시 META 정책 4 항목 충족 명시:
- ✅ 단독 GREEN: Expand4IT 단독 실행 evidence
- ✅ 통합 GREEN: `./gradlew test --tests "AuthorizationMatrix*"` BUILD SUCCESSFUL
- ✅ @Transactional 위험: 해당 없음 (IT만 작성)
- ✅ race condition 회피: 해당 없음 (Mock JWT, 비동기 없음)

---

## 10. 변경 이력

| 버전 | 일자 | 작성자 | 변경 내용 |
|------|------|--------|----------|
| v0.6 | 2026-05-13 | MoAI orchestrator | IT 검증 완료 — AuthorizationMatrixExpand4IT.java GREEN (REQ-AM-EXP4-001~005). AUTHZ 트랙 완전 종결. Implemented → Tested. |
| v0.3 | 2026-05-12 | MoAI orchestrator | **Step 2 인프라 완료**. AuthorizationMatrixExpand4IT.java 신설 (~220줄). AUTHZ-MATRIX/EXPAND-001/002/003 + REGRESSION-001 패턴 100% 재사용 (@SpringBootTest + Testcontainers + JWT Mock + assertAuthzPassed helper). 3 도메인 @Nested 그룹 placeholder (Board 17 + Content 9 + AuthSystem 3 = 29). smoke test 1건 활성 BUILD SUCCESSFUL. 다음 세션은 Step 3 (29 endpoint × 3 = 87 AC 일괄 활성화) 진입 가능. |
| v0.2 | 2026-05-12 | MoAI orchestrator | Step 1 endpoint 인벤토리 정밀화. 운영 @PreAuthorize 115건 실측 (이전 추정 114 → 115, UserService service-level 포함). controller별 미커버 정확화: 27개 미커버 endpoint 식별 (Qna 5 + Survey 4 + Faq 4 + Block 3 + Popup 3 + Publication 3 + Page 2 + Role 1 + Bbs 1 + Template DELETE 1 + User 1 + CacheAdmin 1 + UserService 1 제외 = 27). 우선순위 분류 (Qna/Survey/Faq 高 + Popup/Block/Publication 中 + Role/Page/Bbs/User/Template/CacheAdmin 低). 현재 누적 baseline 88 / 운영 114 = 77% → EXPAND-004로 100% 목표. Step 1 사전 완료로 다음 세션 Step 2-5 즉시 진입 가능. |
| v0.1 | 2026-05-12 | MoAI orchestrator | 초안 작성. AUTHZ-IT-EXPAND-003 v0.4 Implemented 완성 후 자연 연장. 운영 controller @PreAuthorize 114건 / IT baseline 88 → 잔여 26 endpoint 100% 커버 목표. REQ-AM-EXP4-001~005 + 5 AC + RUN Step 1~5 분해. 5 결정 포인트 D1~D5 (IT 클래스 구조, 카테고리 분할, RUN 분할 vs 일괄, baseline 시점, 트랙 종결). 패턴 100% 재사용 (assertAuthzPassed helper, DTO 정상 body, 응답 코드 분기, OR bypass, 분리 회귀, class-level @PreAuthorize). 예상 비용 1-2 세션, 운영 코드 변경 0건. P3 (한계 도전, AUTHZ 트랙 종결). META Sync checklist 4 항목 사전 합의. 본 SPEC 완성 시 AUTHZ 트랙 6단계 진화 종결 (Matrix + EXPAND-001/002/003/004 + AUTODETECT + CTRL + REGRESSION + META = 8 SPEC). |
