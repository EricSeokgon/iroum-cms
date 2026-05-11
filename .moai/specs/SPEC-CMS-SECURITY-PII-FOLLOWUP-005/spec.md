# SPEC-CMS-SECURITY-PII-FOLLOWUP-005: PiiAuditEnhanceIT AC-009-2 race condition 정밀 진단 v0.1

**Status**: Planned (2026-05-12) — 다음 세션 RUN 진입 권장
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
| v0.1 | 2026-05-12 | MoAI orchestrator | 초안 작성. PII-FOLLOWUP-004 v0.3 Mostly Implemented 후 잔여 AC-009-2 race condition 분리 SPEC. 본 세션 시도 결과 명문화: @TestMethodOrder + @Order 적용 시 AC-009-2 GREEN되나 AC-FU-003-1 회귀 → revert. 다음 세션 옵션 A/B/C/D 검토 권장. REQ-PII-FU5-001/002 + 3 AC. P3 우선순위 (PII 트랙 100% IT 완성, 운영 영향 없음). |
