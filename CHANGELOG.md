# Changelog

모든 주요 변경 사항이 이 파일에 기록됩니다.

형식은 [Keep a Changelog](https://keepachangelog.com/ko/1.1.0/) 1.1.0 표준을 따르며,
이 프로젝트는 [Semantic Versioning](https://semver.org/spec/v2.0.0.html)을 준수합니다.

---

## [Unreleased]

### Added

- **PiiKeyVault 인터페이스 + LocalEnvPiiKeyVault 구현**
  - `PiiKeyVault` 인터페이스: `getActiveKey()`, `getKeyByVersion(int)`, `getHmacKey()` 메서드 + `ActiveKey` record 정의
  - `LocalEnvPiiKeyVault`: 환경변수(`PII_EMAIL_KEY_V1`, `PII_EMAIL_HMAC_KEY`) base64 디코딩 + 32-byte 키 길이 검증
  - Spring profile `prod` + `LocalEnvPiiKeyVault` 조합 부팅 거부 가드 (운영 환경 안전성)
  - 단위 테스트 14 GREEN (키 로드 성공/실패, 길이 검증, 누락 환경변수 처리)
  (SPEC-CMS-SECURITY-PII-001 Step 1, commit 1d4ae61)

- **AesGcmEmailEncryptionService + HMAC-SHA256 구현**
  - `AesGcmEmailEncryptionService`: AES-256-GCM 암호화/복호화 (12-byte IV, 16-byte auth tag 분리)
  - `SecureRandom` 기반 12-byte IV 생성 (IV 재사용 방지)
  - HMAC-SHA256 lookup 키 계산 (`HmacSHA256`, 암호화 키와 분리된 전용 키)
  - 복호화 실패(`AEADBadTagException`) 시 `audit_log` CRITICAL 적재 + `PiiIntegrityException` 전파
  - Micrometer 메트릭: `pii.email.encrypt.count`, `pii.email.decrypt.count`, `pii.email.decrypt.failure.count`
  - 단위 테스트 17 GREEN (encrypt/decrypt roundtrip, null 처리, tag mismatch, IV 신선도, 동시성 등)
  (SPEC-CMS-SECURITY-PII-001 Step 2, commit 0a6b14e)

- **V24 마이그레이션 — PII 암호화 컬럼 + HMAC lookup 인덱스**
  - `V24__pii_encryption_email.sql`: 5개 신규 컬럼 추가
    - `email_encrypted BYTEA`: AES-256-GCM 암호문
    - `email_iv BYTEA`: GCM IV (12 bytes)
    - `email_tag BYTEA`: GCM auth tag (16 bytes)
    - `email_hmac VARCHAR(64)`: HMAC-SHA256(hmacKey, normalizedEmail) — lookup 키
    - `email_key_version SMALLINT NOT NULL DEFAULT 1`: 점진적 키 회전 지원
  - `idx_users_email_hmac` UNIQUE 부분 인덱스 생성 (HMAC lookup 성능 + UNIQUE 제약)
  - `data_dictionary` 5개 row 시드 (SPEC-CMS-009 데이터 분류 통합)
  - 기존 `email`, `email_hash` 컬럼 deprecated 주석 처리 (V25에서 DROP 예정)
  (SPEC-CMS-SECURITY-PII-001 Step 3, commit e432d53)

- **UserMapper.findByEmailHmac 신규 쿼리**
  - `UserMapper.xml`에 `findByEmailHmac` 쿼리 추가 (HMAC lookup 전용, REQ-PII-EMAIL-006)
  - `UserMapper.java` 인터페이스 메서드 추가: `Optional<User> findByEmailHmac(String emailHmac)`
  (SPEC-CMS-SECURITY-PII-001 Step 4, commit 29878b9)

- **PiiEmailIntegrationTest 4 GREEN**
  - Testcontainers + PostgreSQL 16 기반 통합 테스트 4건
    1. 신규 사용자 생성 시 email 암호화 저장 검증
    2. `findByEmailHmac`으로 HMAC lookup 정상 동작 검증
    3. 복호화 roundtrip 정확성 검증
    4. UNIQUE 인덱스 중복 삽입 차단 검증
  (SPEC-CMS-SECURITY-PII-001 Step 4, commit 29878b9)

- **AbstractIntegrationTest PII 키 주입**
  - `AbstractIntegrationTest` 베이스 클래스: `PII_EMAIL_KEY_V1`, `PII_EMAIL_HMAC_KEY` 더미 키 환경변수 자동 주입
  - SpringBootTest 컨텍스트 로드 시 `LocalEnvPiiKeyVault` 누락 키 예외 방지
  (SPEC-CMS-SECURITY-PII-001 Step 4, commit 29878b9)

- **NoEmailWildcardValidator + AdminEmailPartialSearchException — admin email partial 검색 차단 (REQ-PII-EMAIL-007)**
  - `NoEmailWildcardValidator`: RFC 5321 valid email + 와일드카드(`*`, `?`, `%`, `_`) 부정 문자 클래스 거부
  - `AdminEmailPartialSearchException`: 400 ADMIN_EMAIL_PARTIAL_FORBIDDEN 전용 예외
  - `@NoEmailWildcard` Bean Validation annotation
  - `UserController` `@Validated` + 파라미터 적용
  - `GlobalExceptionHandler` 400 핸들러 + ConstraintViolationException 핸들러
  - 통합 테스트 11/11 GREEN (PiiEmailAdminSearchIT — 와일드카드 4종 + 정상 + 정규화 + 권한)
  - 사용자 결정: email 빈 문자열은 무시(전체 검색 허용)
  (SPEC-CMS-SECURITY-PII-002 Step 1, commit 3a8be0f)

- **EmailMaskSerializer — API 응답 email 마스킹 (REQ-PII-EMAIL-008)**
  - Jackson `JsonSerializer<String>` + SecurityContext 분기 (ADMIN/본인 평문, 그 외 마스킹)
  - 1자=`*`, 2자=`**`, 3자+=첫CP+`***`+마지막CP, 코드 포인트 단위 (IDN 안전)
  - 사용자 결정: 2자 local-part 마스킹은 `**@e***.com` (SPEC §5.4 원문 유지)
  - `UserSummary`, `UserDetail` `@JsonSerialize(using = EmailMaskSerializer.class)` 적용
  - Java record 호환 검증
  - 통합 테스트 8/8 GREEN (PiiEmailMaskIT — 1/2/3+자, IDN, 이모지, ADMIN/본인 분기)
  (SPEC-CMS-SECURITY-PII-002 Step 2, commit fbedd8c)

- **PII 접근 감사 보강 — recordBulk @Async + Micrometer (REQ-PII-EMAIL-009)**
  - `PersonalDataAccessLogServiceImpl.recordBulk(viewerId, viewerRole, targetUserIds, fields, purpose)` `@Async("auditExecutor")` 비동기 일괄 INSERT
  - `MeterRegistry` 주입 + `pii.audit.log.failure.count` Micrometer counter
  - `UserServiceImpl.findPage(actor)` 본인 제외 + `recordBulk` 호출
  - `PersonalDataAccessPurpose.ADMIN_EMAIL_LOOKUP` enum 추가
  - 사용자 결정: AOP fallback 허용 + ERROR 로그 + Micrometer counter
  - 통합 테스트 3/6 GREEN + 3 @Disabled (AC-009-1, 5, 6 — 비동기 검증 인프라 follow-up SPEC-CMS-SECURITY-PII-FOLLOWUP-001로 추적)
  (SPEC-CMS-SECURITY-PII-002 Step 3, commit 04b9fe3)

- **PiiEmailMaskArchTest — ArchUnit 강제 (UserSummary/UserDetail email @JsonSerialize)**
  - `archunit-junit5:1.3.0` 의존성 추가
  - 5 ArchUnit 케이스: UserSummary/UserDetail email 필드 `@JsonSerialize(using = EmailMaskSerializer.class)` 누락 방지 + Architecture safety net
  - 신규 DTO 추가 시 마스킹 누락 자동 차단
  (SPEC-CMS-SECURITY-PII-002 Step 4, commit 0b3d05e)

- **JwtTestAuth utility + Awaitility 의존성 (테스트 인프라)**
  - `JwtTestAuth`: `JwtPrincipal` record를 SecurityContext에 주입하는 IT 인증 헬퍼 (50줄)
  - `awaitility:4.2.2` 의존성 추가 (비동기 검증용 폴링)
  - 다중 IT 클래스 회귀 BUILD SUCCESSFUL (회귀 0건)
  (SPEC-CMS-SECURITY-PII-002 Step 4, commit 0b3d05e)

- **IntegrationAsyncConfig — IT 전용 비동기 실행기 override (REQ-PII-FU-001)**
  - `@TestConfiguration` + `@Profile("integration")` + `@Primary` 조합
  - `@Bean(name="auditExecutor")` SyncTaskExecutor 반환 — `@Async("auditExecutor")` 호출이 호출 스레드에서 동기 완료
  - 운영 `AsyncConfig.auditExecutor()` ThreadPoolTaskExecutor를 IT profile 한정 override (default profile 무영향)
  - `@MX:NOTE` + `@MX:SPEC` 적용
  (SPEC-CMS-SECURITY-PII-FOLLOWUP-001 Step 1, commit `5fe440b`)

- **@MockitoSpyBean 마이그레이션 — Spring Framework 6.2 표준 적용 (REQ-PII-FU-002)**
  - `org.springframework.boot.test.mock.mockito.SpyBean` (deprecated) → `org.springframework.test.context.bean.override.mockito.MockitoSpyBean`
  - `PiiAuditEnhanceIT` `@SpyBean` → `@MockitoSpyBean` (사용처 단 1곳, Scope Discipline)
  - `recordBulk(long, String, List, Set, PersonalDataAccessPurpose)` 5-arg matcher 시그니처 매칭 한계 해소
  (SPEC-CMS-SECURITY-PII-FOLLOWUP-001 Step 2, commit `5fe440b`)

- **PiiAuditEnhanceIT @Disabled 3건 활성화 (REQ-PII-FU-003)**
  - `findPage_bulkAuditLog_nRows` (AC-FU-003-1, ← PII-002 AC-009-1)
  - `auditInsertFailure_returns200AndDoesNotPropagateError` (AC-FU-003-2, ← PII-002 AC-009-5)
  - `findPage_bulkAudit_distinctTargetUserIds` (AC-FU-003-3, ← PII-002 AC-009-6)
  - PII-002 RUN 1차에서 forward reference로 격리되어 있던 IT 3건 forward reference 완전 회수
  (SPEC-CMS-SECURITY-PII-FOLLOWUP-001 Step 3, commit `5fe440b`)

### Changed

- **User 엔티티 5 PII 필드 추가**
  - `User.java`: `emailEncrypted`, `emailIv`, `emailTag`, `emailHmac`, `emailKeyVersion` 필드 추가
  - Lombok `@NoArgsConstructor` + `@AllArgsConstructor` 파라미터 정합성 강화
  (SPEC-CMS-SECURITY-PII-001 Step 4, commit 29878b9)

- **UserServiceImpl 암호화 경로 적용**
  - `UserServiceImpl.create()`: email 암호화 + HMAC 계산 후 저장 경로 적용
  - `UserServiceImpl.update()`: email 변경 시 재암호화 + 신규 HMAC 갱신
  (SPEC-CMS-SECURITY-PII-001 Step 4, commit 29878b9)

- **MigrationOrderIT V24 포함**
  - `MigrationOrderIT`: V17→V23 범위에서 V17→V24 범위로 확장
  - V24 마이그레이션 순서 및 체크섬 검증 포함
  (SPEC-CMS-SECURITY-PII-001 Step 3, commit e432d53)

- **UserController email 파라미터 검증 가드 적용**
  - `@Validated` 컨트롤러 + `@NoEmailWildcard email` 파라미터
  (SPEC-CMS-SECURITY-PII-002 Step 1, commit 3a8be0f)

- **GlobalExceptionHandler PII 예외 핸들러 추가**
  - `AdminEmailPartialSearchException` 400 핸들러 (RFC 9457 ProblemDetail)
  - `ConstraintViolationException` 400 핸들러 (Bean Validation 위반 표준화, 동일 ADMIN_EMAIL_PARTIAL_FORBIDDEN 코드)
  - `@MX:NOTE` + `@MX:SPEC` 추가 (SPEC §5.3 / REQ-PII-EMAIL-007 응답 코드 고정 근거)
  (SPEC-CMS-SECURITY-PII-002 Step 1, commits 3a8be0f + sync 단계)

- **UserServiceImpl findPage 시그니처 변경**
  - `findPage(actor)` 본인 row 제외 + `recordBulk` 호출
  (SPEC-CMS-SECURITY-PII-002 Step 3, commit 04b9fe3)

- **PiiAuditEnhanceIT 클래스 헤더 — 명시적 @Import**
  - `@Import(IntegrationAsyncConfig.class)` 추가 (프로젝트 컨벤션 일관 — `WebMvcTestInfraConfig` 선례)
  - `@TestConfiguration` 자동 컴포넌트 스캔 미보장 환경에서 IntegrationAsyncConfig 명시적 로드
  (SPEC-CMS-SECURITY-PII-FOLLOWUP-001 Step 1, commit `5fe440b`)

- **PiiAuditEnhanceIT — Awaitility polling 정리 (D5-1)**
  - SyncTaskExecutor override로 동기 실행 보장됨 → `await().atMost(2, SECONDS).untilAsserted(...)` 호출 제거
  - import 정리: `@Disabled`, `Awaitility.await`, `TimeUnit.SECONDS` 제거 (가독성 향상)
  (SPEC-CMS-SECURITY-PII-FOLLOWUP-001 Step 3, commit `5fe440b`)

- **PersonalDataAccessLogService.recordBulk 신규 메서드**
  - 기존 `record()` 패턴 따라 `@Async("auditExecutor")` + MDC 캡처 + 일괄 INSERT
  - try-catch fallback + Micrometer counter
  - `@MX:SPEC` sub-line 추가 (SPEC §5.5 / REQ-PII-EMAIL-009 — 적재 실패 시 user-facing 에러 미전파 정책)
  (SPEC-CMS-SECURITY-PII-002 Step 3, commits 04b9fe3 + sync 단계)

### Fixed

- **PiiEmailIntegrationTest 다중 IT 클래스 실행 시 격리 결함 해소**
  - `UserMapper.xml`: `email_encrypted`, `email_iv`, `email_tag` 컬럼에 `jdbcType="BINARY"` 명시
  - `UserMapper.xml`: `email_key_version` 컬럼에 `jdbcType="SMALLINT"` 명시
  - `PiiEmailIntegrationTest`에 `@Transactional` 추가 (테스트 간 DB 상태 격리)
  - 다중 IT 클래스 병렬/순차 실행 환경에서 `PiiEmailIntegrationTest` 회귀 0건 확인
  (SPEC-CMS-SECURITY-PII-001 Step 4 follow-up, commit f91628a)

### Security

- **PIPA 제29조 안전성 확보 조치 의무 충족**
  - `users.email` 컬럼 AES-256-GCM 암호화 적용 (애플리케이션 레이어)
  - HMAC-SHA256 기반 lookup으로 deterministic SHA-256 rainbow table 공격 방지
  - 키 관리 인터페이스(`PiiKeyVault`) 추상화로 운영 KMS(AWS KMS / HashiCorp Vault) 연동 준비
  - 코드 리뷰 `8c9ffd3` HIGH 갭 #3 (UserMapper email 암호화 미구현) 해소
  - 운영 배포 차단(P0 blocker) 상태 해소
  (SPEC-CMS-SECURITY-PII-001 Step 1~4, commits 1d4ae61, 0a6b14e, e432d53, 29878b9, f91628a, 44cc3b8)

- **PIPA 제29조 안전성 확보 조치 의무 추가 완화**
  - admin email partial 검색 차단 (전사 사용자 노출 방지)
  - API 응답 email 마스킹 (DTO 레벨, ADMIN/본인 외 사용자 PII 노출 차단)
  - PII 접근 감사 보강 (`personal_data_access_log` 일괄 적재로 비ADMIN/비본인 admin lookup 추적성 확보)
  - ArchUnit으로 마스킹 강제 (신규 DTO 회귀 방지)
  - OWASP A03(Injection) / A04(Insecure Design) / A05(Misconfiguration) / A09(Logging) 점검 PASS
  - SPEC-CMS-SECURITY-PII-001과 결합하여 운영 배포 차단 상태 완전 해소
  (SPEC-CMS-SECURITY-PII-002 Step 1~4, commits 3a8be0f, fbedd8c, 04b9fe3, 0b3d05e, 1b1f7d0)

---

### 후속 SPEC 예정

본 SPEC 1차 범위에서 의도적으로 제외된 항목들이 후속 SPEC으로 분리됩니다.
상세 비범위 정의는 SPEC §3.2를 참조하십시오.

| 후속 SPEC 후보 | 내용 |
|--------------|------|
| **Step 5 (이행 대기)** | `PiiEmailMigrationJob` 운영 배치 + V25 평문 컬럼 DROP — 운영 KMS 결정 후 별도 PR |
| **SPEC-CMS-SECURITY-PII-002** | REQ-PII-EMAIL-007(관리자 검색 제약) + REQ-PII-EMAIL-008(응답 마스킹) + REQ-PII-EMAIL-009(PII 접근 감사) — Implemented (1차) |
| **SPEC-CMS-SECURITY-PII-FOLLOWUP-001** | PII 비동기 감사 IT 검증 인프라 정비 (@Disabled 3건 활성화) — **Implemented (1차) 2026-05-08** |
| **SPEC-CMS-SECURITY-PII-KMS-001** | AWS KMS / HashiCorp Vault 어댑터 구현 (1차 `LocalEnvPiiKeyVault` 대체) |
| **SPEC-CMS-SECURITY-PII-ROTATION-001** | 키 자동 회전 배치(`PiiEmailRekeyJob`) + cron 스케줄 |
| **SPEC-CMS-SECURITY-PII-MASKING-001** | 로그/백업 PII 마스킹 표준 (Logback 필터 + pg_dump 파이프) |
| **SPEC-CMS-SECURITY-PII-NEXT-001 시리즈** | `users.name`, `users.phone_e164`, `login_history.ip` 등 나머지 PII 컬럼 암호화 |
