# SPEC-CMS-SECURITY-AUTHZ-IT-EXPAND-003: HTTP 권한 매트릭스 IT 확장 3차 — 운영 120 @PreAuthorize 전체 endpoint IT 커버 v0.1

**Status**: Planned (2026-05-12) — AUTHZ-IT-EXPAND-002 + REGRESSION-001 v0.8 완성 후 자연 연장
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

## 10. 변경 이력

| 버전 | 일자 | 작성자 | 변경 내용 |
|------|------|--------|----------|
| v0.1 | 2026-05-12 | MoAI orchestrator | 초안 작성. AUTHZ-IT-EXPAND-002 + REGRESSION-001 v0.8 완성 후 자연 연장. 운영 ~120 endpoint 전체 IT 매트릭스 적용 계획. AUTHZ-AUTODETECT-001 baseline (54 endpoint, 103 메소드) 활용. REQ-AM-EXP3-001~005 + 6 AC + RUN Step 1~6 분해. 결정 포인트 D1~D5 (IT 클래스 구조, endpoint 수집, 시나리오 자동화, baseline 갱신, Implementation 위임). META-IT-GREEN-MANDATORY-001 Sync checklist 4 항목 사전 합의. 예상 비용 3-4 세션, 운영 코드 변경 0건. P2 (보안 회귀 검출 능력 완전 커버, 운영 영향 0). |
