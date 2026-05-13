# SPEC-CMS-SECURITY-PII-FOLLOWUP-001: PII 비동기 감사 IT 검증 인프라 정비 (@Disabled 3건 활성화) v0.2

## 1. 개요

| 항목 | 내용 |
|------|------|
| SPEC ID | SPEC-CMS-SECURITY-PII-FOLLOWUP-001 |
| 제목 | PII 비동기 감사 IT 검증 인프라 정비 (@Disabled 3건 활성화) |
| 작성일 | 2026-05-08 |
| 작성자 | manager-spec (MoAI) |
| 상태 | Tested |
| 우선순위 | **P2 (Operational quality / non-blocker)** |
| 분류 | Test Infrastructure SPEC (코드/Production 변경 없음 — 순수 테스트 인프라 + IT 활성화) |
| 의존 SPEC | SPEC-CMS-SECURITY-PII-002 §5.5 REQ-PII-EMAIL-009 (RUN 1차 완료, commit 6aadc45까지) |
| 형제 SPEC | SPEC-CMS-SECURITY-PII-002 (PII 노출 통제 — Implemented 1차 완료), SPEC-CMS-SECURITY-PII-001 (Email AES-256-GCM 암호화 — Implemented 1차 완료) |

본 SPEC은 SPEC-CMS-SECURITY-PII-002 RUN 1차에서 비동기 감사(@Async("auditExecutor") 기반 `recordBulk`) IT 검증 인프라 부재로 인해 `@Disabled`로 격리된 IT 3건(`PiiAuditEnhanceIT.findPage_bulkAuditLog_nRows`, `auditInsertFailure_returns200AndDoesNotPropagateError`, `findPage_bulkAudit_distinctTargetUserIds`, 각각 PII-002 acceptance.md 기준 AC-009-1 / AC-009-5 / AC-009-6)의 forward reference를 해소하기 위한 후속 SPEC이다. **운영 코드 변경은 없으며**, 본 SPEC의 모든 산출물은 IT-only 테스트 인프라(SyncTaskExecutor override, `@MockitoSpyBean` 마이그레이션) 및 기존 IT의 `@Disabled` 제거에 한정된다. PII-002 §5.5 REQ-PII-EMAIL-009 적재 정책(본인 row 제외, HMAC lookup-only 제외, AOP fallback 200 응답)은 본 SPEC에서 변경하지 않으며, 동일 행동을 결정적(deterministic)으로 검증할 수 있는 IT 인프라를 제공하는 것이 목적이다.

**구현 대상 요구사항**: REQ-PII-FU-001 (비동기 IT 검증 인프라), REQ-PII-FU-002 (`@SpyBean` → `@MockitoSpyBean` 마이그레이션), REQ-PII-FU-003 (@Disabled IT 3건 활성화 검증).

본 SPEC의 1차 범위는 (1) IT-only `auditExecutor` Bean을 SyncTaskExecutor로 override하여 `@Async("auditExecutor") + @Transactional` IT 환경의 결정적 검증을 가능하게 하고, (2) Spring Boot 3.5.9 환경의 deprecated `@SpyBean` import를 Spring Framework 6.2 표준 `@MockitoSpyBean`으로 마이그레이션하며, (3) `PiiAuditEnhanceIT`에 격리되어 있는 `@Disabled` IT 3건을 활성화하여 PII-002 RUN 1차에서 단위 테스트로만 검증되었던 행동을 IT 레이어로 끌어올리는 것이다. 신규 DDL은 없으며, 운영 환경(default Spring profile)에는 영향을 주지 않는다(`@ActiveProfiles("integration")` 한정).

---

## 2. 배경 및 동기

### 2.1 PII-002 RUN 1차의 forward reference 인용

`backend/src/test/java/kr/co/ircp/cms/domain/security/pii/PiiAuditEnhanceIT.java`에는 PII-002 RUN 1차에서 격리된 3건의 IT가 다음과 같이 본 SPEC을 forward reference 형태로 참조하고 있다.

| IT 메서드 (line) | 격리 사유 (요약) | PII-002 AC 매핑 |
|------------------|------------------|------------------|
| `findPage_bulkAuditLog_nRows()` (line 96-101) | `@Async("auditExecutor") + REQUIRES_NEW` IT 검증 인프라 부재 — SyncTaskExecutor IT-only override 또는 backend service 호출 흐름 진단 후 활성화 | AC-009-1 |
| `auditInsertFailure_returns200AndDoesNotPropagateError()` (line 188-191) | Mockito `@SpyBean` 시그니처 매칭 실패 — Spring Boot 3.4 `@MockitoSpyBean` 마이그레이션 또는 SpyBean 한계 검토 필요 | AC-009-5 |
| `findPage_bulkAudit_distinctTargetUserIds()` (line 217-219) | AC-009-1과 동일 사유(비동기 `recordBulk` IT 검증 인프라). REQ-PII-EMAIL-009 핵심 동작은 단위 테스트로 검증됨 | AC-009-6 |

본 SPEC은 위 3건의 forward reference 정합성을 회수하고, 단위 테스트로만 충족되어 있는 PII-002 §5.5 동작 검증을 IT 레이어로 승급한다.

### 2.2 비동기 IT 검증 인프라 부재 진단

PII-002 §5.5 RUN 1차에서 도입된 행동 — `UserServiceImpl.findPage(actor)` 결과 N건을 `personal_data_access_log`에 일괄 적재하는 `recordBulk`는 `@Async("auditExecutor")` 적용 메서드이다(SPEC-CMS-SECURITY-PII-002 §5.5, commit 04b9fe3). `@Async` 호출은 별도 ThreadPoolTaskExecutor(`auditExecutor`, AsyncConfig.java 참조: core=2, max=8, queue=500, CallerRunsPolicy)에서 실행되므로 IT 호출 스레드와 트랜잭션 컨텍스트가 분리된다.

PII-002 RUN 1차의 `PiiAuditEnhanceIT`는 클래스 레벨 `@Transactional`(line 50-53)으로 IT 격리를 보장하지만, `@Async + @Transactional(REQUIRES_NEW)` 적재는 IT 트랜잭션이 commit되기 전에 별도 스레드/별도 트랜잭션에서 실행되므로 동일 IT 안에서 적재된 row를 SELECT하기 어렵다. PII-002 RUN 1차에서는 Awaitility 기반 polling(line 112-114, 231-239)을 도입했지만 결정적 검증은 실패했고, 결국 3건이 `@Disabled`로 격리되었다.

### 2.3 `@SpyBean` deprecated 진단

Spring Boot 3.4(2024-11-21 출시)부터 `org.springframework.boot.test.mock.mockito.SpyBean`은 deprecated 처리되었으며, Spring Framework 6.2의 `BeanOverrideStrategy.WRAP_BEAN` 기반 `org.springframework.test.context.bean.override.mockito.MockitoSpyBean`이 권장된다. 본 프로젝트는 Spring Boot 3.5.9 환경(build.gradle.kts:14)이며, `@SpyBean` 사용처는 `PiiAuditEnhanceIT` 한 곳(line 16 import, line 74 `@SpyBean PersonalDataAccessLogService`)에 국한된다.

PII-002 RUN 1차의 AC-009-5(AOP fallback HTTP 200) IT는 `Mockito.doThrow(...).when(spy).recordBulk(...)`로 적재 실패 시뮬레이션을 시도했으나, deprecated `@SpyBean`의 시그니처 매칭 한계로 인해 throw가 발생하지 않아 IT가 실패했다. `@MockitoSpyBean` 마이그레이션으로 해소 가능하다.

### 2.4 운영 회귀 검출 갭

PII-002 §5.5 REQ-PII-EMAIL-009 핵심 동작(`recordBulk` 호출, AOP fallback, `target_user_id` 중복 없음)은 PII-002 RUN 1차에서 단위 테스트로 검증되었다. 그러나 IT 레이어에서는 미검증 상태로 남아 있어 다음과 같은 운영 회귀를 검출하지 못할 수 있다.

- AOP advice(@Around)와 `@Async` executor의 상호작용 변경
- Spring Security `JwtPrincipal` SecurityContext 전파 오류 (executor thread)
- `personal_data_access_log` jsonb 컬럼(`accessed_fields`) 직렬화 회귀
- `purpose='ADMIN_USER_LIST'` enum 화이트리스트 미적용

본 SPEC은 위 갭을 IT 레이어로 끌어올려 회귀 검출 가능 범위를 확대한다.

---

## 3. 범위 및 비범위

### 3.1 1차 포함 범위 (P2)

| 항목 | 설명 |
|------|------|
| **REQ-PII-FU-001 — IT-only `auditExecutor` SyncTaskExecutor override** | `@ActiveProfiles("integration")` IT 컨텍스트에서 `auditExecutor` Bean을 `org.springframework.core.task.SyncTaskExecutor`로 override. `@Async("auditExecutor")` 메서드가 호출 스레드에서 동기 실행되도록 강제하여 결정적 검증 보장. 운영 환경(default profile)에는 영향 없음. |
| **REQ-PII-FU-002 — `@SpyBean` → `@MockitoSpyBean` 마이그레이션** | `PiiAuditEnhanceIT`의 `org.springframework.boot.test.mock.mockito.SpyBean` import 제거 후 `org.springframework.test.context.bean.override.mockito.MockitoSpyBean` 적용. Mockito `doThrow` 시그니처 매칭 정상화. |
| **REQ-PII-FU-003 — @Disabled IT 3건 활성화** | `PiiAuditEnhanceIT.findPage_bulkAuditLog_nRows` (AC-FU-003-1, ← PII-002 AC-009-1), `auditInsertFailure_returns200AndDoesNotPropagateError` (AC-FU-003-2, ← PII-002 AC-009-5), `findPage_bulkAudit_distinctTargetUserIds` (AC-FU-003-3, ← PII-002 AC-009-6)에서 `@Disabled` annotation 제거 후 GREEN 통과 검증. |
| **`@TestConfiguration` 신규 클래스** | 권장: `IntegrationAsyncConfig`(또는 유사 명명) — IT-only Bean 선언. 위치는 `backend/src/test/java/kr/co/ircp/cms/config/` 하위 권장. |
| **테스트 코드 한정 변경** | 본 SPEC의 모든 변경은 `backend/src/test/java/` 하위 + `backend/src/test/resources/` 하위에 한정. |

### 3.2 1차 비범위 (후속 SPEC 또는 운영 절차 영역)

| 비범위 항목 | 사유 |
|------------|------|
| **운영 코드(`backend/src/main/java/`) 수정** | 본 SPEC은 PII-002 §5.5 적재 정책을 변경하지 않으며, 동일 행동을 IT로 검증할 수 있도록 테스트 인프라만 정비한다. `AsyncConfig.java`, `UserServiceImpl`, `PersonalDataAccessLogService` 등 운영 코드 git diff = 0이 완료 기준의 일부다. |
| **다른 `@Async` executor (`accessLogExecutor`, `searchLogExecutor`) 영향** | 본 SPEC의 SyncTaskExecutor override는 `auditExecutor` Bean에만 한정한다. 다른 executor의 IT 검증은 별도 후속 SPEC 또는 필요 발생 시점의 인프라 추가로 다룬다. |
| **비동기 행동 자체의 IT 검증 (실제 ThreadPool 동시성 검증)** | SyncTaskExecutor override는 비동기 동작을 결정적으로 가린다. 실제 동시성/timing 검증이 필요할 경우 별도 IT 클래스(또는 Awaitility polling 유지 IT)에서 다룬다. 본 SPEC은 PII-002 §5.5 기능 검증에 한정한다. |
| **다른 `@SpyBean` 사용처 일괄 마이그레이션** | 조사 결과 `PiiAuditEnhanceIT` 외 사용처 없음. 향후 `@SpyBean` 도입 시 표준은 `@MockitoSpyBean`으로 통일하되, 본 SPEC은 현재 사용처 1건에 한정한다. |
| **production AsyncConfig 변경** | 운영 ThreadPoolTaskExecutor 설정(core=2, max=8, queue=500)은 PII-002 §5.5 RUN 1차에서 도입된 그대로 유지한다. |
| **신규 IT 클래스 분리** | 권장은 기존 `PiiAuditEnhanceIT`의 `@Disabled` 제거(Scope Discipline). 분리 IT 클래스 신설은 §6 D3에서 비권장 대안으로 명시한다. |

---

## 4. 데이터 모델 변경

신규 DDL은 **없다**. `personal_data_access_log` 테이블(SPEC-CMS-002 §17.3 REQ-AUTH-018-D)을 그대로 재사용하며, `purpose` enum 화이트리스트(SPEC-CMS-SECURITY-PII-002 §4.1) 또한 그대로 유지한다.

본 SPEC은 데이터 모델 영역에서 **변경 0건**이며, 검증 인프라(테스트 코드)에 한정된다.

---

## 5. EARS 요구사항 (REQ-PII-FU-001 ~ 003)

### 5.1 REQ-PII-FU-001 (비동기 IT 검증 인프라 — Ubiquitous)

The system SHALL provide an IT-only synchronous override for the `auditExecutor` Bean to enable deterministic verification of `@Async("auditExecutor")` flows in `@Transactional` integration tests.

세부 행동:
- `@ActiveProfiles("integration")` 컨텍스트(또는 본 SPEC 신규 `@TestConfiguration` Import 적용 IT)에서 `auditExecutor` Bean 조회 시 `org.springframework.core.task.SyncTaskExecutor` 인스턴스가 반환되어야 한다.
- `@Async("auditExecutor")` 적용 메서드(`PersonalDataAccessLogService.recordBulk` 등) 호출이 IT 호출 스레드에서 즉시 실행되어 동일 IT 메서드 안에서 적재 결과를 SELECT 가능해야 한다.
- 클래스 레벨 `@Transactional` IT에서 적재 row를 commit 없이 검증할 수 있어야 한다(`@Async` REQUIRES_NEW가 동기 분기에서 결정적으로 동작).
- 운영 환경(default Spring profile, 즉 `@ActiveProfiles` 미지정 또는 IT 외 컨텍스트)에서는 본 SPEC이 도입한 override가 적용되지 않아야 하며, 기존 `AsyncConfig.auditExecutor` ThreadPoolTaskExecutor(core=2, max=8, queue=500, CallerRunsPolicy)가 그대로 동작해야 한다.

본 요구사항은 PII-002 §5.5 REQ-PII-EMAIL-009 적재 정책을 변경하지 않으며, 동일 행동을 IT 레이어에서 결정적으로 검증할 수 있도록 인프라만 추가한다.

### 5.2 REQ-PII-FU-002 (`@SpyBean` → `@MockitoSpyBean` 마이그레이션 — Ubiquitous)

The system SHALL migrate all `@SpyBean` usages in the integration test suite to `@MockitoSpyBean` (Spring Framework 6.2 / Spring Boot 3.4+) to align with the supported bean override API.

세부 행동:
- `PiiAuditEnhanceIT`의 import `org.springframework.boot.test.mock.mockito.SpyBean`(line 16)을 `org.springframework.test.context.bean.override.mockito.MockitoSpyBean`으로 교체.
- field annotation `@SpyBean PersonalDataAccessLogService`(line 74)를 `@MockitoSpyBean PersonalDataAccessLogService`으로 교체.
- 마이그레이션 후 `Mockito.doThrow(...).when(spy).recordBulk(...)` 호출의 시그니처 매칭이 정상화되어 throw가 의도대로 발생해야 한다(AC-FU-003-2 검증 가능 조건).
- 본 SPEC 시점의 마이그레이션 범위는 `PiiAuditEnhanceIT` 1개 파일에 한정한다(현재 다른 사용처 없음). 향후 신규 `@SpyBean` 도입 시 표준은 `@MockitoSpyBean`으로 통일한다(project test convention).

### 5.3 REQ-PII-FU-003 (@Disabled IT 3건 활성화 검증 — Event-driven)

When SPEC-CMS-SECURITY-PII-FOLLOWUP-001 RUN 단계가 완료되면, the system SHALL pass `PiiAuditEnhanceIT.findPage_bulkAuditLog_nRows` (AC-FU-003-1), `auditInsertFailure_returns200AndDoesNotPropagateError` (AC-FU-003-2), `findPage_bulkAudit_distinctTargetUserIds` (AC-FU-003-3) without the `@Disabled` annotation.

세부 행동:
- AC-FU-003-1 (← PII-002 AC-009-1) — bulk N건 적재: SyncTaskExecutor override 적용 + 사용자 5명 사전 적재 + ADMIN findPage 호출 → HTTP 200 + audit row 5건 적재(동기 검증, Awaitility 불요).
- AC-FU-003-2 (← PII-002 AC-009-5) — AOP fallback HTTP 200: `@MockitoSpyBean PersonalDataAccessLogService` + `Mockito.doThrow` 적용 → ADMIN findPage 호출 → HTTP 200 정상 응답 + 적재 실패 시뮬레이션 + Micrometer `pii.audit.log.failure.count` 증가 검증.
- AC-FU-003-3 (← PII-002 AC-009-6) — `target_user_id` 중복 없음: SyncTaskExecutor override + 사용자 5명 사전 적재 → ADMIN findPage 호출 → 적재 row의 `target_user_id` distinct 5건 + 중복 0건.
- 모든 IT의 `@Disabled` annotation 제거(완료 기준의 일부).
- 다중 IT 클래스 회귀 0건(`./gradlew integrationTest` GREEN).
- LSP 0 errors(테스트 코드 변경 후 정적 분석 통과).

---

## 6. 결정 포인트 (사용자 검토 필요)

본 SPEC RUN 단계 진입 전, 다음 5건의 결정 포인트에 대한 사용자 응답이 필요하다. 각 항목의 첫 번째 옵션이 권장안이다.

### D1. 비동기 IT 검증 방식

| 옵션 | 설명 | 장단점 |
|------|------|--------|
| **D1-1 (권장): `@TestConfiguration` + IT-only `auditExecutor` SyncTaskExecutor override** | `IntegrationAsyncConfig` 신규 `@TestConfiguration` 클래스에서 `@Bean("auditExecutor")` SyncTaskExecutor 선언. `@ActiveProfiles("integration")` 또는 명시적 `@Import(IntegrationAsyncConfig.class)`로 IT에 적용. | 가장 단순 + 결정적 + 격리 강함. 단점: 비동기 동작 자체(실제 ThreadPool 동시성)는 IT에서 검증 불가 — 별도 단위 테스트로 격리 필요. |
| D1-2: Awaitility polling 유지 + `@Transactional` 제거 + 명시적 cleanup | 실제 비동기 동작을 IT에서 검증하되, 클래스 레벨 `@Transactional`을 제거하고 `@AfterEach`로 적재 row 정리. | 실제 비동기 동작 검증 가능. 단점: 격리 결함 위험(다른 IT 클래스와 DB 상태 간섭) + timing 의존(flaky 가능성) + cleanup 누락 시 운영 데이터 오염. |
| D1-3: D1-1 + D1-2 혼합 | 기능 검증은 SyncTaskExecutor override(D1-1), 별도 IT 클래스에서 timing/concurrency 검증은 Awaitility polling(D1-2). | 양 검증 모두 가능. 단점: IT 클래스 2개 유지 비용 + 본 SPEC 범위 확대. |

### D2. `@SpyBean` → `@MockitoSpyBean` 마이그레이션 범위

| 옵션 | 설명 |
|------|------|
| **D2-1 (권장): `PiiAuditEnhanceIT`만 마이그레이션** | 현재 사용처 1건 한정. 향후 신규 `@SpyBean` 도입 시 표준은 `@MockitoSpyBean`으로 통일. |
| D2-2: 전체 코드베이스 일괄 마이그레이션 | 조사 결과 다른 사용처 없음. Scope Discipline 위반(불필요 변경). 비권장. |

### D3. 테스트 활성화 방식

| 옵션 | 설명 |
|------|------|
| **D3-1 (권장): 기존 `PiiAuditEnhanceIT`의 `@Disabled` 제거 + 인프라 정비** | Scope Discipline 준수. 기존 IT 클래스의 결합도/응집도 유지. |
| D3-2: 새 IT 클래스(`PiiAuditEnhanceAsyncIT`) 분리 + 기존 클래스 유지 | 비동기 영역만 별도 클래스로 분리. 단점: 기존 IT 클래스의 `@Disabled` 잔존(forward reference 미해소). |
| D3-3: AC-009-1/5/6를 단위 테스트로 격하 | 서비스 레벨 단위 테스트로 검증. 단점: PII-002 §5.5 IT 갭 미해소(운영 회귀 검출 갭 잔존). |

### D4. SyncTaskExecutor 설정 위치

| 옵션 | 설명 |
|------|------|
| **D4-1 (권장): `@TestConfiguration` Java class** | `IntegrationAsyncConfig`(또는 유사) Java 클래스 + `@Profile("integration")` 자동 적용 또는 IT 클래스별 `@Import` 명시. 명시성/Type-safety 우수. |
| D4-2: `application-integration.yml` 설정 | YAML에서 직접 Bean override 구성. 단점: Spring Boot의 YAML 기반 Bean override 제약 + 가독성 저하. |
| D4-3: IT 클래스별 `@Import` 명시 | `PiiAuditEnhanceIT` 클래스에 `@Import(IntegrationAsyncConfig.class)` 명시. D4-1과 호환 가능 — 명시적 IT만 SyncTaskExecutor 적용. |

### D5. 비동기 행동 자체의 검증 방식

| 옵션 | 설명 |
|------|------|
| **D5-1 (권장): 단위 테스트(직접 `recordBulk` 호출)에서 행동 검증, IT는 기능만 검증** | PII-002 RUN 1차 단위 테스트 + 본 SPEC IT(SyncTaskExecutor 동기 분기). 실제 ThreadPool 동시성은 운영 환경에서 검증(또는 별도 부하 테스트). |
| D5-2: 별도 IT 클래스(`PiiAuditEnhanceAsyncBehaviorIT`)에서 Awaitility polling으로 검증 | timing/동시성 IT 추가. 단점: 본 SPEC 범위 확대 + flaky 위험. |
| D5-3: 비동기 행동 검증 불요 (현 코드 + 단위 테스트로 충분) | PII-002 §5.5 RUN 1차 단위 테스트로 충분하다는 판단. 추가 IT 신설 안 함. |

권장 조합 요약: **D1-1 + D2-1 + D3-1 + D4-1 + D5-1** — 가장 단순하고 Scope Discipline에 부합하며, PII-002 §5.5 forward reference 3건의 IT 활성화 + `@SpyBean` 단일 사용처 마이그레이션을 최소 변경으로 달성한다.

---

## 7. 위험 및 가정

### 7.1 위험 및 대응

| 위험 ID | 설명 | 대응 |
|--------|------|------|
| **RISK-FU-01** | SyncTaskExecutor override가 비동기 행동(실제 ThreadPool 동시성/CallerRunsPolicy/큐 포화)을 가린다 | D5-1 권장: 단위 테스트에서 비동기 행동 검증, IT는 기능 검증에 한정. PII-002 §5.5 RUN 1차 단위 테스트(현 commit 6aadc45 시점)는 이미 적재 정책을 검증. |
| **RISK-FU-02** | `@MockitoSpyBean`이 Java records 또는 final 클래스와 호환되지 않을 가능성 | `PersonalDataAccessLogService`는 Spring Bean(`@Service` 일반 클래스)이므로 호환 위험 낮음. 마이그레이션 후 즉시 시그니처 매칭 검증(AC-FU-003-2). 호환성 결함 발견 시 `@MockitoBean` + manual stub 대안 검토. |
| **RISK-FU-03** | IT 격리 결함 재발 — 본 SPEC RUN 후에도 다른 IT와 DB 상태 간섭 가능성 | 클래스 레벨 `@Transactional` 유지 + SyncTaskExecutor override로 동기 분기 강제 → 적재 row가 IT 트랜잭션과 함께 rollback. 별도 cleanup 불요. |
| **RISK-FU-04** | `@TestConfiguration` 신규 클래스의 적용 범위 제어 실패(default profile에 누출) | `@Profile("integration")` 명시 + `@ActiveProfiles("integration")` IT에만 적용. 또는 D4-3 `@Import` 명시로 IT 클래스 단위 적용. AC-FU-001-3에서 운영 환경 무영향 검증. |
| **RISK-FU-05** | `@SpyBean` 마이그레이션 시 다른 IT(현재 미발견)에 부수 영향 | 사전 조사: `grep -r "SpyBean" backend/src/test/` 사용처 1건만 발견됨. RUN Step 2 진입 전 재조사 필수. |

### 7.2 SPEC-PII-002 통합 노트

본 SPEC은 PII-002 §5.5 REQ-PII-EMAIL-009 적재 정책을 변경하지 않는다. RUN 1차(commit 6aadc45)에서 적용된 다음 행동은 본 SPEC 시점 그대로 유지된다.

- `UserServiceImpl.findPage(actor)` 결과 N건 일괄 적재(`recordBulk` @Async)
- 본인 row 제외, HMAC lookup-only 제외, `/me` 자기조회 제외
- AOP fallback: INSERT 실패 시 user-facing 200 + ERROR 로그 + Micrometer `pii.audit.log.failure.count` counter

본 SPEC은 위 행동을 변경하지 않으며, IT 레이어에서 결정적으로 검증할 수 있도록 인프라만 정비한다.

### 7.3 가정

- **ASSUM-FU-01**: PII-002 §5.5 REQ-PII-EMAIL-009 적재 정책은 본 SPEC RUN 단계에서 변경되지 않는다(운영 코드 git diff = 0).
- **ASSUM-FU-02**: Spring Boot 3.5.9 + Spring Framework 6.2 환경이 유지된다(`build.gradle.kts:14` 변경 없음).
- **ASSUM-FU-03**: `application-integration.yml` 또는 IT 베이스 클래스(`AbstractIntegrationTest`)의 `@ActiveProfiles("integration")` 가정이 유효하다.
- **ASSUM-FU-04**: `awaitility:4.2.2` 의존성(이미 추가됨)은 AC-FU-003-1/3의 동기 분기 검증에서 사용하지 않는다(본 SPEC RUN 후 polling 호출은 제거 또는 사용 안 함). 단, D5-2 채택 시 별도 IT 클래스에서 재사용 가능.
- **ASSUM-FU-05**: `JwtTestAuth` 헬퍼와 `AbstractIntegrationTest`(PII-001 RUN에서 도입된 PII 키 환경변수 자동 주입)는 본 SPEC에서 변경하지 않는다.
- **ASSUM-FU-06**: `org.springframework.test.context.bean.override.mockito.MockitoSpyBean`이 Spring Boot 3.5.9 환경에서 정상 import 가능하다(Spring Framework 6.2 표준). 호환성 문제 발견 시 RISK-FU-02 대응 절차로 fallback.

---

## 8. PIPA 컴플라이언스 매핑

본 SPEC은 PIPA 제29조 안전성 확보 조치 의무에 직접 신규 적재되는 보안 통제를 추가하지 않는다(테스트 인프라 SPEC). 다만 PII-002 §5.5 REQ-PII-EMAIL-009(접속 기록 보관 의무)의 IT 검증 갭을 회수함으로써 다음과 같이 운영 회귀 검출 능력을 강화한다.

| PIPA 조항 | PII-002 매핑 | 본 SPEC 기여 |
|----------|---------------|---------------|
| 제29조 접속 기록 보관 | REQ-PII-EMAIL-009(`personal_data_access_log` 일괄 적재) | IT 레이어 회귀 검출 — `recordBulk` 호출 / AOP fallback / `target_user_id` 중복 없음 검증을 IT로 승급 |
| 제29조 접근 통제 | REQ-PII-EMAIL-007/008(PII-002 RUN 1차 적용) | 본 SPEC 무관 — 변경 없음 |

본 SPEC 적용 후 `personal_data_access_log` 적재 회귀가 IT 단계에서 검출 가능해지므로, 운영 배포 직전 회귀 검출 신뢰도가 상승한다.

---

## 9. RUN Step 분해 (Step 1 ~ 3)

본 SPEC RUN 단계는 3개 Step으로 분해되며, 각 Step은 독립적으로 검증 가능하다. 각 Step 완료 후 `./gradlew integrationTest` GREEN 확인이 진입 조건이다.

### Step 1: SyncTaskExecutor IT-only override (REQ-PII-FU-001)

목적: `@ActiveProfiles("integration")` IT 컨텍스트에서 `auditExecutor` Bean을 SyncTaskExecutor로 override.

작업 단위:
1. `backend/src/test/java/kr/co/ircp/cms/config/IntegrationAsyncConfig.java`(또는 동등 명명) 신규 생성.
   - `@TestConfiguration` 또는 `@Configuration` + `@Profile("integration")` 적용.
   - `@Bean("auditExecutor")` `SyncTaskExecutor` 선언.
   - `@Primary` 또는 `@Profile("integration")` 분기로 default profile에는 영향 없도록 보장.
2. `PiiAuditEnhanceIT`에 `@Import(IntegrationAsyncConfig.class)` 추가(D4-3 명시 적용 옵션 채택 시) 또는 `@ActiveProfiles("integration")`만으로 자동 적용 확인(D4-1).
3. AC-FU-001-1 (Bean 조회 시 SyncTaskExecutor 반환), AC-FU-001-3 (default profile 무영향) 검증.

검증:
- `./gradlew integrationTest --tests PiiAuditEnhanceIT` GREEN(단, Step 3까지는 `@Disabled` 잔존 가능 — Step 1 단독 검증은 기존 활성 IT의 회귀 0건).
- 다른 IT 클래스(예: `UserAdminControllerIT` 등 PII-002 §6 영향 분석에 명시된 IT) 회귀 0건.
- LSP 0 errors.

### Step 2: `@SpyBean` → `@MockitoSpyBean` 마이그레이션 (REQ-PII-FU-002)

목적: `PiiAuditEnhanceIT`의 deprecated `@SpyBean` import를 Spring Framework 6.2 표준 `@MockitoSpyBean`으로 교체.

작업 단위:
1. `backend/src/test/java/kr/co/ircp/cms/domain/security/pii/PiiAuditEnhanceIT.java`:
   - line 16 import 변경: `org.springframework.boot.test.mock.mockito.SpyBean` → `org.springframework.test.context.bean.override.mockito.MockitoSpyBean`.
   - line 74 annotation 변경: `@SpyBean` → `@MockitoSpyBean`.
2. RUN Step 2 진입 전 `grep -r "SpyBean" backend/src/test/`로 다른 사용처 재조사 — 발견 시 본 SPEC 범위 외(별도 SPEC 또는 사용자 결정 요청).
3. AC-FU-002-1 (import + annotation 변경 적용), AC-FU-002-2 (Mockito `doThrow` 시그니처 매칭 정상 동작 — Step 3에서 확인) 검증.

검증:
- `./gradlew integrationTest --tests PiiAuditEnhanceIT` 컴파일 GREEN(deprecation 경고 0건).
- LSP 0 errors.

### Step 3: @Disabled 제거 + IT GREEN (REQ-PII-FU-003)

목적: `PiiAuditEnhanceIT`의 격리된 IT 3건에서 `@Disabled` annotation 제거 + GREEN 통과.

작업 단위:
1. `PiiAuditEnhanceIT` line 96-101, line 188-191, line 217-219의 `@Disabled` annotation 제거.
2. AC-FU-003-1 / AC-FU-003-2 / AC-FU-003-3 검증:
   - `findPage_bulkAuditLog_nRows()` (AC-FU-003-1, ← PII-002 AC-009-1) — 사용자 5명 사전 적재 + ADMIN findPage → HTTP 200 + audit row 5건 동기 검증(Awaitility polling 호출 제거 또는 미사용).
   - `auditInsertFailure_returns200AndDoesNotPropagateError()` (AC-FU-003-2, ← PII-002 AC-009-5) — `@MockitoSpyBean` `Mockito.doThrow` → ADMIN findPage → HTTP 200 + Micrometer counter 증가.
   - `findPage_bulkAudit_distinctTargetUserIds()` (AC-FU-003-3, ← PII-002 AC-009-6) — `target_user_id` distinct 5건 + 중복 0건.
3. (선택) PII-002 RUN 1차에서 도입된 Awaitility polling 코드(line 112-114, 231-239)를 동기 분기에서 무력화 — `Awaitility.await().atMost(...)` 호출은 SyncTaskExecutor 동기 실행 후에는 즉시 통과하지만, 가독성 향상을 위해 polling 제거 권장.

검증:
- `./gradlew integrationTest` 전체 GREEN — `@Disabled` 0건.
- `PiiAuditEnhanceIT` 다른 활성 IT 회귀 0건.
- 다중 IT 클래스 회귀 0건.
- 운영 코드 git diff = 0(완료 기준의 일부, AC-FU-001-3 동등 검증).
- LSP 0 errors.

### Step 의존성 요약

```
Step 1 (SyncTaskExecutor override) → Step 2 (@MockitoSpyBean 마이그레이션) → Step 3 (@Disabled 제거 + IT GREEN)
```

Step 1은 AC-FU-003-1 / AC-FU-003-3의 동기 분기 검증의 전제 조건. Step 2는 AC-FU-003-2의 Mockito `doThrow` 시그니처 매칭의 전제 조건. Step 3은 본 SPEC 완료 기준의 종착점이다.

---

## 10. 후속 SPEC 안내

본 SPEC은 SPEC-CMS-SECURITY-PII 시리즈의 follow-up 종착점이다. 본 SPEC RUN 1차 완료 후 추가 follow-up SPEC 발의 예정 없음.

다만 본 SPEC §3.2 비범위 항목 중 운영 인프라 의사결정에 의존하는 사항(KMS 키 회전, 다른 PII 컬럼 암호화, 백업 마스킹, Logback 필터 마스킹)은 SPEC-CMS-SECURITY-PII-002 §3.2에 이미 분리 명시되어 있으며, 본 SPEC과 무관하다.

비동기 행동 자체의 timing/동시성 IT 검증이 운영 회귀 사유로 필요해질 경우, D5-2 옵션의 별도 IT 클래스(`PiiAuditEnhanceAsyncBehaviorIT`)를 신설하는 후속 SPEC을 발의할 수 있다(현 시점 발의 불요).

---

## 11. 변경 이력

| 버전 | 일자 | 변경 내용 | 작성자 |
|------|------|----------|--------|
| v0.1 | 2026-05-08 | 초안 작성. SPEC-CMS-SECURITY-PII-002 RUN 1차의 `@Disabled` 3건 forward reference 회수를 위한 P2 테스트 인프라 SPEC. REQ-PII-FU-001 (SyncTaskExecutor IT override), REQ-PII-FU-002 (`@SpyBean` → `@MockitoSpyBean` 마이그레이션), REQ-PII-FU-003 (@Disabled 3건 활성화) 정의. 결정 포인트 D1~D5(권장 조합: D1-1 + D2-1 + D3-1 + D4-1 + D5-1). RUN Step 1~3 분해. 운영 코드 변경 0건. | manager-spec |
| v0.3 | 2026-05-13 | @Disabled 3건 활성화 GREEN (REQ-PII-FU-001/002/003) IT 검증 완료. Implemented → Tested. | MoAI orchestrator |
| v0.2 | 2026-05-08 | RUN 1차 완료 — Step 1~3 적용 (commit `5fe440b`). IntegrationAsyncConfig 신규 (59줄, SyncTaskExecutor + @Profile("integration") + @Primary) + `@SpyBean` → `@MockitoSpyBean` 마이그레이션 + `@Import(IntegrationAsyncConfig.class)` 명시 보강 + @Disabled 3건 제거 + Awaitility polling 정리. 사용자 결정 D1-1/D2-1/D3-1/D4-1/D5-1 전 채택. 운영 코드 변경 0건. | manager-docs |
