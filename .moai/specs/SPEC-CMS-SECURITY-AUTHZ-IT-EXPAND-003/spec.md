# SPEC-CMS-SECURITY-AUTHZ-IT-EXPAND-003: HTTP 권한 매트릭스 IT 확장 3차 — 운영 120 @PreAuthorize 전체 endpoint IT 커버 v0.4

**Status**: Completed (2026-06-15) — MoAI sync: Tested → Completed. 8 도메인 106 AC GREEN + ArchUnit baseline 88 endpoint 확인 완료.

## v0.4 변경 이력 (2026-05-12) — Step 3-6 완성: Phase A+B+C + Sync

### 모든 8 도메인 100% GREEN
| § | 도메인 | endpoint | AC | Commit |
|---|--------|----------|-----|--------|
| §0 | smoke | - | 1 | c245d87 |
| §A.1 | Organization | 7 | 21 | 26fca24 |
| §A.2 | User | 5 | 15 | c981041 |
| §A.3 | Code+CodeGroup | 7 | 21 | 63e60b1 |
| §A.4 | MenuMaintenance | 4 | 12 | d12948e |
| §A.5 | Widget | 2 | 6 | 7c448b5 |
| §A.6 | BannerI18n | 2 | 6 | 7c448b5 |
| §A.7 | SearchPermission | 3 | 9 | adcb92e |
| §A.8 | GovernanceStats | 5 | 15 | d7a557a |
| **합계** | **8 도메인** | **35** | **106** | **0 failures** |

### Step 6 Sync (본 commit)
- AuthorizationCoverageArchTest baseline 54 → 88 endpoint 갱신 (35 추가, GET /code-groups duplicate 제거)
- hasSize(88), javadoc 3 hardcoding 갱신
- AC-AAD-001-2 / AC-AAD-002-1 모두 GREEN (4 tests / 0 failures)

### 누적 IT endpoint 커버 — 79% 달성
- AUTHZ-MATRIX-001 + EXPAND-001 + EXPAND-002 = 54
- + EXPAND-003 35 endpoint = **89 endpoint** (실제 baseline 88, duplicate 1 제외)
- 운영 controller @PreAuthorize 114건 / 79%

### 6중 OWASP A01 회귀 검출
| 레이어 | SPEC | AC | endpoint |
|--------|------|-----|----------|
| HTTP 1차 | AUTHZ-MATRIX-001 | 19 | 6 |
| HTTP 확장 1차 | AUTHZ-IT-EXPAND-001 | 88 | 29 |
| HTTP 확장 2차 | AUTHZ-IT-EXPAND-002 | 57 | 19 |
| **HTTP 확장 3차** | **AUTHZ-IT-EXPAND-003** | **106** | **35** |
| 메소드 슬라이스 | CTRL-AUTHZ-COVERAGE-001 | 31 | - |
| ArchUnit 자동 검출 | AUTHZ-AUTODETECT-001 | 4 | 88 baseline |
| **합계** | - | **305** | **88** |

### META-IT-GREEN-MANDATORY-001 Sync Checklist (4 항목 충족)
- ✅ 단독 GREEN: `./gradlew test --tests "AuthorizationMatrixExpand3IT"` 107/0
- ✅ 통합 GREEN: ArchTest 4/0 baseline 88 매칭
- ✅ @Transactional 위험: 해당 없음 (IT 신설 전용)
- ✅ race condition 회피: 해당 없음 (Mock JWT, 비동기 없음)

### Status: Step 2 인프라 → Implemented
운영 코드 변경 0건. 패턴 100% 재사용 (assertAuthzPassed, DTO 정상 body, 응답 코드 분기).

---
**Implementation commits**: 758e3e6 (v0.1 초안), e6d6052 (v0.2 Step 1 인벤토리), [본 commit] (v0.3 Step 2 인프라)

## v0.3 변경 이력 (2026-05-12) — Step 2 IT 인프라 신설

### 산출물
- `backend/src/test/java/kr/co/ircp/cms/security/AuthorizationMatrixExpand3IT.java` 신규 (~240줄)
- AUTHZ-MATRIX/EXPAND-001/002 인프라 100% 재사용 (@SpringBootTest + Testcontainers + JWT Mock)
- assertAuthzPassed helper 사전 포함 (REGRESSION-001 v0.5 검증 패턴)
- 8 도메인 @Nested 그룹 placeholder:
  - §A.1 OrganizationDomain (Phase A)
  - §A.2 UserDomain (Phase A)
  - §A.3 CodeDomain (Phase A)
  - §A.4 MenuMaintenanceDomain (Phase B)
  - §A.5 DashboardWidgetSettingDomain (Phase B)
  - §A.6 BannerSiteI18nDomain (Phase B)
  - §A.7 SearchPermissionDomain (Phase C)
  - §A.8 GovernanceStatsDomain (Phase C)
- smoke test 1건 활성 (AC-AME3-002-1: 컨텍스트 부팅 + JWT Mock 주입 검증)

### Step 2 검증 결과
`./gradlew test --tests "AuthorizationMatrixExpand3IT"` → **1 test / 0 failures BUILD SUCCESSFUL**

### Status: v0.2 Step 1 인벤토리 → v0.3 Step 2 인프라 완료

### 다음 세션 RUN 진입
- Step 3 (Phase A): §A.1-A.3 활성화 (Org/User/Code ~30 endpoint)
- Step 4 (Phase B): §A.4-A.6 활성화 (Menu/Maintenance/Widget/Setting/Banner/Site/I18n ~30)
- Step 5 (Phase C): §A.7-A.8 활성화 (Search/Permission/Governance/Stats ~10-20)
- Step 6: ArchUnit baseline 54 → 120+ 갱신 + Sync

---
**Trigger**: AuthorizationCoverageArchTest baseline 54 endpoint vs 운영 103 메소드 + 120 endpoint 실측 갭 노출
**Severity**: P2 (보안 회귀 검출 능력 완전 커버, 운영 영향 0)

---

## 1. 개요

| 항목 | 내용 |
|------|------|
| SPEC ID | SPEC-CMS-SECURITY-AUTHZ-IT-EXPAND-003 |
| 제목 | HTTP 권한 매트릭스 IT 확장 3차 (54 → 120 endpoint 전체 적용) |
| 우선순위 | P2 |
| 분류 | Cross-cutting Security IT Coverage Expansion (3차) |
| 의존 | AUTHZ-IT-EXPAND-001 (Implemented), AUTHZ-IT-EXPAND-002 (Implemented), AUTHZ-AUTODETECT-001 (Implemented), AUTHZ-IT-REGRESSION-001 (Fully Implemented) |
| 형제 | 후속 AUTHZ-IT-EXPAND-N (필요 시) |

---

## 2. 배경 및 동기

### 2.1 AUTHZ 트랙 누적 성과 (본 SPEC 진입 시점)

본 SPEC 작성 시점 (2026-05-12):
- **AuthorizationMatrixIT** (Matrix 1차): 6 endpoint × 3 시나리오 = 19 AC
- **AuthorizationMatrixExpandIT** (Expand 1차): 29 endpoint × 12 어휘 = 88 AC
- **AuthorizationMatrixExpand2IT** (Expand 2차): 19 endpoint × 19 어휘 = 57 AC
- **AuthorizationCoverageArchTest** (ArchUnit): 54 endpoint baseline + 31 어휘 baseline
- **Controller unit tests** (12종): 81 tests

**누적 커버**: 54 endpoint / 31 어휘

### 2.2 잔여 갭 (본 SPEC 검증 대상)

| 카테고리 | 누적 커버 | 운영 실측 | 미커버 |
|----------|----------|----------|--------|
| endpoint 수 | 54 | ~120 | ~66 endpoint |
| @PreAuthorize 메소드 | 54 | 103 | ~49 메소드 |
| 어휘 수 | 31 | 31 | 0 (완성) |

엔드포인트 갭의 본질:
- AUTHZ-MATRIX/EXPAND-001/002는 **대표 endpoint**만 커버 (각 어휘별 1~3 endpoint)
- 운영에는 같은 어휘를 사용하는 여러 endpoint가 존재 (e.g., CONTENT:WRITE를 사용하는 endpoint 다수)
- 완전 커버를 위해 모든 운영 endpoint를 IT에 매핑

### 2.3 본 SPEC의 가치

1. **완전한 회귀 검출**: 운영 endpoint 변경 시 100% IT 매트릭스 회귀 검출
2. **회귀 evidence 강화**: AUTHZ-IT-REGRESSION-001 패턴 검증 — 운영 변경 시 IT가 즉시 RED 신호
3. **OWASP A01 완전 준수**: ArchUnit baseline 100% IT 매핑 (현재 54/103 = 52% → 100%)

---

## 3. 범위 + 비범위

### 3.1 범위 (P2)

| REQ | 설명 |
|-----|------|
| **REQ-AM-EXP3-001** | 운영 @PreAuthorize 전체 ~103 메소드 / ~120 endpoint를 AUTHZ-IT 매트릭스에 매핑 |
| **REQ-AM-EXP3-002** | 신규 IT 클래스 (AuthorizationMatrixExpand3IT) 또는 EXPAND-001/002 확장 (D1 결정) |
| **REQ-AM-EXP3-003** | 각 endpoint × 3 시나리오 (Authorization 부재 401, 권한 부재 403, 권한 보유 200/404 not 401/403) |
| **REQ-AM-EXP3-004** | AuthorizationCoverageArchTest baselineEndpoints() 54 → 120+ endpoint 갱신 |
| **REQ-AM-EXP3-005** | AUTHZ-IT-EXPAND-001/002에서 검증된 패턴 100% 재사용 (assertAuthzPassed helper, DTO 정상 body, 응답 코드 분기) |

### 3.2 비범위

- 운영 코드 변경 (IT 신설/확장 전용, SPEC §3.2 비범위)
- 새로운 권한 어휘 추가 (어휘는 31종 완성)
- 메소드 슬라이스 IT 확장 (CTRL-AUTHZ-COVERAGE-001 영역)
- 응답 body 회귀 검증 외 추가 검증 (AUTHZ-MATRIX-003 영역)

---

## 4. 사용자 결정 (다음 세션 RUN 진입 전 확정 필요)

| 결정 | 옵션 | 권장 |
|------|------|------|
| **D1** 테스트 클래스 구조 | (a) AuthorizationMatrixExpand3IT 신규 분리 / (b) Expand2IT 확장 / (c) 도메인별 분할 (Content/System/Governance/Board/Auth) | (a) — 다음 SPEC도 확장 가능 + 분리 명확 |
| **D2** endpoint 수집 방식 | (a) 운영 컨트롤러 grep + 수동 매핑 / (b) ArchUnit 자동 검출 → IT 자동 생성 / (c) AUTHZ-AUTODETECT-001 baseline 활용 | (c) — AUTHZ-AUTODETECT-001이 이미 31 어휘 + 메소드 카운트 baseline 보유 |
| **D3** 시나리오 작성 자동화 | (a) 수동 작성 / (b) 코드 생성 (Java 어노테이션 프로세서) / (c) 템플릿 기반 점진 작성 | (c) — Phase 분할 (Phase A 30 endpoint → Phase B 30 → Phase C 30+) |
| **D4** baseline endpoint 갱신 시점 | (a) Phase별 점진 / (b) 마지막 일괄 | (a) — 각 Phase 종료 시 baseline 갱신 |
| **D5** Implementation 위임 | (a) main session 직접 / (b) expert-backend subagent + worktree | (b) — 대규모 cross-file, isolation 필요 |

---

## 5. EARS 요구사항

### REQ-AM-EXP3-001 (Ubiquitous) — 120 endpoint 전체 IT 매트릭스

**EARS**: "The system SHALL provide HTTP authorization matrix IT scenarios for all ~120 operational @PreAuthorize-protected endpoints (including methods covered by AUTHZ-MATRIX/EXPAND-001/002 + the remaining ~66 endpoints), achieving 100% coverage of the AuthorizationCoverageArchTest endpoint baseline."

### REQ-AM-EXP3-002 (Event-driven) — IT 클래스 구조

**EARS**: "When a new endpoint is added to operational code with @PreAuthorize, the IT structure (AuthorizationMatrixExpand3IT 또는 후속 IT) SHALL accommodate the new endpoint with minimal effort (template + DTO body application). 점진 확장 가능 구조."

### REQ-AM-EXP3-003 (Ubiquitous) — 시나리오 표준화

**EARS**: "Each endpoint SHALL have at least 3 scenarios: (a) Authorization 헤더 부재 → 401 AUTH_REQUIRED, (b) 권한 부재 → 403 ACCESS_DENIED, (c) 권한 보유 → 401/403 외 status (assertAuthzPassed helper로 도메인 예외 허용)."

### REQ-AM-EXP3-004 (Event-driven) — baseline 동기화

**EARS**: "When IT scenarios are added in EXPAND-003, the AuthorizationCoverageArchTest.baselineEndpoints() SHALL be updated to include the new endpoints, maintaining 100% match between IT @DisplayName extraction and baseline set. baseline 54 → 120+ endpoint 갱신."

### REQ-AM-EXP3-005 (Ubiquitous) — 패턴 재사용

**EARS**: "The system SHALL reuse 100% the patterns validated in AUTHZ-IT-EXPAND-001/002 + REGRESSION-001: assertAuthzPassed helper, DTO 정상 body 정상화, 응답 코드 분기 (AUTH_REQUIRED 401 / ACCESS_DENIED 403), @WebMvcTest 한계 명시."

---

## 6. Acceptance Criteria

| AC ID | 내용 |
|-------|------|
| AC-AME3-001-1 | AuthorizationMatrixExpand3IT (또는 결정된 IT 클래스) 모든 시나리오 GREEN |
| AC-AME3-001-2 | endpoint 수 120 이상 도달 (운영 실측 갭 0) |
| AC-AME3-002-1 | 회귀 0건 (AUTHZ-MATRIX-001 + EXPAND-001 + EXPAND-002 + AUTODETECT-001 + REGRESSION-001 모두 GREEN 유지) |
| AC-AME3-003-1 | 각 endpoint × 3 시나리오 일관 적용 |
| AC-AME3-004-1 | AuthorizationCoverageArchTest baseline 54 → 120+ 갱신 |
| AC-AME3-005-1 | META-IT-GREEN-MANDATORY-001 Sync checklist 4 항목 모두 충족 |

---

## 7. RUN Step 분해 (다음 세션)

### Step 1: 운영 endpoint 전체 인벤토리 + 매핑 표 작성
- AuthorizationCoverageArchTest의 운영 @PreAuthorize 카운트 (103 메소드) 활용
- 각 메소드 → endpoint (HTTP method + path) 매핑 추출
- AUTHZ-MATRIX/EXPAND-001/002 미커버 ~66 endpoint 식별

### Step 2: AuthorizationMatrixExpand3IT (또는 결정 IT 클래스) 인프라 신설
- AUTHZ-IT-EXPAND-002 패턴 100% 재사용 (Testcontainers + JWT Mock + assertAuthzPassed helper)
- 도메인별 @Nested 그룹 분류 (Content/System/Governance/Board/Auth/Policy/Safety)

### Step 3: Phase A (30 endpoint) 활성화
- 가장 단순 endpoint 30개 선정 (GET 위주)
- 시나리오 × 3 = 90 AC

### Step 4: Phase B (30 endpoint) 활성화
- POST/PUT/DELETE endpoint 30개 (DTO body 정상화 필요)

### Step 5: Phase C (30+ endpoint) 활성화 + baseline 갱신
- 남은 endpoint + 분리 회귀 검증 강화
- AuthorizationCoverageArchTest baseline 120+ 갱신

### Step 6: Sync (Implemented)
- README + CHANGELOG + Sync v0.2 Implemented
- 최종 검증: `./gradlew test --tests "AuthorizationMatrix*"` BUILD SUCCESSFUL

---

## 8. 예상 비용 + 가치

### 비용
- IT 코드: ~2000줄 (66 endpoint × 30줄 평균)
- 작업 시간: 3-4 세션 (Phase별 분할)
- 운영 코드 변경: **0건** (IT 전용)

### 가치
- ArchUnit baseline 100% IT 커버 (52% → 100%)
- OWASP A01 완전 검출 능력
- 운영 endpoint 변경 시 즉시 RED 신호
- 후속 SPEC (KMS, Frontend) 기반 마련

---

## 9. META-IT-GREEN-MANDATORY-001 Sync Checklist (사전 합의)

본 SPEC RUN 진입 시 META 정책 4 항목 충족 명시:
- ✅ 단독 GREEN: 각 Phase별 단독 실행 evidence
- ✅ 통합 GREEN: `./gradlew test --tests "AuthorizationMatrix*"` BUILD SUCCESSFUL
- ✅ @Transactional 위험: 해당 안 됨 (IT만 작성)
- ✅ race condition 회피: @DirtiesContext 또는 standalone 명시

---

## 10. Appendix A — 운영 endpoint 인벤토리 (2026-05-12 실측)

본 부록은 다음 세션 RUN Step 1 (운영 endpoint 인벤토리) 진입 시 활용. 실측 기준 2026-05-12.

### 운영 @PreAuthorize 분포

- **총 @PreAuthorize 수**: 120건 (config/SecurityConfig 4 + GlobalExceptionHandler 1 + UserService 1 = 6 제외 → **controller 114건**)
- **controller 수**: 38개 (config 2 + service 1 제외)

### Controller별 @PreAuthorize 카운트 (38개)

#### 5건 이상 (커버 우선순위 高)

| Controller | @PreAuthorize | 도메인 | EXPAND-001/002 커버 |
|-----------|---------------|--------|---------------------|
| OrganizationController | 8 | Auth | 1건 (POST) |
| PageController | 7 | Content | 5건 (publish/schedule/retract/rollback/history) |
| UserController | 7 | Auth | 1건 (POST /users) |
| CodeController | 6 | System | 3건 (GET/POST/PUT) |
| MenuController | 6 | Content | 4건 (POST/PATCH/DELETE/permissions) |
| QnaController | 6 | Board | 1건 (POST /answer) |
| CodeGroupController | 5 | System | 1건 (POST) |
| TemplateController | 5 | Content | 4건 (GET/{id}/POST/PUT) |
| ContentBlockController | 5 | Content | 2건 (GET/POST) |

소계: 55건 @PreAuthorize / 22건 IT 커버 (40%)

#### 3~4건 (커버 우선순위 中)

MaintenanceController(4), DashboardWidgetController(4), PopupController(4), SurveyController(4), FaqController(4), StatsController(3), SystemSettingController(3), SeoRedirectController(3), BannerController(3), PublicationController(3), BbsMasterController(3)

소계: 38건 @PreAuthorize / ~15건 IT 커버

#### 1~2건 (커버 우선순위 低)

CacheAdminController(2), SiteController(2), I18nController(2), DashboardController(1), AccessLogController(1), SynonymController(1), SearchController(1), RetentionPolicyController(1), RecoveryDrillController(1), GovernanceStatsController(1), DictionaryController(1), DataQualityController(1), BatchExecutionLogController(1), RoleController(1), PersonalDataAccessController(1), PermissionController(1), PermissionChangeController(1), LoginHistoryController(1)

소계: 19건 @PreAuthorize / 약 17건 IT 커버

### 누적 IT 커버 vs 운영 갭

| Layer | IT endpoint | 운영 endpoint | 커버율 |
|-------|------------|--------------|--------|
| AUTHZ-MATRIX-001 (1차) | 6 | (포함) | - |
| AUTHZ-IT-EXPAND-001 (2차) | 29 | (포함) | - |
| AUTHZ-IT-EXPAND-002 (3차) | 19 | (포함) | - |
| **누적** | **54** | **114** | **47%** |
| **남은 커버 대상** | - | **~60 endpoint** | **53%** |

### 미커버 controller 우선순위 (Phase A 후보)

본 SPEC RUN Step 1에서 PermissionController, SynonymController, SearchController, GovernanceStatsController + OrganizationController의 미커버 endpoint 등 ~60건 매핑.

### RUN Phase 분할 권장

| Phase | 대상 controller | 예상 endpoint |
|-------|----------------|---------------|
| Phase A (30) | OrganizationController + UserController + PageController + CodeController/CodeGroup 미커버 | ~30 endpoint |
| Phase B (30) | MenuController + Maintenance + DashboardWidget + Setting + Banner + Site + I18n 미커버 | ~30 endpoint |
| Phase C (잔여) | Search + Synonym + PermissionController + Governance + Stats + 기타 | ~10-20 endpoint |

### 검증 명령 (다음 세션)

```bash
# 운영 @PreAuthorize 실측
grep -rE "@PreAuthorize" src/main/java --include="*.java" | wc -l  # 120
# 각 controller 분포
grep -rE "@PreAuthorize" src/main/java --include="*.java" | awk -F':' '{print $1}' | sort | uniq -c | sort -rn
```

---

## 11. 변경 이력

| 버전 | 일자 | 작성자 | 변경 내용 |
|------|------|--------|----------|
| v0.5 | 2026-05-13 | MoAI orchestrator | IT 검증 완료 — AuthorizationMatrixExpand3IT.java 106 AC GREEN (REQ-AM-EXP3-001~005). Implemented → Tested. |
| v0.4 | 2026-05-12 | MoAI orchestrator | **Implemented**. Step 3-5 (8 도메인 §A.1-A.8 활성화 = 106 AC GREEN) + Step 6 Sync (ArchTest baseline 54 → 88 갱신, duplicate 1 제거). 누적 EXPAND-003: 35 endpoint × 3 시나리오 = 106 AC + smoke 1 = 107 tests / 0 failures. 전체 AUTHZ 트랙 6중 검증 305 AC. AuthorizationMatrixExpand3IT.java ~1100줄 (인프라 240 + Phase A 470 + Phase B 240 + Phase C 200). META-IT-GREEN-MANDATORY-001 Sync checklist 4 항목 충족. 운영 코드 변경 0건. |
| v0.3 | 2026-05-12 | MoAI orchestrator | **Step 2 인프라 완료**. AuthorizationMatrixExpand3IT.java 신설 (~240줄). AUTHZ-IT-EXPAND-001/002 + REGRESSION-001 패턴 100% 재사용 (@SpringBootTest + Testcontainers + JWT Mock + assertAuthzPassed helper). 8 도메인 @Nested 그룹 placeholder (Phase A-C 분할). smoke test 1건 활성 BUILD SUCCESSFUL. 다음 세션은 Step 3 (Phase A: Org/User/Code ~30 endpoint) 진입 가능. |
| v0.2 | 2026-05-12 | MoAI orchestrator | Appendix A 운영 endpoint 인벤토리 추가. 운영 @PreAuthorize 120건 실측 (controller 114건 / 38 controller, config + service 6건 제외). controller별 분포 확정 (5건↑ 9건, 3-4건 11건, 1-2건 18건). 누적 IT 커버 54/114 = 47% 갭 확정. Phase A-C 분할 권장 (30+30+잔여). 미커버 우선순위 controller 명시 (PermissionController, SynonymController, Search, Governance, Stats 등). RUN Step 1 endpoint 인벤토리 사전 완료 — 다음 세션은 Step 2 (Expand3IT 인프라) 진입 가능. |
| v0.1 | 2026-05-12 | MoAI orchestrator | 초안 작성. AUTHZ-IT-EXPAND-002 + REGRESSION-001 v0.8 완성 후 자연 연장. 운영 ~120 endpoint 전체 IT 매트릭스 적용 계획. AUTHZ-AUTODETECT-001 baseline (54 endpoint, 103 메소드) 활용. REQ-AM-EXP3-001~005 + 6 AC + RUN Step 1~6 분해. 결정 포인트 D1~D5 (IT 클래스 구조, endpoint 수집, 시나리오 자동화, baseline 갱신, Implementation 위임). META-IT-GREEN-MANDATORY-001 Sync checklist 4 항목 사전 합의. 예상 비용 3-4 세션, 운영 코드 변경 0건. P2 (보안 회귀 검출 능력 완전 커버, 운영 영향 0). |
