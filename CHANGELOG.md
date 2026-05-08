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

---

### 후속 SPEC 예정

본 SPEC 1차 범위에서 의도적으로 제외된 항목들이 후속 SPEC으로 분리됩니다.
상세 비범위 정의는 SPEC §3.2를 참조하십시오.

| 후속 SPEC 후보 | 내용 |
|--------------|------|
| **Step 5 (이행 대기)** | `PiiEmailMigrationJob` 운영 배치 + V25 평문 컬럼 DROP — 운영 KMS 결정 후 별도 PR |
| **SPEC-CMS-SECURITY-PII-002** | REQ-PII-EMAIL-007(관리자 검색 제약) + REQ-PII-EMAIL-008(응답 마스킹) + REQ-PII-EMAIL-009(PII 접근 감사) |
| **SPEC-CMS-SECURITY-PII-KMS-001** | AWS KMS / HashiCorp Vault 어댑터 구현 (1차 `LocalEnvPiiKeyVault` 대체) |
| **SPEC-CMS-SECURITY-PII-ROTATION-001** | 키 자동 회전 배치(`PiiEmailRekeyJob`) + cron 스케줄 |
| **SPEC-CMS-SECURITY-PII-MASKING-001** | 로그/백업 PII 마스킹 표준 (Logback 필터 + pg_dump 파이프) |
| **SPEC-CMS-SECURITY-PII-NEXT-001 시리즈** | `users.name`, `users.phone_e164`, `login_history.ip` 등 나머지 PII 컬럼 암호화 |
