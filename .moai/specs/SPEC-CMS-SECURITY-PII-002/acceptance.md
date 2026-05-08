# SPEC-CMS-SECURITY-PII-002 — 인수 기준 (Acceptance Criteria)

본 문서는 SPEC-CMS-SECURITY-PII-002 PII 노출 통제(Admin 검색 partial 차단 + 응답 마스킹 + PII 접근 감사 보강)의 Given/When/Then 형식 인수 시나리오와 품질 게이트를 정의한다. 모든 시나리오는 통합 테스트(Testcontainers PostgreSQL 16 + Spring Boot 3.4 `@SpringBootTest`) 또는 단위 테스트(JUnit 5 + Mockito)로 검증 가능해야 한다.

본 SPEC은 SPEC-CMS-SECURITY-PII-001 RUN 1차(V24 마이그레이션 + AesGcmEmailEncryptionService + HMAC lookup) 적용 완료 상태를 전제로 하며, 신규 DDL은 없다.

---

## A. REQ-PII-EMAIL-007 — Admin email partial 검색 차단

### AC-007-1 — partial 패턴 4종 거부 (와일드카드)

- **Given**: ADMIN 권한 사용자가 인증된 상태이며, V24 마이그레이션이 적용되어 `email_hmac` 컬럼이 존재하고, `NoEmailWildcardValidator`가 컨트롤러 파라미터 검증 단계에 등록됨
- **When**: 관리자가 와일드카드/유사 패턴이 포함된 email 파라미터로 사용자 검색을 호출
  - `GET /api/v1/admin/users?email=john*`
  - `GET /api/v1/admin/users?email=*example.com`
  - `GET /api/v1/admin/users?email=%doe%`
  - `GET /api/v1/admin/users?email=john_`
- **Then**:
  - 4개 요청 모두 400 Bad Request 응답
  - 응답 body의 에러 코드는 `ADMIN_EMAIL_PARTIAL_FORBIDDEN`
  - 에러 메시지에 "email 컬럼은 완전일치 검색만 허용됩니다" 또는 동등 문구 포함
  - `users` 테이블에 ILIKE 또는 `pg_trgm` 쿼리가 실행되지 않음 (DB 슬로우 쿼리 로그 또는 Hibernate Statistics 검증)
  - `personal_data_access_log`에 row가 적재되지 않음 (실패 응답은 적재 제외)

### AC-007-2 — `@` 미포함 partial 거부

- **Given**: ADMIN 권한 사용자가 인증된 상태
- **When**: 관리자가 `@`이 없는 email 파라미터로 검색 — `GET /api/v1/admin/users?email=test`
- **Then**:
  - 응답은 400 Bad Request `ADMIN_EMAIL_PARTIAL_FORBIDDEN`
  - RFC 5321 valid email format 위배(local@domain 구조 미충족)로 거부
  - DB 쿼리 미실행

### AC-007-3 — `@`-trailing partial 거부

- **Given**: ADMIN 권한 사용자가 인증된 상태
- **When**: 관리자가 `@`로 끝나거나 `@` 뒤가 비어있는 email 파라미터로 검색 — `GET /api/v1/admin/users?email=test@`
- **Then**:
  - 응답은 400 Bad Request `ADMIN_EMAIL_PARTIAL_FORBIDDEN`
  - domain-part 미존재로 RFC 5321 valid email format 위배
  - DB 쿼리 미실행

### AC-007-4 — 정상 완전일치 검색 + audit 적재

- **Given**: ADMIN 권한 사용자(id=1)가 인증된 상태이며, DB에 `email="john.doe@example.com"`(id=42) row가 SPEC-PII-001 RUN 1차로 적재됨(`email_encrypted`/`email_hmac` 정상)
- **When**: `GET /api/v1/admin/users?email=john.doe@example.com`
- **Then**:
  - 응답은 200 OK + 결과 1건
  - 서버는 normalizedEmail HMAC을 계산하여 `email_hmac` 매칭으로 row 1건 반환 (REQ-PII-EMAIL-006 재사용)
  - 응답 body의 email은 SUPER_ADMIN 권한이면 평문, ADMIN(비SUPER) 권한이면 마스킹 (REQ-008과의 결합 — 본 시나리오는 SUPER_ADMIN으로 가정 시 `"john.doe@example.com"`)
  - `personal_data_access_log`에 신규 row 1건 적재 — `accessor_id=1`, `target_user_id=42`, `accessed_fields=["email"]`, `purpose='ADMIN_EMAIL_LOOKUP'`

### AC-007-5 — 빈 문자열 무시 (전체 검색 분기)

- **Given**: ADMIN 권한 사용자가 인증된 상태이며, DB에 N건 사용자 row 존재
- **When**: `GET /api/v1/admin/users?email=` (빈 문자열) 또는 `GET /api/v1/admin/users` (파라미터 미포함)
- **Then**:
  - 응답은 200 OK + N건 페이지 결과
  - 빈 문자열은 `null` 동등 처리되어 `NoEmailWildcardValidator`를 통과 (Spring `@RequestParam(required=false)` null/빈 동등)
  - email 매칭 분기 미진입, 전체 검색 분기로 진행
  - ILIKE 쿼리 미발생
  - `personal_data_access_log`에 N건 일괄 적재 — `purpose='ADMIN_USER_LIST'` (REQ-009 결합)

### AC-007-6 — username/name partial 검색은 정상 동작 (회귀 검증)

- **Given**: ADMIN 권한 사용자가 인증된 상태이며, DB에 `username='johndoe'`, `name='John Doe'` 사용자 row 존재
- **When**: `GET /api/v1/admin/users?username=john*` 또는 `GET /api/v1/admin/users?name=*Doe*`
- **Then**:
  - 응답은 200 OK + 매칭 결과
  - `users.username`/`users.name`에 ILIKE 또는 `pg_trgm` partial 쿼리가 정상 실행됨 (REQ-007은 email만 차단 — `NoEmailWildcardValidator`는 email 파라미터에만 적용)
  - SPEC-CMS-010 §4 trgm 인덱스 활용 정상 동작 (회귀)
  - `personal_data_access_log`에 일괄 적재 — `purpose='ADMIN_USER_LIST'`

---

## B. REQ-PII-EMAIL-008 — API 응답 email 마스킹

### AC-008-1 — local-part 1자 마스킹 (`*`)

- **Given**: USER 권한 사용자(id=10)가 인증된 상태이며, 조회 대상은 id=42 (`email="a@example.com"`)로 SPEC-PII-001 RUN 1차로 적재됨
- **When**: `GET /api/v1/users/42` 호출
- **Then**:
  - 응답 status 200 OK
  - 응답 body의 `email` 필드는 `"*@e***.com"`
  - local-part `"a"` (1자) → `"*"` (단일 별표)
  - domain-part `"example.com"` → `"e***.com"` (첫 글자 + `***` + TLD)

### AC-008-2 — local-part 2자 마스킹 (`**`, 사용자 결정 사항)

- **Given**: USER 권한 사용자(id=10)가 인증된 상태이며, 조회 대상은 id=43 (`email="ab@example.com"`)
- **When**: `GET /api/v1/users/43` 호출
- **Then**:
  - 응답 status 200 OK
  - 응답 body의 `email` 필드는 `"**@e***.com"`
  - local-part `"ab"` (2자) → `"**"` (이중 별표) — SPEC-PII-001 §5.4 원문 따름, analyst의 `a*` 해석 무효
  - domain-part `"example.com"` → `"e***.com"`

### AC-008-3 — local-part 3자 이상 마스킹 (`j***e@e***.com`)

- **Given**: USER 권한 사용자(id=10)가 인증된 상태이며, 조회 대상은 id=42 (`email="john.doe@example.com"`)
- **When**: `GET /api/v1/users/42` 호출
- **Then**:
  - 응답 status 200 OK
  - 응답 body의 `email` 필드는 `"j***e@e***.com"`
  - local-part `"john.doe"` (8자, 3자 이상) → 첫 글자 `"j"` + `"***"` + 마지막 글자 `"e"` = `"j***e"`
  - domain-part `"example.com"` → `"e***.com"`
  - `personal_data_access_log`에 적재되지 않음 (마스킹 응답은 평문 노출 없음 — REQ-009 명시)

### AC-008-4 — 본인 조회 시 평문 노출 (마스킹 미적용)

- **Given**: 사용자(USER 권한, id=42)가 인증된 상태
- **When**: `GET /api/v1/users/42` 또는 `GET /api/v1/me` 호출
- **Then**:
  - 응답 status 200 OK
  - 응답 body의 `email` 필드는 `"john.doe@example.com"` (평문)
  - SecurityContext의 `JwtPrincipal.userId() == target.userId()` 분기로 `EmailMaskSerializer`가 마스킹을 우회
  - `/api/v1/me`의 경우 UserSelf DTO 반환 (DTO 자체가 마스킹 미적용 — 자기 정보 평문 OK 정책)
  - `personal_data_access_log`에 적재되지 않음 (본인 조회 적재 제외 — REQ-009 명시)

### AC-008-5 — SUPER_ADMIN 조회 시 평문 노출 + audit 적재

- **Given**: SUPER_ADMIN 권한 사용자(id=1)가 인증된 상태이며 조회 대상은 id=42 (`email="john.doe@example.com"`)
- **When**: `GET /api/v1/admin/users/42` 호출
- **Then**:
  - 응답 status 200 OK
  - 응답 body의 `email` 필드는 `"john.doe@example.com"` (평문)
  - `EmailMaskSerializer`가 `hasRole('SUPER_ADMIN')` 분기로 마스킹 우회
  - `personal_data_access_log`에 신규 row 1건 적재 — `accessor_id=1`, `target_user_id=42`, `accessed_fields=["email"]`, `purpose='ADMIN_USER_DETAIL'` (SPEC-PII-001 RUN 1차 기존 적용분)

### AC-008-6 — IDN 도메인·이모지 local-part 코드 포인트 안전 (EC-001)

- **Given**: USER 권한 사용자(id=10)가 인증된 상태이며, 조회 대상은 id=44로 IDN 도메인 또는 이모지 포함 email 적재
  - 케이스 A: `email="alice@한국.kr"` (IDN domain)
  - 케이스 B: `email="🙂a@example.com"` (이모지 + alphabet local 2자 코드 포인트)
- **When**: `GET /api/v1/users/44` 호출
- **Then**:
  - 케이스 A 응답 body의 `email`은 `"a***e@한***.kr"` (local 5자 → 첫/마지막, IDN domain 첫 라벨 첫 글자 + `***` + TLD)
  - 케이스 B 응답 body의 `email`은 `"**@e***.com"` (이모지 1 코드 포인트 + `a` 1 코드 포인트 = 총 2 코드 포인트 → `**` 마스킹, AC-008-2 규칙 적용)
  - `String.codePointCount(0, length)` 기반 길이 계산으로 UTF-16 surrogate pair 안전
  - `codePointAt()` + `appendCodePoint()`로 첫/마지막 코드 포인트 추출
  - 마스킹 결과는 valid UTF-8 문자열

---

## C. REQ-PII-EMAIL-009 — PII 접근 감사 보강

### AC-009-1 — `findPage(actor)` 결과 N건 일괄 적재

- **Given**: ADMIN 권한 사용자(id=1)가 인증된 상태이며, DB에 사용자 row 5건(id=10, 20, 30, 40, 50) 적재됨, `PersonalDataAccessPurpose.ADMIN_USER_LIST` enum 추가됨
- **When**: `GET /api/v1/admin/users?page=0&size=10` (전체 검색)
- **Then**:
  - 응답 status 200 OK + Page<UserSummary> 5건
  - 응답 반환 후 `@TransactionalEventListener(phase=AFTER_COMMIT)` 또는 `@Async` 비동기 실행으로 audit 적재
  - `personal_data_access_log`에 5건 신규 row 적재 (또는 batch INSERT 1건)
    - 각 row: `accessor_id=1`, `target_user_id ∈ {10, 20, 30, 40, 50}`, `accessed_fields=["email"]`, `purpose='ADMIN_USER_LIST'`, `accessed_at` ≈ 메서드 진입 시각, `ip_hash=SHA-256(admin.ip)`
  - 메서드 진입 시각이 동기 캡처되어 비동기 적재 시각과 무관하게 정확한 접근 시점 기록
  - API 응답 지연 < 50ms (비동기 실행으로 동기 트랜잭션에 영향 없음)

### AC-009-2 — 본인 row는 적재 제외

- **Given**: ADMIN 권한 사용자(id=1)가 인증된 상태이며, DB에 사용자 row 5건(id=1, 10, 20, 30, 40) 적재 — 그 중 id=1은 ADMIN 본인
- **When**: `GET /api/v1/admin/users?page=0&size=10`
- **Then**:
  - 응답 status 200 OK + Page<UserSummary> 5건 (본인 row 포함)
  - `personal_data_access_log`에는 4건만 적재 (id=10, 20, 30, 40)
  - id=1 (본인) row는 `targetUserIds`에서 사전 제외 (`!= actor.userId()`)
  - 이유: 본인 조회 적재 제외 (REQ-PII-EMAIL-009 명시, SPEC-PII-001 §5.5 재사용)

### AC-009-3 — HMAC lookup-only 경로 적재 제외

- **Given**: 비로그인 사용자가 비밀번호 재설정 요청 (`POST /api/v1/auth/password/reset/request {email: "john.doe@example.com"}`), DB에 해당 사용자 row 적재
- **When**: `AuthService.requestPasswordReset`가 normalizedEmail HMAC으로 `findByEmailHmac` lookup
- **Then**:
  - lookup이 성공하든 실패하든 `personal_data_access_log`에 적재되지 않음
  - 이유: HMAC 매칭만 수행되며 평문 복호화가 발생하지 않으므로 PII 노출 없음 (REQ-PII-EMAIL-009 본문 명시)
  - 이후 토큰 발급 후 사용자 email로 발송하는 단계는 본 시나리오 검증 범위 외 (의도적 제외)

### AC-009-4 — 자기 정보 조회 (`GET /api/v1/me`) 적재 제외

- **Given**: USER 권한 사용자(id=42)가 인증된 상태
- **When**: `GET /api/v1/me` 호출
- **Then**:
  - 응답 status 200 OK + UserSelf 평문 email
  - `personal_data_access_log`에 적재되지 않음 (본인 조회 적재 제외)
  - SecurityContext의 `JwtPrincipal.userId() == target.userId()` 사전 분기로 적재 호출 자체 미발생

### AC-009-5 — INSERT 실패 시 user-facing 에러 미전파 (AOP fallback)

- **Given**: ADMIN 권한 사용자(id=1)가 인증된 상태, Mockito로 `PersonalDataAccessLogService.recordBulk`가 `DataAccessException`을 throw하도록 설정
- **When**: `GET /api/v1/admin/users?page=0&size=10`
- **Then**:
  - 응답 status 200 OK + Page<UserSummary> 정상 반환 (검색 결과 영향 없음)
  - user-facing 에러 미전파 (AOP fallback 정책 — 사용자 결정 사항)
  - 서버 로그에 ERROR 레벨 로그 적재 — 메시지에 `"PII audit log INSERT failed"` 또는 동등
  - Micrometer 카운터 `pii.audit.log.failure.count`가 1 증가
  - 운영 알림 큐에 push (SPEC-CMS-005 REQ-CROSS-001-D-6 통합)
  - 5분간 실패 카운터 임계치(기본 10건) 초과 시 ALERT (별도 시나리오)

### AC-009-6 — 비동기 실행 후 트랜잭션 커밋 시 적재 검증

- **Given**: ADMIN 권한 사용자(id=1)가 인증된 상태, `@TransactionalEventListener(phase=AFTER_COMMIT)` 또는 `@Async + @Transactional(propagation=REQUIRES_NEW)` 설정 활성, 메서드 진입 시각을 동기 캡처
- **When**: `GET /api/v1/admin/users` 호출 → 메서드 진입 → 시각 캡처 → 검색 트랜잭션 커밋 → AFTER_COMMIT 이벤트 발화 → 비동기 INSERT 실행
- **Then**:
  - API 응답이 즉시 반환됨 (동기 응답 지연 영향 없음)
  - `personal_data_access_log`의 `accessed_at` 컬럼은 메서드 진입 시각(동기 캡처)에 가까움 (트랜잭션 커밋 후 시각 차이 보정됨)
  - 비동기 실행 컨텍스트에서 `SecurityContextHolder` 접근이 정상 동작 (서블릿 컨텍스트 전파 또는 명시적 전달)
  - 검색 트랜잭션이 롤백된 경우(예: 다른 예외) audit는 적재되지 않음 (`AFTER_COMMIT` 보장)
  - 통합 테스트는 `Awaitility` 또는 `@Async` 동기 실행 모드(`SyncTaskExecutor`)로 비동기 검증

---

## D. Quality Gates (Step별 PASS 기준)

### D.1 Step 1 — REQ-007 admin partial 차단

- 통합 테스트 PASS: AC-007-1 ~ AC-007-6 (총 6건)
- 단위 테스트 커버리지 ≥ 90% (`NoEmailWildcardValidator`, `AdminEmailPartialSearchException`)
- 정적 분석: `Spotbugs`, `Checkstyle` 0건 위반
- LSP: 0 errors, 0 warnings
- DB 슬로우 쿼리 로그 검증: partial 패턴 거부 시 ILIKE 미발생

### D.2 Step 2 — REQ-008 응답 마스킹

- 통합 테스트 PASS: AC-008-1 ~ AC-008-6 (총 6건)
- 단위 테스트 커버리지 ≥ 90% (`EmailMaskSerializer`)
- Java record 호환성 IT 검증: Spring Boot 3.4 + Jackson 2.18+ 환경에서 `@JsonSerialize` 필드/component accessor 어노테이션 정상 인식 (RISK-002-01 대응)
- IDN/이모지 코드 포인트 안전 단위 테스트 PASS (EC-001, AC-008-6)
- SecurityContext null/empty fallback 검증: 마스킹이 보수적 기본값 (RISK-002-02 대응)

### D.3 Step 3 — REQ-009 PII 감사 보강

- 통합 테스트 PASS: AC-009-1 ~ AC-009-6 (총 6건)
- 단위 테스트 커버리지 ≥ 85% (`UserServiceImpl.findPage(actor)` 오버로드, `PersonalDataAccessLogService.recordBulk`)
- 비동기 실행 IT 검증: `Awaitility` 또는 `SyncTaskExecutor`로 AFTER_COMMIT 이벤트 검증 (AC-009-6)
- AOP fallback 검증: Mockito `doThrow`로 INSERT 실패 시뮬레이션 → user-facing 정상 응답 + ERROR 로그 + 카운터 증가 (AC-009-5)
- `PersonalDataAccessPurpose` enum `ADMIN_USER_LIST`, `ADMIN_EMAIL_LOOKUP` 추가 검증

### D.4 Step 4 — ArchUnit 강제

- ArchUnit 테스트 PASS: `PiiEmailMaskArchTest`
  - `UserSummary.email` 필드에 `@JsonSerialize(using = EmailMaskSerializer.class)` 강제
  - `UserDetail.email` 필드에 `@JsonSerialize(using = EmailMaskSerializer.class)` 강제
  - `UserSelf` 예외 (마스킹 미적용 정책 명시)
- 향후 신규 `*UserResponse` DTO 추가 시 동일 규칙 자동 검증 (`@PiiSensitive` 마커 또는 패키지 규칙)
- 기존 SPEC-PII-001 ArchUnit 테스트 회귀 PASS

---

## E. Definition of Done (DoD)

본 SPEC은 다음 6개 체크리스트가 모두 충족될 때 완료(Done) 상태로 간주한다.

- [ ] **DoD-1**: `NoEmailWildcardValidator` + `@NoEmailWildcard` 어노테이션 구현 + Bean Validation 통합, AC-007-1 ~ AC-007-6 PASS, `ADMIN_EMAIL_PARTIAL_FORBIDDEN` 에러 코드 `GlobalExceptionHandler` 매핑 완료
- [ ] **DoD-2**: `EmailMaskSerializer` 구현 + `UserSummary.email`, `UserDetail.email`에 `@JsonSerialize` 적용, AC-008-1 ~ AC-008-6 PASS, 코드 포인트 단위 길이 계산(IDN/이모지 안전), Spring Boot 3.4 + Jackson 2.18+ record 호환성 IT 검증 완료
- [ ] **DoD-3**: `PersonalDataAccessPurpose` enum `ADMIN_USER_LIST`, `ADMIN_EMAIL_LOOKUP` 추가, `UserServiceImpl.findPage(actor)` 오버로드 내 `recordBulk` 직접 호출 또는 `@TransactionalEventListener AFTER_COMMIT` 비동기 적재, AC-009-1 ~ AC-009-6 PASS
- [ ] **DoD-4**: AOP fallback 정책 — INSERT 실패 시 ERROR 로그 + Micrometer `pii.audit.log.failure.count` + 운영 알림 큐 push, user-facing 에러 미전파, AC-009-5 PASS
- [ ] **DoD-5**: ArchUnit 테스트 `PiiEmailMaskArchTest` 작성 — UserSummary/UserDetail email 필드 마스킹 직렬화 강제, UserSelf 예외 명시, D.4 PASS
- [ ] **DoD-6**: SPEC-CMS-SECURITY-PII-001 RUN 1차 회귀 PASS — 신규 가입 → HMAC lookup → 복호화 → 인증 흐름 정상, 기존 `personal_data_access_log` 적재 경로(SPEC-PII-001 RUN 1차 적용분) 영향 없음, V24 마이그레이션 컬럼 무결성 유지

---
