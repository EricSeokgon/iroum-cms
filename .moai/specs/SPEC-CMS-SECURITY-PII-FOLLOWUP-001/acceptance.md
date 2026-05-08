# SPEC-CMS-SECURITY-PII-FOLLOWUP-001 Acceptance Criteria v0.1

본 문서는 SPEC-CMS-SECURITY-PII-FOLLOWUP-001 §5(EARS 요구사항)에 정의된 REQ-PII-FU-001 ~ 003에 대한 상세 수락 기준이다. 모든 AC는 Given-When-Then 형식이며, 각 AC는 RUN Step 완료 시점에서 결정적으로 검증 가능하다.

본 SPEC의 모든 AC는 PII-002 §5.5 REQ-PII-EMAIL-009 적재 정책의 운영 코드 변경 없이 검증되어야 한다 — 운영 코드 git diff = 0이 완료 기준의 일부다.

---

## A. REQ-PII-FU-001 — 비동기 IT 검증 인프라

### AC-FU-001-1 — `auditExecutor` IT-only override 적용

- **Given**: `@ActiveProfiles("integration")` 적용된 IT 컨텍스트, `IntegrationAsyncConfig`(또는 동등 명명) `@TestConfiguration` 클래스 활성.
- **When**: Spring `ApplicationContext`에서 Bean name `auditExecutor` 또는 Bean type `org.springframework.core.task.TaskExecutor` 조회.
- **Then**:
  - 반환되는 인스턴스는 `org.springframework.core.task.SyncTaskExecutor` 타입이어야 한다.
  - `instanceof ThreadPoolTaskExecutor`는 false여야 한다(default profile의 `AsyncConfig.auditExecutor`와 분기됨).
- **검증 방법**: IT 클래스에 `@Autowired @Qualifier("auditExecutor") TaskExecutor executor` 주입 후 `assertThat(executor).isInstanceOf(SyncTaskExecutor.class)`.

### AC-FU-001-2 — IT에서 `recordBulk` 동기 실행

- **Given**: SyncTaskExecutor override 적용된 IT 컨텍스트, ADMIN 권한 `JwtPrincipal` SecurityContext 주입(`JwtTestAuth.jwtAuth(adminId, "admin", "ADMIN")`), 사용자 5명(본인 제외) 사전 적재.
- **When**: ADMIN이 `GET /api/v1/users` 호출 → `UserServiceImpl.findPage(actor)` 실행 → `PersonalDataAccessLogService.recordBulk(@Async("auditExecutor"))` 호출.
- **Then**:
  - `recordBulk` 호출이 IT 호출 스레드(`Thread.currentThread()`)에서 즉시 완료되어야 한다(별도 ThreadPool 스레드로 dispatch되지 않음).
  - 동일 IT 메서드의 `@Transactional` 컨텍스트 안에서 `personal_data_access_log` 테이블의 신규 적재 row를 SELECT 가능해야 한다(commit 없이).
  - Awaitility polling 호출 없이도 적재 결과가 결정적으로 가시화되어야 한다.
- **검증 방법**: `personalDataAccessLogRepository.findByActorUserId(adminId)` 또는 동등 SELECT 쿼리로 신규 row 5건 확인.

### AC-FU-001-3 — 운영 환경 무영향

- **Given**: default Spring profile (운영 환경, `@ActiveProfiles` 미지정 또는 `integration` 외 profile).
- **When**: Spring `ApplicationContext`에서 Bean name `auditExecutor` 조회.
- **Then**:
  - 반환되는 인스턴스는 `org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor` 타입이어야 한다(`AsyncConfig.auditExecutor`).
  - core=2, max=8, queue=500, RejectedExecutionHandler=`CallerRunsPolicy` 설정이 그대로 유지되어야 한다.
  - 본 SPEC이 도입한 `IntegrationAsyncConfig`의 SyncTaskExecutor가 default profile에 적용되지 않아야 한다.
- **검증 방법**: 운영 코드 git diff = 0 + 운영 빌드(`./gradlew bootJar`) GREEN + 기존 비-IT 테스트 회귀 0건. 별도 단위 테스트(`AsyncConfigTest`가 존재할 경우) GREEN 유지.

---

## B. REQ-PII-FU-002 — `@SpyBean` → `@MockitoSpyBean` 마이그레이션

### AC-FU-002-1 — `@MockitoSpyBean` import + annotation 적용

- **Given**: `PiiAuditEnhanceIT` 클래스(파일 경로: `backend/src/test/java/kr/co/ircp/cms/domain/security/pii/PiiAuditEnhanceIT.java`).
- **When**: import 문 + field annotation 마이그레이션 적용.
- **Then**:
  - import 변경: `org.springframework.boot.test.mock.mockito.SpyBean`(line 16) 제거 → `org.springframework.test.context.bean.override.mockito.MockitoSpyBean` 추가.
  - field annotation 변경(line 74): `@SpyBean PersonalDataAccessLogService` → `@MockitoSpyBean PersonalDataAccessLogService`.
  - 컴파일 GREEN(deprecation 경고 0건).
  - 다른 `@SpyBean` 사용처 0건 (`grep -r "SpyBean" backend/src/test/` 매칭 0건 — 단, `@MockitoSpyBean` 매칭은 정상).
- **검증 방법**: `./gradlew compileTestJava` GREEN + `grep -rE "import org\.springframework\.boot\.test\.mock\.mockito\.SpyBean" backend/src/test/` 0건.

### AC-FU-002-2 — Mockito `doThrow` 시그니처 매칭 정상 동작

- **Given**: `@MockitoSpyBean PersonalDataAccessLogService spy` 주입된 `PiiAuditEnhanceIT`, `Mockito.doThrow(new DataAccessException("simulated INSERT failure"){}).when(spy).recordBulk(any())` 적용.
- **When**: ADMIN 컨텍스트에서 `UserServiceImpl.findPage(actor)`가 트리거되는 IT 시나리오 실행 → AOP advice 또는 `recordBulk` 직접 호출 시점.
- **Then**:
  - `recordBulk(...)` 호출 시점에 stubbed `DataAccessException`이 throw되어야 한다(시그니처 매칭 정상화).
  - `@SpyBean` 환경에서 발생한 시그니처 매칭 실패(throw 미발생)가 재발하지 않아야 한다.
  - throw된 예외가 PII-002 §5.5 AOP fallback 정책으로 흡수되어야 한다(user-facing 200 + ERROR 로그 + Micrometer counter 증가) — AC-FU-003-2에서 통합 검증.
- **검증 방법**: AC-FU-003-2 IT 실행 결과로 통합 검증.

---

## C. REQ-PII-FU-003 — @Disabled IT 3건 활성화

### AC-FU-003-1 (← PII-002 AC-009-1) — `findPage_bulkAuditLog_nRows` 활성화

- **Given**:
  - SyncTaskExecutor override 적용 IT 컨텍스트(AC-FU-001-1 충족).
  - 사용자 5명(ADMIN 본인 제외) 사전 적재 — `userRepository.saveAll(...)` 또는 동등.
  - ADMIN `JwtPrincipal` SecurityContext 주입.
- **When**: `GET /api/v1/users` (ADMIN findPage) MockMvc 호출.
- **Then**:
  - HTTP 200 응답.
  - 응답 page.content size = 5(본인 row 제외 정책 적용).
  - `personal_data_access_log` 테이블에 신규 row 5건 적재되어야 한다.
  - 적재 row의 `purpose='ADMIN_USER_LIST'`, `actor_user_id=ADMIN id`, `accessed_fields=["email"]`(jsonb 배열).
  - Awaitility polling 호출 없음(또는 호출되어도 즉시 통과 — 동기 분기로 사전 완료).
  - `@Disabled` annotation 제거 후 GREEN.
- **검증 방법**: MockMvc + `personalDataAccessLogRepository` SELECT + `assertThat(rows).hasSize(5)`.

### AC-FU-003-2 (← PII-002 AC-009-5) — `auditInsertFailure_returns200AndDoesNotPropagateError` 활성화

- **Given**:
  - `@MockitoSpyBean PersonalDataAccessLogService spy` 마이그레이션 완료(AC-FU-002-1, AC-FU-002-2 충족).
  - SyncTaskExecutor override 적용(AC-FU-001-1 충족) — AOP fallback 정책의 동기 분기 검증.
  - `Mockito.doThrow(new DataAccessException("simulated INSERT failure"){}).when(spy).recordBulk(any())`.
  - 사용자 5명 사전 적재, ADMIN `JwtPrincipal` 주입.
- **When**: `GET /api/v1/users` (ADMIN findPage) MockMvc 호출 → `recordBulk` 호출 시점에 stubbed throw 발생.
- **Then**:
  - HTTP 200 응답(AOP fallback 정책 — user-facing 에러 미전파).
  - 응답 본문은 정상 user list(throw 발생 사실이 사용자에게 노출되지 않음).
  - ERROR 로그 1건 이상 기록(AOP advice의 fallback 로그).
  - Micrometer counter `pii.audit.log.failure.count` 1 이상 증가.
  - `personal_data_access_log` 테이블에 신규 row 0건(throw로 인해 적재 실패).
  - `@Disabled` annotation 제거 후 GREEN.
- **검증 방법**: MockMvc + `meterRegistry.counter("pii.audit.log.failure.count").count()` 증분 확인 + repository SELECT 0건 확인.

### AC-FU-003-3 (← PII-002 AC-009-6) — `findPage_bulkAudit_distinctTargetUserIds` 활성화

- **Given**:
  - SyncTaskExecutor override 적용(AC-FU-001-1 충족).
  - 사용자 5명(ADMIN 본인 제외) 사전 적재 — 5명의 `user_id`는 모두 distinct.
  - ADMIN `JwtPrincipal` 주입.
- **When**: `GET /api/v1/users` (ADMIN findPage) MockMvc 호출.
- **Then**:
  - HTTP 200 응답.
  - `personal_data_access_log` 신규 row 5건의 `target_user_id` 컬럼이 모두 distinct(중복 0건).
  - `target_user_id` 집합과 사전 적재 5명의 `user_id` 집합이 동일.
  - 본인 ADMIN의 `user_id`는 `target_user_id`로 적재되지 않음(본인 row 제외 정책).
  - `@Disabled` annotation 제거 후 GREEN.
- **검증 방법**: MockMvc + `SELECT DISTINCT target_user_id FROM personal_data_access_log WHERE actor_user_id = ?` 결과 5건 + 사전 적재 user_id 집합과 정확히 일치.

---

## D. Quality Gates (완료 기준)

### D.1 모든 IT GREEN — `@Disabled` 0건

- `PiiAuditEnhanceIT` 안의 모든 `@org.junit.jupiter.api.Disabled` annotation 제거되어야 한다.
- `grep -rE "@Disabled" backend/src/test/java/kr/co/ircp/cms/domain/security/pii/PiiAuditEnhanceIT.java` 매칭 0건.
- `./gradlew integrationTest --tests PiiAuditEnhanceIT` GREEN.

### D.2 다중 IT 클래스 회귀 0건

- `./gradlew integrationTest` 전체 실행 시 GREEN.
- 본 SPEC RUN 진입 시점(commit 6aadc45) 대비 활성 IT의 fail 0건.
- LSP 0 errors(테스트 코드 변경 후 정적 분석 통과).

### D.3 운영 코드 변경 0건

- `git diff main -- backend/src/main/` 결과 0 line.
- `git diff main -- 'backend/src/test/**/*.kt'` 등 운영 build 영역 외부 변경 0건.
- 본 SPEC의 모든 변경은 `backend/src/test/java/` + `backend/src/test/resources/` 한정.
- 변경 허용 파일 명시:
  - 신규: `backend/src/test/java/kr/co/ircp/cms/config/IntegrationAsyncConfig.java`(또는 동등 명명).
  - 수정: `backend/src/test/java/kr/co/ircp/cms/domain/security/pii/PiiAuditEnhanceIT.java`(import + annotation + `@Disabled` 제거 + 선택적 polling 제거).

### D.4 LSP 0 errors

- `./gradlew compileTestJava` GREEN(deprecation 경고 0건 — `@SpyBean` 마이그레이션 완료).
- IDE LSP 진단 0 errors.

### D.5 운영 환경 무영향 회귀

- `./gradlew bootJar` GREEN(운영 build 회귀 0건).
- AC-FU-001-3 만족 — default profile에서 `auditExecutor`가 `ThreadPoolTaskExecutor`(core=2, max=8, queue=500)로 동작.

---

## E. Definition of Done

본 SPEC RUN 단계 완료 기준은 다음 모든 항목 충족이다.

- [ ] AC-FU-001-1, AC-FU-001-2, AC-FU-001-3 모두 GREEN(REQ-PII-FU-001 완료).
- [ ] AC-FU-002-1, AC-FU-002-2 모두 GREEN(REQ-PII-FU-002 완료).
- [ ] AC-FU-003-1, AC-FU-003-2, AC-FU-003-3 모두 GREEN, `@Disabled` 0건(REQ-PII-FU-003 완료).
- [ ] D.1 ~ D.5 Quality Gates 충족.
- [ ] PII-002 RUN 1차의 forward reference(@Disabled 메시지의 "SPEC-CMS-SECURITY-PII-FOLLOWUP-001" 인용) 회수 — `@Disabled` annotation이 모두 제거되었으므로 메시지 자체도 함께 제거됨.
- [ ] 운영 코드 git diff = 0(D.3).
- [ ] commit 메시지에 본 SPEC ID 인용(`SPEC-CMS-SECURITY-PII-FOLLOWUP-001 Step N — ...` 형식, PII-002 RUN 1차 commit 패턴 일관).

본 SPEC은 PII-002 RUN 1차의 검증 갭을 회수하는 follow-up SPEC이며, 본 SPEC RUN 1차 완료 후 추가 follow-up SPEC 발의는 예정되어 있지 않다(SPEC §10 후속 SPEC 안내 참조).
