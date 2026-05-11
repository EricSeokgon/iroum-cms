# SPEC-CMS-SECURITY-PII-FOLLOWUP-002: PII-FOLLOWUP-001 잔여 RED 해소 (@MockitoSpyBean + @Async 근본 충돌) v0.1

**Status**: Planned (2026-05-11) — 다음 세션 RUN 진입 권장
**Trigger**: AUTHZ-AUTODETECT-001 Step 1 실제 구동 검증 중 발견된 PII-FOLLOWUP-001 잔여 회귀
**Severity**: P1 (PII 감사 로그 IT GREEN 미달성 — PIPA 컴플라이언스 회귀 검출 능력 부재)

---

## 1. 배경 (Discovery 경위)

### 1.1 본 SPEC 발생 경위

AUTHZ-AUTODETECT-001 Step 1 (commit 2be18d0/9cb4933) 실제 구동 검증 중 **JaCoCo 통합 효과로 :integrationTest가 자동 실행**되어 PII-FOLLOWUP-001(commit 4d05349 + 5fe440b) 잔여 회귀가 노출됨.

| 단계 | 결과 | 처리 |
|------|------|------|
| 1차 진단 | 6 FAILED (모두 ApplicationContext fail cascading) | application-integration.yml `spring.main.allow-bean-definition-overriding: true` (commit 7887e38) → 3 GREEN 회복 |
| 2차 진단 | 3 FAILED (AC-FU-003-1/2/3) | Mockito matcher anyString/anySet 보강 (commit 8d0b13e) → 효과 없음 |
| 3차 진단 | 동일 3 FAILED | 본 SPEC으로 분리 |

### 1.2 결정적 발견 — PII-FOLLOWUP-001 절차 결함

PII-FOLLOWUP-001 v0.2 Implemented (commit 4d05349 + 5fe440b)는 **정적 검증만 수행**:
- @SpyBean → @MockitoSpyBean 마이그레이션 (정적)
- @Disabled 3건 활성화 (정적)
- IntegrationAsyncConfig 신설 (정적)
- **실제 Java 17 + Gradle IT GREEN 검증 0회**

본 세션 첫 실제 구동에서 모든 잔여 RED 노출. SPEC 검증 절차 강화 필요.

---

## 2. Root Cause

### 2.1 @MockitoSpyBean + @Async("auditExecutor") 이중 wrapping 충돌

```
PersonalDataAccessLogService (interface)
  ↓ @Async("auditExecutor") 메소드 적용 (recordBulk)
  ↓
Spring AOP CGLIB proxy (@Async wrapping)
  ↓
@MockitoSpyBean → CGLIB Spy (proxy를 spy)
  ↓
Mockito.doThrow().when(spy).recordBulk(...) → Stub 적용 시도
  ↓
Mockito error: "Following methods cannot be stubbed/verified:
  final/private/equals()/hashCode().
  Mocking methods declared on non-public parent classes is not supported."
```

CGLIB proxy의 메소드는 Mockito가 stub 불가능한 형태로 만들어짐 → matcher가 stack에 dangling → afterEach reset 시점에 InvalidUseOfMatchersException throw.

### 2.2 영향 AC 3건

| AC | 증상 | Root cause |
|----|------|-----------|
| AC-FU-003-1 | personal_data_access_log INSERT 0건 (expected ≥5) | Spy가 real method 호출 못 함 → recordBulk 실제 호출 안 됨 → audit log 0건 |
| AC-FU-003-2 | InvalidUseOfMatchersException | Spy stub 실패로 matcher dangling |
| AC-FU-003-3 | INSERT 0건 + duplicate check 빈 결과 | AC-FU-003-1과 동일 root cause |

---

## 3. 해결 옵션 (다음 세션 결정)

### 옵션 A: 운영 코드 리팩토링 — @Async 분리 wrapping bean
- `PersonalDataAccessLogServiceImpl`에서 @Async 제거
- 별도 `AsyncAuditDispatcher` bean 신설 → 그 안에서 @Async 적용
- Service interface는 sync method만 노출
- 장점: IT에서 @MockitoSpyBean 정상 동작
- 단점: 운영 코드 중간 영향 (호출 site 변경)

### 옵션 B: IT 전략 재설계 — @MockitoSpyBean 제거
- AC-FU-003-2 (Mockito stub 시뮬레이션) 별도 IT로 분리: real `PersonalDataAccessLogServiceImpl` 직접 unit test 또는 별도 `@MockBean` 사용
- AC-FU-003-1/3은 real method 호출 검증: `@Transactional(propagation = NOT_SUPPORTED)` 또는 `@Sql cleanup` 전략
- 장점: 운영 코드 변경 0줄
- 단점: PII Audit IT 구조 재설계 부담

### 옵션 C: @Async 동기 모드 + Spy 우회
- IntegrationAsyncConfig에서 SyncTaskExecutor + Spy 자체를 다른 방식으로 mock
- ProxyFactory로 @Async 제거된 별도 bean 등록
- 장점: 부분적 우회
- 단점: 복잡, 비표준

**권장**: 옵션 B (IT 재설계) — 운영 코드 변경 0줄 + 표준 Spring TestContext 패턴

---

## 4. EARS 요구사항

### REQ-PII-FU2-001 (Ubiquitous) — PiiAuditEnhanceIT 재설계
**EARS**: "The system SHALL refactor `PiiAuditEnhanceIT` to remove `@MockitoSpyBean` dependency and verify all 6 acceptance criteria through real method invocation, with `AC-FU-003-2` (audit insertion failure simulation) extracted to a separate unit-level test."

### REQ-PII-FU2-002 (Event-driven) — PII-FOLLOWUP-001 v0.3 회고적 정정
**EARS**: "When this SPEC is implemented and 6 AC GREEN is confirmed, PII-FOLLOWUP-001 spec.md SHALL append a v0.3 history entry noting the regression discovery and resolution."

### REQ-PII-FU2-003 (Ubiquitous) — SPEC 검증 절차 강화
**EARS**: "The MoAI process SHALL require actual Java 17 + Gradle IT GREEN verification before marking any SPEC as 'Implemented' status. Static verification (compilation, signature matching) is necessary but not sufficient."

---

## 5. Acceptance Criteria

| AC ID | 내용 |
|-------|------|
| AC-FU2-001-1 | PiiAuditEnhanceIT 6 AC 모두 GREEN (Java 17 + Gradle 실제 구동) |
| AC-FU2-001-2 | @MockitoSpyBean 의존성 제거 (코드 grep으로 확인) |
| AC-FU2-001-3 | AC-FU-003-2 (audit failure 시뮬레이션)이 별도 IT로 분리 + GREEN |
| AC-FU2-002-1 | PII-FOLLOWUP-001 spec.md v0.3 entry 추가 |
| AC-FU2-003-1 | spec-workflow.md에 "사용자 환경 IT GREEN 의무" 항목 명시 |

---

## 6. 진행 이력

### 본 세션 (2026-05-11) 처리
- [✓] Bean override 1차 수정 (commit 7887e38) → ApplicationContext 부팅 GREEN, 3 AC 회복
- [✓] Mockito matcher 타입 명시 (commit 8d0b13e) → 코드 품질 보강
- [✓] 본 SPEC 분리 작성 — Root cause 명문화 + 해결 옵션 3종 정리
- [⏸️] 옵션 결정 + RUN — 다음 세션 (사용자 환경 IT GREEN 의무 패턴 적용)

### Reference
- AUTHZ-AUTODETECT-001 Step 1 실제 구동 commit 9cb4933 (회귀 발견 트리거)
- PII-FOLLOWUP-001 commits: 4d05349, 5fe440b (회귀 원인)
- 관련 종합 보고서: `.moai/reports/security-infra-track-summary-20260511-v2.md`

---

## 7. 변경 이력

| 버전 | 일자 | 작성자 | 변경 내용 |
|------|------|--------|----------|
| v0.1 | 2026-05-11 | MoAI orchestrator | 초안 작성. AUTHZ-AUTODETECT-001 Step 1 실제 구동 검증 중 PII-FOLLOWUP-001 잔여 3 RED 발견. Root cause 명문화 (@MockitoSpyBean + @Async CGLIB proxy 충돌). 해결 옵션 3종 (운영 리팩토링 / IT 재설계 / @Async 우회) — 권장 옵션 B. REQ-PII-FU2-001~003 정의. PII-FOLLOWUP-001 절차 결함 (정적 검증만, 실제 GREEN 미검증) 명시. 다음 세션 RUN 진입 권장. |
