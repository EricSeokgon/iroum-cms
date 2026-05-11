# SPEC-CMS-SECURITY-PII-FOLLOWUP-003: PII Audit IT 잔여 2 AC 해소 (HikariCP readOnly connection 본질적 제약) v0.1

**Status**: Planned (2026-05-11) — 다음 세션 RUN 진입 권장
**Trigger**: PII-FOLLOWUP-002 v0.2 Implemented 후 잔여 2 AC (AC-FU-003-1/3 audit row 적재 검증) Known Limitation 인정 + 옵션 A/C/F 시도 실패
**Severity**: P2 (PII Audit IT 100% GREEN 회복, 운영 회귀 위험 0)

---

## 1. 배경 (PII-FOLLOWUP-002 v0.2 §3.4 결론)

PII-FOLLOWUP-002 v0.2 Implemented로 핵심 목표(Spy + @Async CGLIB proxy 충돌) 100% 해소.
잔여 2 AC (AC-FU-003-1/3 audit row N건 적재 검증)는 **Spring transaction propagation API로 해결 불가** 확정:

| 옵션 | 시도 결과 | commit |
|------|----------|--------|
| A: REQUIRES_NEW 단독 | 실패 (audit row 0건) | 94ae3b1 (revert) |
| C: @Async 분리 wrapping bean | 실패 (동일 패턴) | f2b9018 (revert) |
| F: REQUIRES_NEW + readOnly=false 명시 | 실패 (동일 패턴) | 555e044 (revert) |

근본 추정: HikariCP `connection.setReadOnly(true)` sticky (`@Transactional(readOnly=true)` UserService.findPage 진입 시점). REQUIRES_NEW로 새 tx 시작해도 thread bound connection 재사용 → INSERT 차단.

---

## 2. 해결 옵션 (다음 세션 사용자 결정 필요)

### 옵션 D: HikariCP 별도 DataSource pool (audit 전용)
- 운영 인프라 변경 (DataSourceConfig + MyBatis SqlSessionFactory 분리)
- audit 전용 connection pool은 readOnly sticky 없음
- 장점: 가장 근본적 해결
- 단점: 운영 변경 큼 (30~60분), MyBatis mapper 분리 필요

### 옵션 E: TransactionTemplate + 별도 connection 강제
- PersonalDataAccessLogServiceImpl에 TransactionTemplate + DataSource 주입
- recordBulk 내부에서 `template.execute(status -> { conn.setReadOnly(false); ... })` 패턴
- 장점: DataSource 분리 없음
- 단점: 코드 패턴 변경 + Spring 표준 우회

### 옵션 G: PiiAuditEnhanceIT 재설계 (IT 측 우회)
- @Transactional 클래스 어노테이션 제거
- @AfterEach에서 TRUNCATE personal_data_access_log 시도 (PIPA BEFORE DELETE 트리거 우회 — TRUNCATE는 별도 트리거)
- 또는 별도 connection으로 jdbcTemplate cleanup
- 장점: 운영 코드 0줄
- 단점: PIPA APPEND-ONLY 의도 우회 (TRUNCATE 트리거 차단 여부 검증 필요)

### 권장 우선순위 (다음 세션)
1. **옵션 G 우선 검증** (운영 코드 0줄, PIPA 정책 준수 가능성)
2. G 실패 시 **옵션 D 채택** (별도 DataSource pool, 운영 인프라 1회 변경)
3. E는 비추천 (Spring 표준 우회 + 운영 패턴 변경)

---

## 3. EARS 요구사항

### REQ-PII-FU3-001 (Ubiquitous) — PiiAuditEnhanceIT 5 AC GREEN 회복
**EARS**: "The system SHALL achieve GREEN status for all 5 acceptance criteria of `PiiAuditEnhanceIT` (AC-009-2, AC-009-3, AC-009-4, AC-FU-003-1, AC-FU-003-3) without modifying operational behavior of `PersonalDataAccessLogServiceImpl`."

### REQ-PII-FU3-002 (Event-driven) — 운영 회귀 0건 보장
**EARS**: "When this SPEC is implemented, AUTHZ-MATRIX-001 (19 AC), AUTHZ-IT-EXPAND-001 (88 AC), AUTHZ-AUTODETECT-001 (4 AC), and `PersonalDataAccessLogServiceImplFallbackTest` (3 AC) SHALL all retain GREEN status."

### REQ-PII-FU3-003 (Ubiquitous) — 채택 옵션 명문화
**EARS**: "The implementation SHALL document the chosen option (D, E, or G) in commit messages and update README + CHANGELOG with the rationale."

---

## 4. Acceptance Criteria

| AC ID | 내용 |
|-------|------|
| AC-FU3-001-1 | PiiAuditEnhanceIT 5 AC 모두 GREEN (실제 Java 17 + Gradle 구동) |
| AC-FU3-002-1 | AUTHZ-MATRIX-001 19 AC 회귀 0건 |
| AC-FU3-002-2 | AUTHZ-IT-EXPAND-001 88 AC 회귀 0건 |
| AC-FU3-002-3 | AUTHZ-AUTODETECT-001 4 AC 회귀 0건 |
| AC-FU3-002-4 | PersonalDataAccessLogServiceImplFallbackTest 3 AC 회귀 0건 |
| AC-FU3-003-1 | 채택 옵션 README + CHANGELOG 명문화 |

---

## 5. 변경 이력

| 버전 | 일자 | 작성자 | 변경 내용 |
|------|------|--------|----------|
| v0.1 | 2026-05-11 | MoAI orchestrator | 초안 작성. PII-FOLLOWUP-002 v0.2 잔여 2 AC (HikariCP readOnly connection sticky 본질적 제약) 해소 SPEC 분리. 본 세션 시도 결과: 옵션 A (REQUIRES_NEW 단독, commit 94ae3b1 revert) + 옵션 C (@Async 분리 wrapping bean, commit f2b9018 revert) + 옵션 F (REQUIRES_NEW + readOnly=false 명시, commit 555e044 revert) 모두 실패 — Spring transaction propagation API 한계 실증. 다음 세션 권장 옵션 D (별도 DataSource pool) / E (TransactionTemplate) / G (IT 재설계). REQ-PII-FU3-001/002/003 정의. 운영 코드 변경 최소화 우선 (옵션 G 우선 검증). |
