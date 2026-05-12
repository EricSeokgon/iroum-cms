# SPEC-CMS-SECURITY-AUTHZ-IT-REGRESSION-001: AUTHZ IT 51 RED 회귀 진단 + 운영 응답 코드 동기 v0.1

**Status**: Planned (2026-05-12) — 진단 분리 SPEC, 회귀 발생 시점 + 원인 정밀화
**Trigger**: PII-FOLLOWUP-005 v0.3 통합 실행 (commit 608855b) 시 발견한 51 unit test/IT failed
**Severity**: P2 (보안 IT 회귀 — 운영 영향 0이나 SPEC 상태와 실제 GREEN 상태 불일치)

---

## 1. 개요

| 항목 | 내용 |
|------|------|
| SPEC ID | SPEC-CMS-SECURITY-AUTHZ-IT-REGRESSION-001 |
| 제목 | AUTHZ IT 51 RED 회귀 진단 + 운영 응답 코드 동기 |
| 우선순위 | P2 |
| 분류 | Cross-cutting Security Regression Diagnosis |
| 의존 | AUTHZ-MATRIX-001, AUTHZ-IT-EXPAND-001 (현재 RED, 본 SPEC으로 회복) |
| META | META-IT-GREEN-MANDATORY-001 첫 위반 사례 진단 |

---

## 2. 배경 — 회귀 발견 경위

### 2.1 발견 시점
- **2026-05-12 PII-FOLLOWUP-005 v0.3 통합 실행** (commit `608855b`):
  - `./gradlew :backend:integrationTest --tests "PiiAuditEnhanceIT"` 실행 시 PII 5/5 GREEN
  - 그러나 `./gradlew :backend:test` 전체 실행 시 1642 tests / 51 failed
- 본 세션 변경 영향 0건 확정:
  - `AuthorizationMatrixExpandIT` 단독 실행 87 tests / 31 failed
  - 즉, 본 세션 @DirtiesContext 추가와 무관한 기존 회귀

### 2.2 SPEC ↔ 실제 상태 불일치
| SPEC | 등록 Status | 실측 상태 |
|------|------------|----------|
| AUTHZ-MATRIX-001 | Implemented v0.1 | Partial RED |
| AUTHZ-IT-EXPAND-001 | Implemented v0.2 | 87 tests / 31 failed (~64% GREEN) |
| AUTHZ-IT-EXPAND-002 | Implemented v0.3 | 58 tests / 0 failed (100% GREEN, 본 세션 작성) |

AUTHZ-IT-EXPAND-002만 100% GREEN — 다른 SPEC은 commit 시점 이후 운영 코드 변경으로 회귀.

---

## 3. 회귀 패턴 분석 (실측 evidence)

### 3.1 패턴 1 — `expected 403 but 400` (@Valid validation 우선)

**위치**: AuthorizationMatrixExpandIT의 ContentDomain/SystemDomain/BlockDomain/AuthDomain 등 + controller unit tests

**원인**:
- IT 시나리오: 권한 부재 (USER role만 보유) → 403 기대
- 실제: @PreAuthorize 체크 전에 @RequestBody/@RequestParam validation이 먼저 실행 → 400
- 본 세션 AUTHZ-IT-EXPAND-002 Phase A에서도 동일 패턴 5건 발견 → 정상 body/param 제공으로 해결 완료

**해결 방향** (REQ-IRR-001):
- AuthorizationMatrixExpandIT 31 failed 시나리오에 정상 body/param 추가
- 패턴: `@Valid @RequestBody DTO` endpoint은 minimum required fields를 만족하는 JSON 제공

### 3.2 패턴 2 — `AUTH_FORBIDDEN vs ACCESS_DENIED` (응답 코드 변경)

**위치**: AuthorizationMatrixIT의 `JSON path "$.code" expected:<AUTH_FORBIDDEN> but was:<ACCESS_DENIED>`

**원인**:
- 운영 SecurityConfig line 122: anonymous user 차단 → `AUTH_FORBIDDEN`
- 운영 GlobalExceptionHandler line 837: authenticated user @PreAuthorize 차단 → `ACCESS_DENIED`
- 이전 IT는 모든 권한 거부 → `AUTH_FORBIDDEN` 가정
- GlobalExceptionHandler에 `AuthorizationDeniedException` 핸들러 추가 시점부터 인증된 사용자의 권한 부족은 `ACCESS_DENIED` 응답

**해결 방향** (REQ-IRR-002):
- IT 시나리오 분기: 인증 부재 (Authorization 헤더 없음) → 401 + (응답 코드 검증 시) AUTH_REQUIRED
- 인증 + 권한 부재 → 403 + (응답 코드 검증 시) ACCESS_DENIED
- AuthorizationMatrixIT JSON path 검증을 운영 동작에 맞춰 정정

### 3.3 패턴 3 — controller unit test 401 → 403 (Security 구성 차이)

**위치**: PermissionChangeControllerTest, UserControllerTest, RoleControllerTest 등 11 controller test

**원인** (추정):
- @WebMvcTest 또는 standalone MockMvc 사용 시 Security 구성 차이
- 단위 테스트 환경의 SecurityFilterChain이 운영과 다르게 동작

**해결 방향** (REQ-IRR-003):
- 각 controller test의 Security 구성 정확화 + 운영 동작 일치
- WebMvcTestInfraConfig 검토

---

## 4. 범위 + 비범위

### 4.1 범위 (P2)

| REQ | 설명 |
|-----|------|
| **REQ-IRR-001** | AuthorizationMatrixExpandIT 31 failed 중 패턴 1 (403→400) 해결 — 정상 body/param 적용 |
| **REQ-IRR-002** | AuthorizationMatrixIT의 AUTH_FORBIDDEN/ACCESS_DENIED 응답 코드 IT 동기 |
| **REQ-IRR-003** | controller unit test 11종의 401/403 차이 진단 + Security 구성 정정 |
| **REQ-IRR-004** | 회귀 시점 git log 정밀 추적 (GlobalExceptionHandler AuthorizationDeniedException 핸들러 추가 commit) |
| **REQ-IRR-005** | AUTHZ-MATRIX-001 + AUTHZ-IT-EXPAND-001 SPEC Status 정정 (Implemented → Mostly Implemented 또는 Partial Regression) |

### 4.2 비범위

- 운영 코드 변경 (응답 코드 정책 결정은 별도 SPEC)
- AUTHZ-IT-EXPAND-002 (본 세션 100% GREEN, 회귀 없음)
- 새로운 IT 시나리오 추가 (AUTHZ-IT-EXPAND-003 별도)

---

## 5. EARS 요구사항

### REQ-IRR-001 (Ubiquitous) — 패턴 1 해결

**EARS**: "The system SHALL update all 31 failed AuthorizationMatrixExpandIT scenarios that fail with `Status expected:<403> but was:<400>` to provide valid `@RequestBody`/`@RequestParam` values that satisfy DTO validation, allowing the test to reach the `@PreAuthorize` authorization check."

검증:
- 31 failed → 0 failed (단독 실행 87 tests / 0 failed BUILD SUCCESSFUL)
- 회귀 0건: AUTHZ-MATRIX-001 + AUTHZ-IT-EXPAND-002 영향 없음

### REQ-IRR-002 (Event-driven) — 응답 코드 동기

**EARS**: "When the operational `GlobalExceptionHandler.handleAuthorizationDenied` returns `ACCESS_DENIED` for authenticated-but-unauthorized users, the IT scenarios SHALL verify `ACCESS_DENIED` for authenticated 403 paths and `AUTH_FORBIDDEN` only for anonymous/unauthenticated 403 paths."

검증:
- AuthorizationMatrixIT JSON path 검증 정정
- AUTH_FORBIDDEN vs ACCESS_DENIED 분기 명확화

### REQ-IRR-003 (Event-driven) — controller unit test Security

**EARS**: "When a controller unit test (`@WebMvcTest` or standalone MockMvc) verifies authentication/authorization status codes, the test's Security configuration SHALL be consistent with operational SecurityConfig + JwtAuthenticationFilter behavior."

검증:
- 11 controller test의 401/403 차이 0건

### REQ-IRR-004 (Ubiquitous) — 회귀 시점 추적

**EARS**: "The system SHALL identify the exact commit hash where `GlobalExceptionHandler.handleAuthorizationDenied` was added, and document the regression impact in the SPEC change log."

검증:
- `git log --oneline -- backend/src/main/java/kr/co/ircp/cms/config/GlobalExceptionHandler.java | head -20`
- AuthorizationDeniedException 핸들러 추가 commit hash 식별

### REQ-IRR-005 (Ubiquitous) — SPEC Status 정정

**EARS**: "The SPEC documents for AUTHZ-MATRIX-001 and AUTHZ-IT-EXPAND-001 SHALL be updated from `Implemented` to `Mostly Implemented (with v0.x regression)` with a regression note pointing to this SPEC."

검증:
- SPEC v0.3 변경 이력 entry 추가
- README SPEC 표 Status 정정

---

## 6. Acceptance Criteria

| AC ID | 내용 |
|-------|------|
| AC-IRR-001-1 | AuthorizationMatrixExpandIT 31 failed → 0 failed (BUILD SUCCESSFUL) |
| AC-IRR-001-2 | 회귀 0건 (AUTHZ-MATRIX-001 + AUTHZ-IT-EXPAND-002 + AUTHZ-AUTODETECT-001) |
| AC-IRR-002-1 | AuthorizationMatrixIT 응답 코드 분기 (AUTH_FORBIDDEN vs ACCESS_DENIED) 정정 GREEN |
| AC-IRR-003-1 | controller unit test 11종 401/403 차이 0건 |
| AC-IRR-004-1 | 회귀 시점 commit hash 식별 + spec.md 명문화 |
| AC-IRR-005-1 | AUTHZ-MATRIX-001 + AUTHZ-IT-EXPAND-001 SPEC Status 정정 |

---

## 7. RUN 단계 분해

### Step 1: 회귀 시점 git log 추적 + 운영 코드 변화 분석
- GlobalExceptionHandler AuthorizationDeniedException 핸들러 추가 commit 식별
- SecurityConfig + JwtAuthenticationFilter 변화 추적
- 영향 범위 보고서 작성

### Step 2: AuthorizationMatrixExpandIT 31 failed 시나리오 정정
- 패턴 1 (403 → 400): 정상 body/param 적용 (본 세션 Phase A 패턴 재사용)
- 패턴 2 (response code mismatch): AUTH_FORBIDDEN/ACCESS_DENIED 분기 정정

### Step 3: AuthorizationMatrixIT JSON path 정정
- 인증 부재 시 `AUTH_FORBIDDEN` (응답 코드 검증)
- 인증 + 권한 부재 시 `ACCESS_DENIED`

### Step 4: controller unit test 11종 401/403 차이 진단
- 각 test의 Security 구성 검토 + 정정
- WebMvcTestInfraConfig 패턴 일관화

### Step 5: SPEC Status 정정 + Sync
- AUTHZ-MATRIX-001 + EXPAND-001 SPEC v0.x 변경 이력 추가
- README + CHANGELOG 갱신
- 본 SPEC Implemented

---

## 8. 결정 포인트

| 결정 | 옵션 | 권장 |
|------|------|------|
| **D1** 응답 코드 정책 | (a) 운영 코드 그대로 + IT 동기 / (b) 운영 코드 rollback (AUTH_FORBIDDEN 통일) | (a) — 운영 변경 우선 보존 |
| **D2** controller test 위치 | (a) 본 SPEC 포함 / (b) 별도 SPEC 분리 | (a) — 동일 회귀 원인 공유 |
| **D3** AUTHZ-MATRIX-001 v0.x | (a) Mostly Implemented + 회귀 note / (b) Implemented + 본 SPEC 참조 | (a) — 정확성 우선 |

---

## 9. 변경 이력

| 버전 | 일자 | 작성자 | 변경 내용 |
|------|------|--------|----------|
| v0.1 | 2026-05-12 | MoAI orchestrator | 초안 작성. PII-FOLLOWUP-005 v0.3 통합 실행 시 발견한 51 unit test/IT failed 회귀 진단 SPEC. 본 세션 변경 영향 0건 확정 (AuthorizationMatrixExpandIT 단독 실행도 31 failed). 회귀 패턴 3가지 확정: (1) @Valid validation 우선 → 403 expected but 400, (2) GlobalExceptionHandler AuthorizationDeniedException 핸들러 추가 → AUTH_FORBIDDEN → ACCESS_DENIED 응답 코드 변경, (3) controller unit test Security 구성 차이. REQ-IRR-001~005 + 6 AC + RUN Step 1~5 분해. AUTHZ-MATRIX-001 + AUTHZ-IT-EXPAND-001 SPEC Status 정정 필요 (Implemented → Mostly Implemented). META-IT-GREEN-MANDATORY-001 첫 위반 사례. P2 (운영 영향 0, SPEC ↔ 실제 GREEN 상태 불일치 해소). |
