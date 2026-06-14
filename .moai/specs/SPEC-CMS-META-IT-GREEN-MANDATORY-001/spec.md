# SPEC-CMS-META-IT-GREEN-MANDATORY-001: IT user environment GREEN mandatory 정책 v0.3

**Status**: Completed (2026-06-15) — MoAI sync: Tested → Completed. Evidence 강화 (PII 5건 + AUTHZ 회귀 6건 + Controller test 12건) 확인 완료.
**Implementation commits**: 75da38a (v0.1 정책 초안), 7c58647 (v0.2 README + Sync checklist), [본 commit] (v0.3 AUTHZ evidence 추가)

## v0.3 변경 이력 (2026-05-12) — AUTHZ REGRESSION-001 evidence 통합

### 추가 evidence (AUTHZ 회귀 5건)

REGRESSION-001 RUN 시 발견된 추가 회귀 패턴이 META 정책의 추가 evidence로 명문화됨:

| Case | SPEC | 위반 REQ | 해결 패턴 |
|------|------|----------|----------|
| 6 | REGRESSION-001 (응답 코드) | REQ-META-IT-002 (운영 변경 미동기) | jsonPath AUTH_FORBIDDEN → ACCESS_DENIED 28건+17건 일괄 정정 |
| 7 | REGRESSION-001 (@Valid 우선) | REQ-META-IT-002 (validation 위험 미명시) | 23+4 DTO body 정상화 (각 endpoint required fields 충족) |
| 8 | REGRESSION-001 (controller test 11+종) | REQ-META-IT-002 (@WebMvcTest Security 차이 미명시) | SecurityFilterChain 없음 → @PreAuthorize → 403 운영 동기 |
| 9 | REGRESSION-001 v0.8 (MatrixIT 8 RED) | REQ-PII-FU2-003 (단독 GREEN, 종합 검증 미실행) | 종합 회귀 검증으로 8 RED 추가 발견 → 정정 (249/0 GREEN) |
| 10 | REGRESSION-001 (service IllegalArgumentException) | REQ-META-IT-002 (운영 GlobalExceptionHandler 미커버 예외) | assertAuthzPassed helper 패턴 (ServletException + cause 허용) |

### META 정책 강화 (REGRESSION-001 case 통합)

**추가 HARD 규칙**:
- **REQ-PII-FU2-003 강화**: 단독 GREEN + 통합 GREEN뿐 아니라 **종합 회귀 검증** (전체 트랙 일괄 실행) 시 0 failed 요구
- **REQ-META-IT-002 확대**: @Transactional 외에도 **운영 GlobalExceptionHandler 커버리지** 명시 (도메인 RuntimeException 처리 여부)
- **REQ-META-IT-006 신설** (응답 코드 동기): IT 시나리오의 jsonPath 응답 코드 검증 시 운영 SecurityConfig + GlobalExceptionHandler 양쪽 분기 명확화 (AUTH_REQUIRED 401 vs AUTH_FORBIDDEN/ACCESS_DENIED 403)

### REGRESSION-001 회복 단계로 본 META 정책 validation

| Phase | RED | 발견 시점 | 정정 패턴 |
|-------|-----|----------|----------|
| Phase A | 28 (응답 코드) | Step 2 시작 | jsonPath 일괄 변경 |
| Phase B1-B5 | 23 (body 누락) | Step 2 Phase B | DTO 정상 JSON |
| Step 4 | 12 (@WebMvcTest 한계) | Step 4 | @PreAuthorize 거부 → 403 동기 |
| Step 6 v0.8 | 8 (MatrixIT) | 종합 회귀 검증 | Banner/Page/User body 정정 |
| **합계** | **59 → 0** | **REGRESSION-001 100% 회복** | **운영 코드 변경 0건** |

### META 정책 현재 적용 SPEC 목록

본 정책은 다음 SPEC에서 정식 적용 사례를 가짐:
- ✅ PII-FOLLOWUP-005 v0.3 (단독 GREEN + 통합 GREEN + @DirtiesContext race condition 회피)
- ✅ AUTHZ-IT-EXPAND-002 v0.3 (단독 + 통합 양쪽 GREEN + assertAuthzPassed helper)
- ✅ AUTHZ-IT-REGRESSION-001 v0.8 (Fully Implemented, 59 RED → 0 100% 회복, 종합 검증 evidence)

---

## v0.2 변경 이력 (2026-05-12) — README 정책 섹션 신설 + Sync checklist 명문화

### 산출물
- README.md §"IT user environment GREEN mandatory 정책 (META)" 신설
- HARD 정책 요약 4건 (단독+통합 양쪽 GREEN / @Transactional 위험 / race condition 회피 / Sync evidence)
- Sync checklist 4 항목 표 (단독 GREEN / 통합 GREEN / @Transactional 위험 / race condition 회피)
- 적용 사례 5건 evidence 표 (PII-FOLLOWUP-001~005)
- SPEC 참조 링크 추가

### Status: Planned → Implemented
정책 문서이므로 IT 신설 0건, 운영 코드 변경 0건. README + spec.md만 갱신.

### 향후 적용
- 신규 SPEC RUN 단계: HARD 정책 4건 준수
- SPEC Sync 단계: checklist 4 항목 모두 evidence 명시
- 누락 시 Implemented 아닌 Mostly Implemented / Partially Diagnosed 상태로만 인정

---
**Trigger**: PII-FOLLOWUP-001 ~ PII-FOLLOWUP-005 트랙에서 반복 발견된 false GREEN + race condition 패턴
**Severity**: P2 (cross-cutting 정책, 향후 SPEC 품질 보장)

---

## 1. 개요

| 항목 | 내용 |
|------|------|
| SPEC ID | SPEC-CMS-META-IT-GREEN-MANDATORY-001 |
| 제목 | IT user environment GREEN mandatory 정책 명문화 |
| 우선순위 | P2 (cross-cutting 메타) |
| 분류 | Process / Quality Gate / Meta Policy |
| 의존 | PII-FOLLOWUP-002/003/004/005 트랙 (실증 evidence) |
| 영향 범위 | 전 SPEC RUN 단계 IT 작성 + Sync 단계 검증 |

---

## 2. 배경 — false GREEN 5건 evidence

PII 트랙에서 5건의 false GREEN/race condition 패턴이 발견됨:

| SPEC | 패턴 | 발견 방식 |
|------|------|----------|
| PII-FOLLOWUP-001 | @MockitoSpyBean + @Async CGLIB proxy 충돌 | 운영 환경 실측 시 RED |
| PII-FOLLOWUP-002 | Spring transaction propagation HikariCP readOnly sticky | 운영 환경 실측 시 RED |
| PII-FOLLOWUP-003 | @Transactional rollback이 audit 적재 가림 (false GREEN) | 옵션 G TRUNCATE cleanup 적용 후 false GREEN 노출 |
| PII-FOLLOWUP-004 AC-009-3 | UnexpectedRollbackException (AuthService catch + REQUIRED propagation) | mockMvc.perform() 직접 실패 |
| PII-FOLLOWUP-004 AC-009-4 | PersonalDataAccessAspect selfAccessOnly 의미 반전 (SPEC ↔ 운영 차이) | SPEC 가정 vs 실제 운영 매칭 차이 |
| PII-FOLLOWUP-005 AC-009-2 | SyncTaskExecutor + @Async + @Transactional REQUIRES_NEW 통합 race condition (단독 GREEN, 통합 RED) | 단독 vs 통합 실행 결과 차이 |

### 핵심 발견
1. **단독 GREEN ≠ 통합 GREEN**: PII-FOLLOWUP-005 AC-009-2 단독 실행 PASSED, 통합 실행 RED — race condition 가능성
2. **@Transactional rollback이 audit/async 효과를 가릴 수 있음** (PII-FOLLOWUP-003)
3. **운영 동작과 SPEC 가정의 차이** — IT가 SPEC 가정만 검증하면 운영 회귀를 놓침 (PII-FOLLOWUP-004)
4. **CGLIB proxy + @Async + @MockitoSpyBean 조합 위험** (PII-FOLLOWUP-001)
5. **Transaction propagation REQUIRED vs REQUIRES_NEW** — 외부 catch block + inner rollback-only 마킹 시 UnexpectedRollbackException

---

## 3. 범위 + 비범위

### 3.1 범위 (P2)

| REQ | 설명 |
|-----|------|
| **REQ-PII-FU2-003** | 신규 IT 작성 시 단독 실행 + 통합 실행 양쪽 GREEN 필수 |
| **REQ-META-IT-002** | @Transactional 위험 명시 — audit/async 효과를 가리는 패턴 회피 |
| **REQ-META-IT-003** | race condition 회피 패턴 명시 (@DirtiesContext, deterministic order, isolated test class) |
| **REQ-META-IT-004** | spec-workflow.md (.claude/rules/moai/) 갱신 (본 프로젝트 한정) — 또는 별도 .moai/policies/it-mandatory.md 신설 |
| **REQ-META-IT-005** | Sync 단계 정책 검증 강화 — 단독 GREEN만 명시한 SPEC은 Sync 차단 |

### 3.2 비범위

- 기존 IT 회귀 검증 (5건은 이미 추적됨)
- spec-workflow.md MoAI 글로벌 갱신 (별도 PR 필요)
- IT framework 변경 (Testcontainers 유지)
- 운영 코드 변경 (정책 문서 작성 전용)

---

## 4. EARS 요구사항

### REQ-PII-FU2-003 (Ubiquitous) — 단독 + 통합 GREEN mandatory

**EARS**: "When a new Integration Test (IT) is created, the test SHALL pass in both standalone execution (`--tests 'TestClass.testMethod'`) AND integrated execution (`./gradlew test` or `./gradlew integrationTest`) before SPEC Sync stage."

검증:
- 단독 PASS 명시: SPEC commit message 또는 README 갱신에 명시
- 통합 PASS 명시: 전체 `./gradlew check` 또는 `./gradlew build` BUILD SUCCESSFUL

### REQ-META-IT-002 (Ubiquitous) — @Transactional 위험 명시

**EARS**: "When an IT class uses `@Transactional` at class or method level, the SPEC SHALL document the audit/async/commit-timing effects that may be hidden by transaction rollback, and the IT SHALL include at least one scenario that verifies post-commit state explicitly (e.g., using TRUNCATE cleanup or @DirtiesContext)."

검증:
- @Transactional 사용 SPEC: spec.md에 "@Transactional 위험" 섹션 명시
- IT 시나리오: 최소 1건 post-commit 검증 (TRUNCATE/@DirtiesContext)

### REQ-META-IT-003 (Event-driven) — race condition 회피 패턴

**EARS**: "When an IT scenario involves async execution (`@Async`, `SyncTaskExecutor`, `CompletableFuture`) interacting with `@Transactional` boundaries, the IT SHALL apply one of the following race-condition avoidance patterns: (a) `@DirtiesContext` per method, (b) `@TestMethodOrder` with explicit ordering + cleanup, (c) standalone-only execution mode marked with javadoc + CI separation."

검증:
- async + transaction 조합 IT: spec.md에 race condition 회피 패턴 명시
- 패턴 (a/b/c) 중 1개 선택 + 근거 commit message에 명시

### REQ-META-IT-004 (Ubiquitous) — 정책 문서 위치

**EARS**: "The IT mandatory policy SHALL be documented at `.moai/policies/it-mandatory.md` in the project root, referenced by future SPEC RUN/Sync stages."

대안 (실용적): 본 SPEC spec.md 자체를 참조 문서로 사용 + README에 링크 추가.

### REQ-META-IT-005 (Event-driven) — Sync 단계 검증 강화

**EARS**: "When SPEC enters Sync stage (status Implemented), the Sync checklist SHALL verify that (a) standalone IT execution evidence is recorded, AND (b) integrated test suite BUILD SUCCESSFUL evidence is recorded. Sync SHALL be blocked if either is missing."

검증:
- Sync commit message: "단독 GREEN" + "통합 BUILD SUCCESSFUL" 둘 다 명시
- 누락 시 Sync 재진입 또는 SPEC Mostly Implemented (Partial)로 강등

---

## 5. Acceptance Criteria

| AC ID | 내용 |
|-------|------|
| AC-META-IT-001-1 | 신규 IT는 단독 실행 PASS 후 통합 실행 BUILD SUCCESSFUL 양쪽 검증 |
| AC-META-IT-001-2 | SPEC commit message에 단독 + 통합 양쪽 검증 명시 |
| AC-META-IT-002-1 | @Transactional 사용 SPEC은 spec.md에 위험 섹션 포함 |
| AC-META-IT-002-2 | @Transactional IT는 post-commit 검증 시나리오 최소 1건 포함 |
| AC-META-IT-003-1 | async + transaction 조합 IT는 race condition 회피 패턴 (a/b/c) 중 1개 선택 |
| AC-META-IT-004-1 | 본 SPEC spec.md를 IT mandatory policy 참조 문서로 README에 링크 |
| AC-META-IT-005-1 | Sync checklist에 단독 + 통합 evidence 항목 명시 |

---

## 6. 적용 사례 (PII 트랙 회고)

### Case 1: PII-FOLLOWUP-003 — @Transactional rollback false GREEN
- **위반 REQ**: REQ-META-IT-002 (transaction 위험 미명시)
- **해결**: 옵션 G TRUNCATE cleanup + @Transactional 제거
- **본 SPEC 적용 시**: 신규 IT 작성 시 transaction 위험 spec.md 명시 + post-commit 검증 시나리오 필수

### Case 2: PII-FOLLOWUP-005 AC-009-2 — race condition 단독 GREEN
- **위반 REQ**: REQ-PII-FU2-003 (단독만 GREEN, 통합 RED)
- **해결**: Partially Diagnosed 상태로 후속 SPEC 분리 + 다음 세션 @DirtiesContext
- **본 SPEC 적용 시**: 단독 + 통합 양쪽 검증 필수 + race condition 회피 패턴 (a/b/c) 선택

### Case 3: PII-FOLLOWUP-001 — @MockitoSpyBean + @Async CGLIB 충돌
- **위반 REQ**: REQ-META-IT-002 (async + mock 위험 미명시)
- **해결**: Fallback Unit test 분리
- **본 SPEC 적용 시**: @Async + @MockitoSpyBean 조합 위험 spec.md 명시

### Case 4: PII-FOLLOWUP-004 AC-009-4 — SPEC 가정 vs 운영 차이
- **위반 REQ**: REQ-META-IT-002 (운영 동작 미검증)
- **해결**: IT 시나리오 expected 정정 (운영 동작 우선)
- **본 SPEC 적용 시**: IT 작성 전 운영 동작 grep 검증 + SPEC 가정과 비교

---

## 7. 결정 포인트

| 결정 | 옵션 | 권장 |
|------|------|------|
| **D1** 정책 문서 위치 | (a) 본 SPEC spec.md 단독 / (b) .moai/policies/it-mandatory.md 신설 / (c) spec-workflow.md (.claude/rules/moai/) 갱신 | (a) — 본 SPEC 자체가 정책 참조 |
| **D2** spec-workflow.md 갱신 시점 | (a) 본 SPEC RUN 시 / (b) 별도 PR / (c) 보류 | (c) — MoAI 글로벌 rule 변경은 신중 |
| **D3** Sync checklist 강화 시점 | (a) 본 SPEC RUN 시 즉시 / (b) 향후 PR | (a) — Sync stage 검증 항목 README 추가 |
| **D4** 정책 enforcement | (a) 권고 사항 / (b) HARD rule | (b) — REQ-PII-FU2-003은 HARD (단독 GREEN만으로 Implemented 인정 금지) |

---

## 8. RUN 단계 분해

### Step 1: 정책 문서 작성
- 본 spec.md를 정책 참조 문서로 확정
- README에 SPEC 참조 링크 추가 (Sync 단계 IT mandatory 정책 명시)

### Step 2: Sync checklist 강화
- README "Sync 단계 검증 항목" 섹션 갱신
- "단독 GREEN" + "통합 BUILD SUCCESSFUL" 양쪽 evidence 명시

### Step 3: 적용 사례 회고
- PII-FOLLOWUP-001~005 회고 명시 (본 spec.md §6 완료)

### Step 4: Sync v0.2 Implemented
- 본 SPEC 자체는 정책 문서이므로 IT 생성 X
- README + CHANGELOG + spec.md만 갱신

---

## 9. 변경 이력

| 버전 | 일자 | 작성자 | 변경 내용 |
|------|------|--------|----------|
| v0.4 | 2026-05-13 | MoAI orchestrator | IT 검증 완료 — META-IT 정책 운영 적용 검증 완료 (PII + AUTHZ 트랙 100% GREEN). Implemented → Tested. |
| v0.3 | 2026-05-12 | MoAI orchestrator | **Evidence 강화**. REGRESSION-001 RUN에서 발견한 추가 회귀 5 case (응답 코드 변경 28+17건, @Valid 우선 23+4건, @WebMvcTest 한계 11+종, MatrixIT 8건 종합 검증 미실행, service IllegalArgumentException helper) 통합. REQ-PII-FU2-003 강화 (종합 회귀 검증 추가), REQ-META-IT-002 확대 (GlobalExceptionHandler 커버리지), REQ-META-IT-006 신설 (응답 코드 동기). 본 정책 정식 적용 SPEC 3건 명시 (PII-FU-005 + AUTHZ-EXPAND-002 + REGRESSION-001). 정책 문서 전용, 운영 코드/IT 신설 0건. |
| v0.2 | 2026-05-12 | MoAI orchestrator | **Implemented**. README §"IT user environment GREEN mandatory 정책 (META)" 신설 — HARD 정책 요약 4건 + Sync checklist 4 항목 표 + 적용 사례 5건 evidence 표 + SPEC 참조 링크. PII-FOLLOWUP-005가 본 정책의 첫 적용 사례 (단독 GREEN, 통합 race condition → Partially Diagnosed). 정책 문서 전용, 운영 코드/IT 신설 0건. 향후 SPEC RUN/Sync 단계 품질 게이트로 작동. |
| v0.1 | 2026-05-12 | MoAI orchestrator | 초안 작성. PII-FOLLOWUP-001~005 트랙에서 발견된 false GREEN/race condition 패턴 5건 evidence 기반 IT user environment GREEN mandatory 정책 명문화. REQ-PII-FU2-003 (단독+통합 양쪽 GREEN 필수) + REQ-META-IT-002 (@Transactional 위험 명시) + REQ-META-IT-003 (race condition 회피 패턴) + REQ-META-IT-004 (정책 문서 위치) + REQ-META-IT-005 (Sync 단계 검증 강화) 정의. 7 AC. 결정 포인트 D1~D4. RUN Step 1~4 분해. 정책 문서 전용, 운영 코드/IT 신설 0건. PII 트랙 5건 회고로 evidence 검증. 향후 SPEC RUN/Sync 단계 품질 게이트로 작동. |
