# SPEC-CMS-SECURITY-PII-001 — 인수 기준 (Acceptance Criteria)

본 문서는 SPEC-CMS-SECURITY-PII-001 개인정보 암호화(PII Encryption — Email)의 Given/When/Then 형식 인수 시나리오와 품질 게이트를 정의한다. 모든 시나리오는 통합 테스트(Testcontainers PostgreSQL 16) 또는 단위 테스트(JUnit 5 + Mockito)로 검증 가능해야 한다.

---

## A. 암호화 (REQ-PII-EMAIL-001 / 002 / 003)

### A.1 REQ-PII-EMAIL-001 — Email AES-256-GCM 암호화

#### A.1.1 신규 가입 시 email 자동 암호화 저장
- **Given**: PiiKeyVault에 활성 v1 키(32 bytes)가 등록되어 있고, V24 마이그레이션이 적용된 상태이며, 신규 사용자 회원가입 요청(`POST /api/v1/auth/signup`)이 `email="John.Doe@Example.COM"`, `password="..."`로 전송됨
- **When**: AuthServiceImpl.signup이 호출되어 `UserMapper.insert`가 실행되고, `AesGcmEmailTypeHandler`가 email 파라미터를 가로챔
- **Then**:
  - users 테이블에 신규 row가 생성된다
  - `email_encrypted` BYTEA 컬럼은 NOT NULL이며, 가변 길이 ciphertext가 저장된다
  - `email_iv` BYTEA 컬럼은 정확히 12 bytes 길이이다
  - `email_tag` BYTEA 컬럼은 정확히 16 bytes 길이이다
  - `email_key_version` SMALLINT 컬럼은 1이다
  - `email_hmac` VARCHAR(64) 컬럼은 hex 인코딩된 HMAC-SHA256 값(64자)이다
  - 평문 email 값은 DB의 어느 컬럼에도 그대로 저장되지 않는다 (`email` 컬럼은 NULL 또는 deprecated)
  - Micrometer 메트릭 `pii.email.encrypt.count`가 1 증가한다

#### A.1.2 동일 평문 + 동일 키로 두 번 INSERT 시 IV 재사용 방지
- **Given**: PiiKeyVault에 활성 v1 키가 등록되어 있고, 두 명의 신규 사용자가 동일 정규화 email은 다르되 평문 길이/문자가 동일한 두 row를 INSERT (테스트용 별도 시나리오로 동일 평문 강제)
- **When**: 두 row의 `email_encrypted`, `email_iv`, `email_tag`를 비교
- **Then**:
  - `email_iv` 값은 두 row가 서로 다르다 (SecureRandom으로 매 호출 신선한 IV 생성)
  - `email_encrypted` 값도 IV가 다르므로 서로 다르다
  - `email_tag` 값도 서로 다르다
  - 평문은 동일하지만 ciphertext만으로는 동일성을 추론할 수 없다 (의미적 보안 만족)

### A.2 REQ-PII-EMAIL-002 — Email 복호화

#### A.2.1 정상 복호화 — SELECT 경로에서 자동 평문 복원
- **Given**: A.1.1 시나리오로 row가 적재되어 있고, `email_key_version=1`이며 PiiKeyVault.getKeyByVersion(1)이 정상 키를 반환
- **When**: `UserMapper.findByEmailHmac(hmac)`가 호출되어 row를 SELECT
- **Then**:
  - User 객체의 `email` 필드는 평문 `"john.doe@example.com"`(또는 정규화된 형태)로 복원된다
  - 복호화는 TypeHandler가 자동 수행하며 service layer는 평문만 다룬다
  - Micrometer 메트릭 `pii.email.decrypt.count`가 1 증가한다
  - `pii.email.decrypt.failure.count`는 변동 없다

#### A.2.2 변조된 ciphertext 복호화 시 무결성 위반 처리
- **Given**: 정상 row가 적재된 후, DB 직접 UPDATE로 `email_encrypted` 또는 `email_tag` byte 1개를 변조
- **When**: `findByEmailHmac` 또는 `findById` SELECT 실행 시 TypeHandler가 복호화 시도
- **Then**:
  - `AEADBadTagException`이 catch된다
  - `audit_log` 테이블에 신규 row가 적재된다 (`severity='CRITICAL'`, `category='PII_INTEGRITY_VIOLATION'`, `target_user_id={해당 row id}`, `description`에 'email decryption failed' 포함)
  - 호출자에게는 `PiiIntegrityException`이 전파되고 HTTP 응답은 500 (`PII_DECRYPT_FAILED`)
  - 평문 복원 시도가 silently 성공하지 않으며 service는 잘못된 평문을 반환하지 않는다
  - 알림 큐에 운영자 알림이 push된다 (SPEC-CMS-005 통합)
  - Micrometer 메트릭 `pii.email.decrypt.failure.count`가 1 증가

### A.3 REQ-PII-EMAIL-003 — HMAC 격상

#### A.3.1 normalizedEmail 기반 결정적 HMAC 생성
- **Given**: PiiKeyVault.getHmacKey()가 고정 hmac 키를 반환하며, 입력 email은 `"  John.Doe@Example.COM  "` (앞뒤 공백, 대소문자 혼재)
- **When**: AuthService가 normalizedEmail 정규화 후 HMAC을 계산
- **Then**:
  - normalizedEmail은 `"john.doe@example.com"`이다 (trim + toLowerCase)
  - HMAC = `hex(HmacSHA256(hmacKey, "john.doe@example.com"))`이며 64자 hex 문자열이다
  - 동일 입력에 대해 매 호출 동일 HMAC이 반환된다 (deterministic)
  - 동일 입력 + 다른 hmacKey → 다른 HMAC (키 의존성 검증)

#### A.3.2 HMAC 키와 암호화 키의 분리 검증
- **Given**: PiiKeyVault에 암호화 키(`getActiveKey().key`)와 HMAC 키(`getHmacKey()`)가 모두 등록됨
- **When**: 단위 테스트로 두 키의 byte 배열을 비교
- **Then**:
  - 두 키의 byte[]는 서로 다르다 (동일 키 재사용 금지 정책 준수)
  - LocalEnvPiiKeyVault에서 두 환경변수(`PII_EMAIL_KEY_V1`, `PII_EMAIL_HMAC_KEY`)가 동일 값으로 설정된 경우 부팅 시 경고 또는 거부 로직이 발동한다 (단위 테스트로 검증)

---

## B. 키 관리 (REQ-PII-EMAIL-004 / 005)

### B.1 REQ-PII-EMAIL-004 — PiiKeyVault 인터페이스

#### B.1.1 LocalEnvPiiKeyVault 환경변수 로드 성공
- **Given**: 환경변수 `PII_EMAIL_KEY_V1=<base64 32-byte key>`, `PII_EMAIL_HMAC_KEY=<base64 32-byte key>`가 설정되고, Spring profile은 `dev` 또는 `local`
- **When**: Spring 부팅 시 `LocalEnvPiiKeyVault` 빈이 초기화됨
- **Then**:
  - `getActiveKey()`는 `ActiveKey(secretKey, version=1)`을 반환한다
  - secretKey의 byte[] 길이는 정확히 32 bytes (AES-256)이다
  - `getKeyByVersion(1)`은 동일 키를 반환한다
  - `getKeyByVersion(2)`는 `PiiKeyVersionNotFoundException`을 발생시킨다 (v2 미등록)
  - `getHmacKey()`는 별도 32-byte 키를 반환한다

#### B.1.2 운영 환경에서 LocalEnvPiiKeyVault 활성화 시 부팅 거부
- **Given**: Spring profile은 `prod`이고, `LocalEnvPiiKeyVault`가 active 빈으로 등록된 상태
- **When**: Spring ApplicationContext 초기화 진행
- **Then**:
  - `IllegalStateException` 또는 `BeanCreationException`이 발생한다
  - 에러 메시지에 `"LocalEnvPiiKeyVault must not be active in prod profile"`(또는 동등) 포함
  - 애플리케이션 부팅이 실패한다 (`ApplicationFailedEvent` 발화)
  - 운영 환경에서는 KMS-backed 구현체만 허용됨이 단위 테스트로 검증된다

### B.2 REQ-PII-EMAIL-005 — 키 회전

#### B.2.1 v2 키 활성화 후 신규 INSERT는 v2로 암호화
- **Given**: 기존 사용자 row는 `email_key_version=1`로 적재되어 있고, PiiKeyVault에 v2 키가 신규 등록되어 active version이 2로 변경됨
- **When**: 신규 회원가입 (`POST /api/v1/auth/signup`)이 호출됨
- **Then**:
  - 신규 row의 `email_key_version` 컬럼은 2이다
  - `email_encrypted`/`email_iv`/`email_tag`는 v2 키로 암호화된 값이다
  - 기존 v1 row는 영향 없이 유지된다

#### B.2.2 v1, v2 row 혼재 상태에서 양쪽 모두 정상 복호화
- **Given**: B.2.1 결과로 v1 row와 v2 row가 혼재하며, PiiKeyVault.getKeyByVersion(1)과 (2) 모두 정상 키 반환
- **When**: `UserMapper.findPage` 또는 `findPageWithScope`로 v1, v2 row를 함께 SELECT
- **Then**:
  - 각 row의 `email_key_version` 값에 따라 TypeHandler가 적절한 키를 조회하여 복호화한다
  - 모든 row의 `User.email`이 정상 평문으로 복원된다
  - 복호화 실패는 발생하지 않는다 (하위 호환 검증)

---

## C. 조회·검색 (REQ-PII-EMAIL-006 / 007)

### C.1 REQ-PII-EMAIL-006 — Email 기반 lookup

#### C.1.1 정규화된 email로 HMAC lookup 성공
- **Given**: 사용자가 가입 시 `email="john.doe@example.com"`으로 적재되어 `email_hmac`가 저장된 상태
- **When**: 로그인 요청에서 `email="John.Doe@Example.COM"`(다른 대소문자) 입력
- **Then**:
  - normalizedEmail = `"john.doe@example.com"`
  - lookupHmac = `hex(HMAC-SHA256(hmacKey, normalizedEmail))`
  - `UserMapper.findByEmailHmac(lookupHmac)`가 가입 시점의 row를 정확히 반환한다
  - User 객체의 email은 복호화되어 평문으로 채워진다
  - 비밀번호 검증 등 후속 인증 흐름이 정상 진행된다

#### C.1.2 미존재 email lookup 시 timing attack 방지
- **Given**: DB에 `john.doe@example.com` row만 존재
- **When**: 로그인 요청에서 `email="nonexistent@example.com"` 입력
- **Then**:
  - `findByEmailHmac`는 `Optional.empty()`를 반환한다
  - 응답 시간은 정상 lookup 대비 5% 이내 차이로 dummy hash 비교를 수행한다 (SPEC-CMS-002 §10.3 timing attack 방지 패턴 재사용)
  - 최종 응답은 `401 Unauthorized` (사용자 존재 여부 미노출)
  - audit_log에는 PII 접근 감사 적재되지 않는다 (복호화 미수행)

### C.2 REQ-PII-EMAIL-007 — 관리자 검색 제약

#### C.2.1 ADMIN 사용자 email 완전일치 검색 정상 동작
- **Given**: ADMIN 권한 사용자가 인증된 상태이며 `john.doe@example.com` row가 적재됨
- **When**: `GET /api/v1/admin/users?email=john.doe@example.com`
- **Then**:
  - 서버는 normalizedEmail HMAC을 계산하여 `email_hmac` 매칭 row 1건을 반환한다
  - 응답에는 평문 email이 포함된다 (ADMIN 권한)
  - PII 접근 감사 row가 `personal_data_access_log`에 적재된다 (purpose='ADMIN_USER_SEARCH')

#### C.2.2 ADMIN email partial 검색 차단
- **Given**: ADMIN 권한 사용자가 인증된 상태
- **When**: `GET /api/v1/admin/users?email=john*` 또는 `email=*example.com` 또는 `email=%doe%`
- **Then**:
  - 서버는 400 Bad Request를 반환한다
  - 응답 body의 에러 코드는 `ADMIN_EMAIL_PARTIAL_FORBIDDEN`이다
  - 에러 메시지는 "email 컬럼은 완전일치 검색만 허용됩니다" (또는 동등)
  - `users` 테이블에 ILIKE 쿼리는 실행되지 않는다 (DB 슬로우 쿼리 로그 검증)
  - username/name partial 검색(`?username=john*`)은 정상 동작 (REQ-PII-EMAIL-007 명시 — email만 차단)

---

## D. 응답 마스킹 (REQ-PII-EMAIL-008)

### D.1 비ADMIN, 비본인 조회 시 email 마스킹
- **Given**: 일반 사용자(USER 권한, id=10)가 인증된 상태이며, 조회 대상 사용자는 id=42 (`email="john.doe@example.com"`)
- **When**: `GET /api/v1/users/42` 호출
- **Then**:
  - 응답 status는 200 OK
  - 응답 body의 `email` 필드는 `"j***e@e***.com"`이다
  - local-part는 `"john.doe"` → `"j***e"` (첫 글자 + `***` + 마지막 글자)
  - domain-part는 `"example.com"` → `"e***.com"` (첫 글자 + `***` + TLD 보존)
  - `personal_data_access_log`에 적재되지 않는다 (마스킹된 응답은 PII 접근으로 분류하지 않음)

### D.2 본인 조회 시 평문 email 노출
- **Given**: 사용자(USER 권한, id=42)가 인증된 상태
- **When**: `GET /api/v1/users/42` 또는 `GET /api/v1/me` 호출
- **Then**:
  - 응답 body의 `email` 필드는 `"john.doe@example.com"` (평문)이다
  - `personal_data_access_log`에 적재되지 않는다 (본인 조회는 감사 제외 — REQ-PII-EMAIL-009)
  - 마스킹 직렬화는 SecurityContext의 user.id == target.id 분기로 우회된다

### D.3 ADMIN 조회 시 평문 email 노출 + 감사 적재
- **Given**: ADMIN 권한 사용자(id=1)가 인증된 상태이며 조회 대상은 id=42
- **When**: `GET /api/v1/admin/users/42` 호출
- **Then**:
  - 응답 body의 `email` 필드는 `"john.doe@example.com"` (평문)이다
  - `personal_data_access_log`에 신규 row가 적재된다
    - `accessor_id=1`, `target_user_id=42`, `accessed_field='email'`, `purpose='ADMIN_USER_DETAIL'`, `accessed_at=CURRENT_TIMESTAMP`
    - `ip_hash`는 ADMIN의 요청 IP SHA-256 hex
  - 응답 시간 추가 지연 < 10ms (audit 적재가 비동기 또는 같은 트랜잭션 내 fast path)

---

## E. Audit (REQ-PII-EMAIL-009)

### E.1 ADMIN 사용자 검색 시 PII 접근 감사 적재
- **Given**: ADMIN 사용자(id=1)가 인증된 상태, DB에 `john.doe@example.com` 사용자(id=42) 존재
- **When**: `GET /api/v1/admin/users?email=john.doe@example.com` 호출
- **Then**:
  - 응답 status 200 OK + 1건 결과
  - `personal_data_access_log`에 신규 row 1건 적재
    - `accessor_id=1`, `target_user_id=42`, `accessed_field='email'`, `purpose='ADMIN_USER_SEARCH'`
  - 응답에는 평문 email 포함 (ADMIN 권한)

### E.2 비밀번호 재설정 lookup은 audit 제외
- **Given**: 비로그인 사용자가 비밀번호 재설정 요청 (`POST /api/v1/auth/password/reset/request {email: "..."}`)
- **When**: AuthService가 normalizedEmail HMAC으로 `findByEmailHmac` lookup
- **Then**:
  - lookup이 성공하든 실패하든 `personal_data_access_log`에 적재되지 않는다 (HMAC 매칭만 수행, 평문 노출 없음)
  - 단, 실제 평문 복호화가 발생하는 다음 단계(예: 토큰 발급 후 사용자에게 email 전송)는 감사 적재 대상이며, 본 시나리오에서는 검증 범위 외 (REQ-PII-EMAIL-009 명시)

---

## F. 비기능 (REQ-PII-EMAIL-010)

### F.1 단일 row 암호화 성능 < 5ms
- **Given**: PiiKeyVault에 활성 키가 등록되어 in-memory 상태이고, 1KB 이내 평문 email
- **When**: AesGcmEncryptor.encrypt를 1,000회 반복 호출하여 평균 측정
- **Then**:
  - 평균 암호화 지연 < 5ms (BCFIPS 또는 javax.crypto SunJCE provider, JDK 17, x86_64 기준)
  - p99 < 15ms
  - 단위 테스트는 JMH 또는 Spring `@TestPropertySource` + `StopWatch`로 측정 (CI 환경 별도 baseline)

### F.2 마이그레이션 배치 100만 row < 30분
- **Given**: 테스트 데이터로 100만 row의 평문 email이 적재된 상태(시드), V24 적용 직후, 단일 노드 PostgreSQL 16 + 단일 백엔드 노드
- **When**: `PiiEmailMigrationJob` 실행
- **Then**:
  - 30분 이내 모든 row가 `email_encrypted` NOT NULL 상태로 마이그레이션 완료
  - `WHERE email_encrypted IS NULL AND email IS NOT NULL` 카운트가 0
  - 무작위 샘플 100건 verify roundtrip 100% 일치
  - Micrometer 메트릭 `pii.migration.progress.success` = 1,000,000
  - `pii.migration.progress.failure` = 0
  - 배치 실행 중 신규 INSERT(회원가입)도 정상 동작 (V24 적용 시점부터 TypeHandler가 자동 암호화)

---

## G. Quality Gates (Step별 PASS 기준)

### G.1 Step 1 — PiiKeyVault 인터페이스 + LocalEnvPiiKeyVault
- 단위 테스트 커버리지 ≥ 90% (인터페이스 + LocalEnv 구현체)
- 8 케이스 이상 PASS:
  - 정상 키 로드, 키 길이 검증, 누락 환경변수, base64 디코딩 실패, prod profile 부팅 거부, 키 분리 검증, ActiveKey/HmacKey 별도 반환, getKeyByVersion 미존재 예외
- ArchUnit: `LocalEnvPiiKeyVault`는 `KmsBackedPiiKeyVault` 패키지에 의존하지 않음
- 정적 분석: `Spotbugs`, `Checkstyle` 0건 위반

### G.2 Step 2 — AesGcmEmailTypeHandler
- 단위 테스트 커버리지 ≥ 90%
- 12 케이스 이상 PASS (REQ-PII-EMAIL-001/002/003 검증):
  - encrypt/decrypt roundtrip, null/empty 처리, large input, key rotation v1→v2 호환, tag mismatch + audit_log, IV 신선도, 키 부재 예외, HMAC 일관성, normalizedEmail 정규화, 동시성 10×100, 메트릭 노출
- 보안 정적 분석: `dependency-check` (Bouncy Castle 또는 javax.crypto 취약점 0건)
- LSP: 0 errors, 0 warnings

### G.3 Step 3 — V24 마이그레이션
- Flyway 적용 성공 (Testcontainers PostgreSQL 16)
- 컬럼 검증: `email_encrypted`, `email_iv`, `email_tag`, `email_hmac`, `email_key_version` 모두 존재
- UNIQUE 인덱스 `idx_users_email_hmac` 존재 (`pg_indexes` 카탈로그)
- `data_dictionary` 5개 row 시드 검증
- 멱등성: 두 번째 적용 시 충돌 없음 (`IF NOT EXISTS` 가드)
- V25는 본 Step 범위 외 (운영 단계 별도 PR)

### G.4 Step 4 — UserMapper.xml + AuthServiceImpl 변경
- 통합 테스트 커버리지 ≥ 85% (UserMapper + AuthService)
- E2E 시나리오 PASS:
  - 신규 가입 → 암호화 저장 → 로그인 → HMAC lookup → 복호화 → 인증 성공
  - 비밀번호 재설정 요청 → HMAC lookup → 토큰 발급
  - 관리자 사용자 상세 조회 → 평문 노출 + audit 적재
  - 비ADMIN 사용자 상세 조회 → 마스킹 응답
  - 관리자 email partial 검색 → 400 ADMIN_EMAIL_PARTIAL_FORBIDDEN
  - 관리자 username partial 검색 → 정상 동작 (회귀 검증)
- 기존 인증 테스트(SPEC-CMS-002 회귀) 100% PASS
- 응답 마스킹 ArchUnit 검증: 모든 `*UserResponse` DTO에 마스킹 매퍼 적용

### G.5 Step 5 — 데이터 마이그레이션 배치
- F.2 성능 기준 PASS (100만 row < 30분)
- 배치 실패 시 ROLLBACK 검증 (의도적 실패 주입 시나리오)
- audit_log CRITICAL 적재 검증 (verify roundtrip 실패 시뮬레이션)
- 운영 매뉴얼 (`pii-email-migration-runbook.md`) 작성 완료
- V25는 본 SPEC 1차 RUN 범위 외 (운영 단계 별도)

---

## H. Definition of Done (DoD)

본 SPEC은 다음 8개 체크리스트가 모두 충족될 때 완료(Done) 상태로 간주한다.

- [ ] **DoD-1**: V24 마이그레이션 SQL 작성·검증 완료 (Flyway + Testcontainers PostgreSQL 16, `data_dictionary` 시드 포함)
- [ ] **DoD-2**: `PiiKeyVault` 인터페이스 + `LocalEnvPiiKeyVault` 구현체 + 단위 테스트 8 케이스 이상 PASS, prod profile 부팅 거부 검증
- [ ] **DoD-3**: `AesGcmEmailTypeHandler` 구현 + 단위 테스트 12 케이스 이상 PASS (encrypt/decrypt/null/large/key rotation/tag mismatch/IV reuse 방지 모두 포함), Micrometer 메트릭 노출
- [ ] **DoD-4**: `UserMapper.xml` 수정(4개 신규 컬럼 매핑 + `findByEmailHmac` 추가), `AuthServiceImpl.findByEmail` HMAC lookup 경로 전환, deprecated `findByEmailHash` 호출 경로 제거
- [ ] **DoD-5**: 응답 마스킹 적용(`UserResponseMapper` 또는 `MaskedEmailSerializer`) — 모든 `*UserResponse` DTO에 일관 적용, ArchUnit 강제, 통합 테스트 D.1/D.2/D.3 PASS
- [ ] **DoD-6**: 관리자 email partial 검색 차단(REQ-PII-EMAIL-007), `ADMIN_EMAIL_PARTIAL_FORBIDDEN` 에러 코드 정의 + 통합 테스트 PASS
- [ ] **DoD-7**: PII 접근 감사(REQ-PII-EMAIL-009) — `personal_data_access_log` 적재 경로 통합, ADMIN 검색·상세 시나리오 검증, 본인 조회·HMAC 단독 lookup은 감사 제외 검증
- [ ] **DoD-8**: 데이터 마이그레이션 배치(`PiiEmailMigrationJob`) 구현 + 운영 매뉴얼 작성, F.2 성능 기준 충족(100만 row < 30분), V25 마이그레이션 SQL 초안 작성(운영 단계 PR로 분리)

---
