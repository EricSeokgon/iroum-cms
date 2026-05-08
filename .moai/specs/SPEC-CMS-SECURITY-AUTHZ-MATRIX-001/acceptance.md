# SPEC-CMS-SECURITY-AUTHZ-MATRIX-001 — 인수 기준 (Acceptance Criteria)

본 문서는 SPEC-CMS-SECURITY-AUTHZ-MATRIX-001 HTTP 권한 매트릭스 통합 테스트 인프라(운영 SecurityFilterChain + `@PreAuthorize` 회귀 검증)의 Given/When/Then 형식 인수 시나리오와 품질 게이트를 정의한다. 모든 시나리오는 통합 테스트(Testcontainers PostgreSQL 16 + Spring Boot 3.5 `@SpringBootTest` + `@AutoConfigureMockMvc`)로 검증 가능해야 한다.

본 SPEC은 SPEC-CMS-SECURITY-PII-001 RUN 1차 PII 더미 키 인프라와 SPEC-CMS-SECURITY-PII-002의 `SecurityConfigIntegrationTest` DI 패턴을 전제로 하며, 운영 코드 변경 0건이다.

---

## A. REQ-AUTHZ-MATRIX-001 — IT 인프라 신설 + smoke test

### AC-AM-001-1 — `AuthorizationMatrixIT` 컨텍스트 부팅 + JWT Mock 주입

- **Given**: `backend/src/test/java/kr/co/ircp/cms/security/AuthorizationMatrixIT.java`가 신규 생성되며, `@SpringBootTest(webEnvironment = WebEnvironment.MOCK)` + `@AutoConfigureMockMvc` + `@Testcontainers` 어노테이션이 적용됨. `static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")`가 정의됨. `@DynamicPropertySource`로 `spring.datasource.url`/`username`/`password`가 주입됨. `@MockitoBean JwtTokenProvider jwtTokenProvider`, `@MockitoBean TokenBlacklistMapper tokenBlacklistMapper`가 정의됨. PII 더미 키(`pii.keyvault.keys.v1`, `pii.keyvault.hmac-key`)가 `@DynamicPropertySource` 또는 `application-integration-test.yml` 프로필로 주입됨
- **When**: `./gradlew integrationTest --tests AuthorizationMatrixIT` 실행
- **Then**:
  - Spring 컨텍스트 로드 성공 (`ApplicationContext` startup 0 errors)
  - Testcontainers PostgreSQL 16 컨테이너가 실행되어 운영 동등 Flyway 마이그레이션 적용 (V24 포함)
  - 운영 `SecurityConfig` Bean(`SecurityFilterChain`, `JwtAuthenticationFilter`, `PasswordEncoder`)이 적재됨 (Spring 컨텍스트에 존재 검증 가능)
  - `JwtTokenProvider`/`TokenBlacklistMapper`는 Mockito Mock으로 대체되어 실제 DB lookup이 발생하지 않음
  - 부팅 자체가 GREEN(예외 없이 완료)

### AC-AM-001-2 — public endpoint smoke test (인증 미요구)

- **Given**: `AuthorizationMatrixIT` 컨텍스트 부팅 완료 상태. 운영 `SecurityConfig`의 `requestMatchers("/api/v1/health/**").permitAll()` 정책이 적용됨
- **When**: `mockMvc.perform(get("/api/v1/health/live"))` 호출 (Authorization 헤더 부재)
- **Then**:
  - 응답 status는 200 OK 또는 운영 health endpoint가 정의한 정상 status
  - 운영 `JwtAuthenticationFilter`가 chain을 정상 통과 (인증 미요구 경로)
  - smoke test가 GREEN — IT 인프라가 운영 SecurityFilterChain을 적재하고 있음 검증

### AC-AM-001-3 — 보호 endpoint smoke test (Authorization 헤더 부재 → 401)

- **Given**: `AuthorizationMatrixIT` 컨텍스트 부팅 완료 상태. 운영 `SecurityConfig.anyRequest().authenticated()` 정책이 적용됨
- **When**: `mockMvc.perform(post("/api/v1/content/banners").contentType(MediaType.APPLICATION_JSON).content("{}"))` 호출 (Authorization 헤더 부재)
- **Then**:
  - 응답 status는 401 Unauthorized
  - 응답 Content-Type은 `application/json;charset=UTF-8`
  - 응답 body의 `code` 필드는 `"AUTH_REQUIRED"` (운영 `SecurityConfig.authenticationEntryPoint` 라인 113~116 정의)
  - jsonPath 검증: `jsonPath("$.code").value("AUTH_REQUIRED")` GREEN
  - 응답 body에 `message` 필드 존재 (한국어 문구는 비검증 — 회귀 노이즈 회피)
  - 응답 body의 `traceId` 필드는 nullable (검증 미수행)

---

## B. REQ-AUTHZ-MATRIX-002 — WRITE 권한 endpoint 매트릭스 (5~7 endpoint × 2 시나리오)

각 endpoint에 대해 시나리오 A(권한 부족 → 403) + 시나리오 B(정합 권한 → 200/2xx)를 검증한다. JWT Mock 시뮬레이션은 `JwtTestAuth` 헬퍼 또는 인라인 `Mockito.when(jwtTokenProvider.parseClaims(...)).thenReturn(JwtClaims(...))` 패턴을 사용한다.

### AC-AM-002-1 — Banner POST 권한 부족 (403 `CONTENT:WRITE` 부재)

- **Given**: `AuthorizationMatrixIT` 컨텍스트 부팅 완료. `JwtTokenProvider` Mock이 `validateToken("fake-token-user")` → true, `parseClaims("fake-token-user")` → `JwtClaims(userId=1, username="user", roles=List.of("USER"), authorities=List.of(), expiresAt=Instant.now().plusSeconds(3600))` 반환하도록 stub. `TokenBlacklistMapper.existsByToken(any)` → false stub. 운영 `BannerController.create` 메소드는 `@PreAuthorize("hasAuthority('CONTENT:WRITE')")` 정책 적용
- **When**: `mockMvc.perform(post("/api/v1/content/banners").header("Authorization", "Bearer fake-token-user").contentType(JSON).content("{...}"))` 호출
- **Then**:
  - 응답 status는 403 Forbidden
  - 응답 body의 `code` 필드는 `"AUTH_FORBIDDEN"` (운영 `SecurityConfig.accessDeniedHandler` 라인 121~123 정의)
  - jsonPath 검증: `jsonPath("$.code").value("AUTH_FORBIDDEN")` GREEN
  - 운영 `JwtAuthenticationFilter`가 SecurityContext에 authenticated principal을 설정한 뒤 `@PreAuthorize` 인터셉터가 `AccessDeniedException`을 throw하고 `accessDeniedHandler`가 응답을 작성

### AC-AM-002-2 — Banner POST 정합 권한 (200/2xx `CONTENT:WRITE` 보유)

- **Given**: `JwtTokenProvider` Mock이 `parseClaims("fake-token-content")` → `JwtClaims(userId=2, username="editor", roles=List.of("EDITOR"), authorities=List.of("CONTENT:WRITE"), expiresAt=future)` 반환 stub
- **When**: `mockMvc.perform(post("/api/v1/content/banners").header("Authorization", "Bearer fake-token-content").contentType(JSON).content(validBannerBody))` 호출
- **Then**:
  - 응답 status는 200 OK 또는 201 Created (운영 컨트롤러 정의에 따름)
  - 401 또는 403 응답이 아님 (status 200~299 범위)
  - 응답 본문 내용 검증은 본 SPEC 비범위 — status code 검증만 수행

### AC-AM-002-3 — Banner PUT 권한 부족 (403 `CONTENT:WRITE` 부재)

- **Given**: AC-AM-002-1과 동일 USER 권한 stub
- **When**: `mockMvc.perform(put("/api/v1/content/banners/1").header("Authorization", "Bearer fake-token-user").contentType(JSON).content("{}"))` 호출
- **Then**:
  - 응답 status는 403, body의 `code` 필드는 `"AUTH_FORBIDDEN"`

### AC-AM-002-4 — Banner PUT 정합 권한 (200/2xx)

- **Given**: AC-AM-002-2와 동일 `CONTENT:WRITE` stub
- **When**: `mockMvc.perform(put("/api/v1/content/banners/1").header("Authorization", "Bearer fake-token-content").contentType(JSON).content(validUpdateBody))` 호출
- **Then**:
  - 응답 status는 200/2xx (404 가능 — 존재하지 않는 banner — 도 정상 권한 통과 신호로 허용. status 401/403 아님이 핵심 검증)
  - **권한 통과 검증 보강**: status 401/403가 아닌 다른 모든 status(200, 204, 404 등)는 GREEN으로 간주

### AC-AM-002-5 — Page POST 권한 부족 (403 `PAGE:WRITE` 부재)

- **Given**: `JwtTokenProvider` Mock이 `parseClaims("fake-token-content")` → `JwtClaims(authorities=List.of("CONTENT:WRITE"), ...)` 반환 stub (`PAGE:WRITE`는 부재)
- **When**: `mockMvc.perform(post("/api/v1/content/pages").header("Authorization", "Bearer fake-token-content").contentType(JSON).content("{}"))` 호출
- **Then**:
  - 응답 status는 403, body의 `code` 필드는 `"AUTH_FORBIDDEN"`
  - `CONTENT:WRITE`와 `PAGE:WRITE`가 별개 권한 어휘임을 검증 (운영 권한 매트릭스 회귀)

### AC-AM-002-6 — Page POST 정합 권한 (200/2xx `PAGE:WRITE` 보유)

- **Given**: `JwtTokenProvider` Mock이 `parseClaims("fake-token-page")` → `JwtClaims(authorities=List.of("PAGE:WRITE"), ...)` 반환 stub
- **When**: `mockMvc.perform(post("/api/v1/content/pages").header("Authorization", "Bearer fake-token-page").contentType(JSON).content(validPageBody))` 호출
- **Then**:
  - 응답 status는 200/2xx (또는 권한 통과를 의미하는 401/403 외 status)

### AC-AM-002-7 — CacheAdmin invalidate 권한 부족 (403 `hasAnyRole('SUPER_ADMIN','DEPT_ADMIN')` 미충족)

- **Given**: `JwtTokenProvider` Mock이 `parseClaims("fake-token-user")` → `JwtClaims(roles=List.of("USER"), authorities=List.of(), ...)` 반환 stub. 운영 `CacheAdminController.invalidate`는 `@PreAuthorize("hasAnyRole('SUPER_ADMIN','DEPT_ADMIN')")` 정책 적용 (라인 27)
- **When**: `mockMvc.perform(post("/api/v1/dashboard/cache/invalidate").header("Authorization", "Bearer fake-token-user").contentType(JSON).content("{}"))` 호출
- **Then**:
  - 응답 status는 403, body의 `code` 필드는 `"AUTH_FORBIDDEN"`
  - `USER` 역할은 `SUPER_ADMIN`/`DEPT_ADMIN` 중 어느 것도 아님 → 정책 미충족 검증

### AC-AM-002-8 — CacheAdmin invalidate 정합 권한 (200/2xx `SUPER_ADMIN` 역할 보유)

- **Given**: `JwtTokenProvider` Mock이 `parseClaims("fake-token-super")` → `JwtClaims(roles=List.of("SUPER_ADMIN"), authorities=List.of(), ...)` 반환 stub
- **When**: `mockMvc.perform(post("/api/v1/dashboard/cache/invalidate").header("Authorization", "Bearer fake-token-super").contentType(JSON).content(validBody))` 호출
- **Then**:
  - 응답 status는 200/2xx (또는 권한 통과 status)

### AC-AM-002-9 — User 등록 권한 부족 (403 `hasRole('SUPER_ADMIN')` 미충족)

- **Given**: `JwtTokenProvider` Mock이 `parseClaims` → `JwtClaims(roles=List.of("ADMIN"), ...)` 반환 stub. 운영 `UserController` 관리자 등록 endpoint는 `@PreAuthorize("hasRole('SUPER_ADMIN')")` 정책 적용 (라인 83)
- **When**: 운영 SUPER_ADMIN 전용 endpoint 호출 (예: `POST` 또는 관리 endpoint)
- **Then**:
  - 응답 status는 403, body의 `code` 필드는 `"AUTH_FORBIDDEN"`
  - `ADMIN` 역할이 `SUPER_ADMIN` 정책을 충족하지 않음을 검증 (역할 위계 회귀)

### AC-AM-002-10 — User 등록 정합 권한 (200/2xx `SUPER_ADMIN` 역할)

- **Given**: `JwtTokenProvider` Mock이 `JwtClaims(roles=List.of("SUPER_ADMIN"), ...)` 반환 stub
- **When**: 동일 SUPER_ADMIN 전용 endpoint 호출 + 적합 토큰
- **Then**:
  - 응답 status는 200/2xx (또는 권한 통과 status)

### AC-AM-002-11 — Governance 클래스 레벨 ADMIN 권한 부족 (403 `hasRole('ADMIN')` 미충족)

- **Given**: `JwtTokenProvider` Mock이 `JwtClaims(roles=List.of("USER"), ...)` 반환 stub. 운영 `RetentionPolicyController` 또는 `DataQualityController`는 클래스 레벨 `@PreAuthorize("hasRole('ADMIN')")` 정책 적용
- **When**: governance endpoint 호출 (예: `GET /api/v1/governance/retention-policies` 또는 동등)
- **Then**:
  - 응답 status는 403, body의 `code` 필드는 `"AUTH_FORBIDDEN"`
  - 클래스 레벨 `@PreAuthorize`가 운영 컨텍스트에서 정상 작동 검증

### AC-AM-002-12 — Governance 클래스 레벨 ADMIN 정합 권한 (200/2xx)

- **Given**: `JwtTokenProvider` Mock이 `JwtClaims(roles=List.of("ADMIN"), ...)` 반환 stub
- **When**: 동일 governance endpoint 호출
- **Then**:
  - 응답 status는 200/2xx

---

## C. REQ-AUTHZ-MATRIX-003 — 401/403/200 표준화 + 응답 body 회귀 검증

### AC-AM-003-1 — 401 응답 body 회귀 검증 (`AUTH_REQUIRED` JSON 형식)

- **Given**: `AuthorizationMatrixIT` 컨텍스트 부팅 완료. 보호 endpoint(예: `POST /api/v1/content/banners`)가 정의됨
- **When**: `Authorization` 헤더 부재 상태로 보호 endpoint 호출
- **Then**:
  - 응답 status는 401 Unauthorized
  - 응답 Content-Type은 정확히 `application/json;charset=UTF-8` (운영 `authenticationEntryPoint` 라인 113 명시 형식)
  - 응답 body 파싱 결과:
    - `code` 필드 = `"AUTH_REQUIRED"` (회귀 검증)
    - `message` 필드 존재 (한국어 문구는 비검증)
    - `traceId` 필드 존재 (값은 비검증 — null 또는 nullable)
  - jsonPath 매처:
    - `jsonPath("$.code").value("AUTH_REQUIRED")` GREEN
    - `jsonPath("$.message").exists()` GREEN

### AC-AM-003-2 — 403 응답 body 회귀 검증 (`AUTH_FORBIDDEN` JSON 형식)

- **Given**: AC-AM-003-1과 동일 컨텍스트. `JwtTokenProvider` Mock이 권한 부족 `JwtClaims` 반환 stub
- **When**: 유효 JWT(`Bearer fake-token`) 헤더 + 권한 부족 상태로 보호 endpoint 호출
- **Then**:
  - 응답 status는 403 Forbidden
  - 응답 Content-Type은 정확히 `application/json;charset=UTF-8` (운영 `accessDeniedHandler` 라인 121 명시 형식)
  - 응답 body 파싱 결과:
    - `code` 필드 = `"AUTH_FORBIDDEN"` (회귀 검증)
    - `message` 필드 존재
    - `traceId` 필드 존재
  - jsonPath 매처:
    - `jsonPath("$.code").value("AUTH_FORBIDDEN")` GREEN
    - `jsonPath("$.message").exists()` GREEN

### AC-AM-003-3 — JwtAuthenticationFilter 운영 적재 검증 (401 경로 EntryPoint 호출 통과)

- **Given**: `AuthorizationMatrixIT` 컨텍스트 부팅 완료. `JwtAuthenticationFilter`는 운영 Bean으로 적재됨 (Mock 아님)
- **When**: `Authorization` 헤더 부재 + 보호 endpoint 호출
- **Then**:
  - 응답 status 401 + body `code=AUTH_REQUIRED`로 도달함 (AC-AM-003-1 결과와 동일)
  - 이는 운영 `JwtAuthenticationFilter`가 chain에 정상 적재되어 anonymous 흐름이 `ExceptionTranslationFilter`까지 전달되었음을 간접 검증
  - 별도 spy 또는 빈 검증으로 `JwtAuthenticationFilter` 적재를 추가 검증 가능 (선택적)

### AC-AM-003-4 — Method Security 운영 인터셉터 적재 검증 (403 경로 `@PreAuthorize` 호출 통과)

- **Given**: `JwtTokenProvider` Mock이 권한 부족 `JwtClaims` stub
- **When**: 유효 토큰 + 권한 부족 상태로 `@PreAuthorize` 적용 endpoint 호출
- **Then**:
  - 응답 status 403 + body `code=AUTH_FORBIDDEN`로 도달함
  - 이는 운영 `@EnableMethodSecurity(prePostEnabled = true)` 어노테이션이 `SecurityConfig` 클래스에 적용되어 `@PreAuthorize` 인터셉터가 정상 작동함을 간접 검증

---

## D. Quality Gates

### D.1 통합 테스트 GREEN

- `./gradlew integrationTest --tests AuthorizationMatrixIT` 실행 시 모든 시나리오(A.AC-AM-001-1 ~ C.AC-AM-003-4) GREEN
- `./gradlew integrationTest` 전체 실행 시 회귀 0건 (기존 IT 24개 + 본 SPEC 신규 IT 모두 GREEN)

### D.2 단위 테스트 회귀 0건

- `./gradlew test` 전체 GREEN — 기존 단위 50건 + ArchUnit 5건 영향 없음

### D.3 운영 코드 git diff 0건 강제

- `git diff --stat backend/src/main/` 출력은 0줄 (운영 코드 변경 없음)
- 본 SPEC 적용 commit은 `backend/src/test/` 경로만 수정해야 함
- `backend/src/main/java/kr/co/ircp/cms/config/SecurityConfig.java` 수정 0건
- `backend/src/main/java/kr/co/ircp/cms/security/JwtAuthenticationFilter.java` 수정 0건

### D.4 LSP 0 errors

- IT 클래스 컴파일 0 errors, 0 warnings
- ArchUnit 규칙(존재 시) 회귀 0건

### D.5 응답 형식 회귀 기준선 고정

- `code=AUTH_REQUIRED`/`code=AUTH_FORBIDDEN` 회귀 검증 PASS (D.1 포함되나 강조)
- 향후 `SecurityConfig` 응답 body 형식 변경 PR 시 본 SPEC IT가 회귀 신호로 작동하여 PR 머지를 차단해야 함 (정상 동작)

### D.6 시나리오 커버리지

- 1차 5~7 endpoint × 2 시나리오(403/200) ≥ 10건 (AC-AM-002-1 ~ AC-AM-002-12 중 6 endpoint × 2 = 12건 권장)
- + 401 시나리오 1건(AC-AM-003-1)
- + 인프라 smoke test 3건(AC-AM-001-1/2/3)
- + 응답 body 회귀 검증 4건(AC-AM-003-1 ~ AC-AM-003-4)
- 총 IT 케이스 수 ≥ 18건 (1차 권장)

---

## E. Definition of Done

본 SPEC은 다음 조건을 모두 만족할 때 RUN 1차 완료로 간주한다.

1. `AuthorizationMatrixIT` 신규 IT 클래스가 활성화되어 부팅 가능 상태 (AC-AM-001-1 ~ AC-AM-001-3 GREEN)
2. WRITE 권한 endpoint 매트릭스 5~7건 × 2 시나리오 ≥ 12개 IT 케이스 GREEN (AC-AM-002-1 ~ AC-AM-002-12)
3. 401/403 응답 body 회귀 검증 4건 GREEN (AC-AM-003-1 ~ AC-AM-003-4)
4. 운영 코드 변경 0건 (D.3 강제)
5. 전체 IT/단위 테스트 회귀 0건 (D.1, D.2)
6. LSP 0 errors (D.4)
7. 5/7 코드 리뷰 C1 항목에 본 SPEC 정밀화 결과 cross-reference 추가 (별도 PR 또는 본 SPEC sync 시)
8. 27 컨트롤러 메소드 레벨 isForbidden 보강은 본 SPEC 비범위 — 별도 SPEC `SPEC-CMS-SECURITY-CTRL-AUTHZ-COVERAGE-001`(가칭)로 후속 명시
9. 사용자 IT 실행 안내 (Java 17 환경): `./gradlew :backend:integrationTest --tests "kr.co.ircp.cms.security.AuthorizationMatrixIT"` (본 SPEC 작성 세션에서는 Java 17 미설치로 IT 실행 검증을 사용자 환경 위임)

---
