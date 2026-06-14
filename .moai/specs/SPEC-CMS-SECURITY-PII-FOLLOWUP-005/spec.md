# SPEC-CMS-SECURITY-PII-FOLLOWUP-005: PiiAuditEnhanceIT AC-009-2 race condition 정밀 진단 v0.3

**Status**: Completed (2026-06-15) — MoAI sync: Tested → Completed. Option B @DirtiesContext 적용 → 5/5 GREEN 완성 확인 완료.
**Implementation commits**: b44cab6 (v0.1 Planned), 667332d (v0.2 옵션 A 진단), [본 commit] (v0.3 Option B 적용)

## v0.3 변경 이력 (2026-05-12) — Option B @DirtiesContext 적용

### Status: Partially Diagnosed → Implemented

PiiAuditEnhanceIT 클래스에 `@DirtiesContext(classMode = AFTER_EACH_TEST_METHOD)` 적용:
- 각 test 후 Spring context 재생성 → SyncTaskExecutor + @Async + @Transactional(REQUIRES_NEW) 통합 race condition 완전 회피
- 단독 GREEN ↔ 통합 GREEN 동등성 보장

### 검증 결과
- **단독 실행**: `--tests "PiiAuditEnhanceIT.findPage_selfRowExcludedFromAudit"` → PASSED
- **통합 실행**: `./gradlew :backend:integrationTest --tests "PiiAuditEnhanceIT"` → **5 tests / 0 failed / 0 skipped**
  - AC-009-2 (본인 row 제외) — PASSED
  - AC-009-3 (HMAC lookup-only 미적재) — PASSED
  - AC-009-4 (self-access auditing) — PASSED
  - AC-FU-003-1 (ADMIN findPage N건) — PASSED
  - AC-FU-003-3 (각 target row 적재) — PASSED

### PII 트랙 완성
PII-FOLLOWUP-001 ~ 005 모두 Implemented:
- PII-FOLLOWUP-001: Implemented (PII 마스킹 + 분리 암호화)
- PII-FOLLOWUP-002: Implemented (@MockitoSpyBean + Fallback Unit test)
- PII-FOLLOWUP-003: Implemented (옵션 G TRUNCATE cleanup)
- PII-FOLLOWUP-004: Implemented (VerificationService REQUIRES_NEW + IT 시나리오 정정, 4/5 → 5/5 회복)
- PII-FOLLOWUP-005: **Implemented** (Option B @DirtiesContext, 5/5 GREEN)

### META-IT-GREEN-MANDATORY-001 REQ-PII-FU2-003 준수
본 SPEC은 META 정책의 첫 번째 정식 적용 사례:
- 단독 GREEN evidence: AC-009-2 단독 실행 PASSED (v0.2 commit 667332d)
- 통합 GREEN evidence: 본 commit `./gradlew integrationTest` 5 tests 0 failed
- race condition 회피 패턴: (a) `@DirtiesContext` AFTER_EACH_TEST_METHOD 적용 (META REQ-META-IT-003)

### 비용
- 각 test ~30초 부팅 (5 test ≈ 2.5분 추가)
- 안전성 최대 — race condition 완전 회피

### 변경 산출물
- PiiAuditEnhanceIT.java:
  - import org.springframework.test.annotation.DirtiesContext 추가
  - 클래스 레벨 @DirtiesContext annotation 추가
  - AC-009-2 진단 디버그 코드 제거 (System.out.println, jdbcTemplate.queryForList) — Option B 적용 후 정상 시나리오로 정리
  - 클래스 javadoc에 Option B 적용 명시

---

## v0.2 변경 이력 (2026-05-12) — 옵션 A 진단 결과

### 결정적 발견: AC-009-2 자체 로직은 정상
- 단독 실행 (`--tests "...findPage_selfRowExcludedFromAudit"`) → **PASSED**
- 통합 실행 (다른 test와 함께) → RED 발생
- **순수한 race condition 확정** — 시나리오 자체 버그 아님

### Root cause 확정
- @AfterEach/@BeforeEach TRUNCATE + SyncTaskExecutor + @Transactional(REQUIRES_NEW) 조합의 본질적 한계
- 이전 시도 (@Order(1) AC-009-2 첫 실행)에서 AC-009-2 GREEN + AC-FU-003-1 RED 결과와 일관
- test 간 audit row commit timing이 비동기적 (SyncTaskExecutor라도 transaction commit은 별개)

### 해결 옵션 재정리

| 옵션 | 설명 | 비용 |
|------|------|------|
| **B** (권장) | `@DirtiesContext`로 각 test 후 Spring context 재생성 | 큼 (각 test ~30초 부팅) |
| A (완료) | jdbcTemplate 디버그 출력 — root cause 확정 | 0 (진단만) |
| C | default 알파벳 순 — 안정성 불확정 | 작음 |
| D | 운영 코드 변경 — 본질적 변경 부담 | 큼 |

### 권장 운영 패턴 (다음 세션)
1. PiiAuditEnhanceIT를 **단독 실행 only** 모드로 명시 (javadoc)
2. CI에서 `./gradlew test --tests "PiiAuditEnhanceIT.*"`로 단독 실행
3. 통합 실행 (전체 IT) 시 race condition으로 인한 RED 인정 + known limitation 명시
**Trigger**: PII-FOLLOWUP-004 v0.3 Mostly Implemented (4/5 GREEN) 후 잔여 AC-009-2 분리
**Severity**: P3 (PII 트랙 100% IT 완성, 운영 영향 없음)

---

## 1. 배경

PII-FOLLOWUP-004 v0.3 Mostly Implemented (commit `a886b20`):
- 4 AC PASSED: AC-009-3 (VerificationService REQUIRES_NEW 운영 fix), AC-009-4 (IT 시나리오 정정), AC-FU-003-1, AC-FU-003-3
- 1 AC SKIPPED: **AC-009-2** (race condition known limitation)

## 2. AC-009-2 시도 결과 (본 세션 학습)

### @TestMethodOrder(OrderAnnotation) + @Order(1) 시도
- AC-009-2 @Order(1) 첫 번째 실행 → AC-009-2 PASSED
- 그러나 AC-FU-003-1 (@Order 미적용)이 회귀 RED
- 모든 메소드 @Order(1-5) 적용 시도 → AC-FU-003-1 동일 RED 회귀
- 본 시도 revert → 4/5 GREEN 안정 상태 유지

### Root cause 추정
JUnit 5 deterministic 순서 적용 시 무언가 stateful 영향:
- AC-009-2 (selfId 적재 + audit 적재) 후 AC-FU-003-1 (admin actor + 5명 audit) 실행 시
- @AfterEach/@BeforeEach TRUNCATE 후에도 SyncTaskExecutor + @Async + @Transactional REQUIRES_NEW commit 타이밍 영향
- 또는 AC-009-2 mockMvc 호출이 hidden state (SecurityContextHolder, MDC 등) 유지

---

## 3. 다음 RUN 진단 옵션

### 옵션 A: jdbcTemplate.queryForList로 실측 row 디버깅
- AC-009-2 시나리오에 audit row 출력 추가
- selfId target_user_id가 실제로 audit 적재되는지 확인
- 본인 제외 filter 동작 검증

### 옵션 B: @DirtiesContext + Spring context 재생성
- 각 test 후 Spring context dirty 처리
- 비용 큼 (각 test마다 부팅), 안정성 최대

### 옵션 C: 메소드명 알파벳 순 (Default) 사용
- @TestMethodOrder 제거
- JUnit 5 default 순서 활용
- 알파벳 순 일관성 검증

### 옵션 D: 운영 코드 디버깅 — UserService.findPage filter selfId 비교
- UserServiceImpl line 121 `filter(id -> id != actor.userId())` 정밀 디버깅
- Long/long autoboxing 검토 (이미 verified safe, 그러나 재검증)
- selfId 매칭 로직 unit test 추가

---

## 4. EARS 요구사항

### REQ-PII-FU5-001 (Ubiquitous) — AC-009-2 GREEN 회복
**EARS**: "The system SHALL achieve GREEN status for PiiAuditEnhanceIT AC-009-2 (본인 row 제외 검증) without regressing other 4 AC."

### REQ-PII-FU5-002 (Event-driven) — race condition 회피 패턴 확립
**EARS**: "When PII Audit IT scenarios involve self-access + admin findPage interleaving, the system SHALL establish a deterministic execution pattern that avoids race condition between TRUNCATE cleanup and SyncTaskExecutor commit timing."

---

## 5. Acceptance Criteria

| AC | 내용 |
|----|------|
| AC-FU5-001-1 | PiiAuditEnhanceIT AC-009-2 GREEN (실제 Java 17 + Gradle) |
| AC-FU5-001-2 | AC-009-3/4 + AC-FU-003-1/3 회귀 0건 |
| AC-FU5-002-1 | race condition 회피 패턴 spec.md에 명문화 (옵션 A/B/C/D 채택 결정) |

---

## 6. 변경 이력

| 버전 | 일자 | 작성자 | 변경 내용 |
|------|------|--------|----------|
| v0.4 | 2026-05-13 | MoAI orchestrator | IT 검증 완료 — @DirtiesContext 적용 5/5 GREEN 완성. PII-FOLLOWUP 트랙 전체 완료. Implemented → Tested. |
| v0.3 | 2026-05-12 | MoAI orchestrator | **Implemented**. Option B @DirtiesContext(AFTER_EACH_TEST_METHOD) 적용 → 5/5 GREEN 완성. 단독 + 통합 양쪽 GREEN 동등성 보장 (META-IT-GREEN-MANDATORY-001 REQ-PII-FU2-003 첫 정식 적용). AC-009-2 진단 디버그 코드 제거. PII-FOLLOWUP 1~5 트랙 전체 Implemented 완성. 통합 실행: 5 tests / 0 failed / 0 skipped. 비용: 각 test ~30초 부팅 (5 test ≈ 2.5분), 안전성 최대. |
| v0.2 | 2026-05-12 | MoAI orchestrator | 옵션 A 진단 완료. AC-009-2 단독 실행 PASSED, 통합 RED — 순수 race condition 확정. Status: Planned → Partially Diagnosed. 다음 세션 권장 옵션 B (@DirtiesContext). |
| v0.1 | 2026-05-12 | MoAI orchestrator | 초안 작성. PII-FOLLOWUP-004 v0.3 Mostly Implemented 후 잔여 AC-009-2 race condition 분리 SPEC. 본 세션 시도 결과 명문화: @TestMethodOrder + @Order 적용 시 AC-009-2 GREEN되나 AC-FU-003-1 회귀 → revert. 다음 세션 옵션 A/B/C/D 검토 권장. REQ-PII-FU5-001/002 + 3 AC. P3 우선순위 (PII 트랙 100% IT 완성, 운영 영향 없음). |
