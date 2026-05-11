# SPEC-CMS-SECURITY-AUTHZ-IT-EXPAND-001 — 인수 기준 (Acceptance Criteria)

본 문서는 SPEC-CMS-SECURITY-AUTHZ-IT-EXPAND-001 HTTP 권한 매트릭스 IT 확장(AUTHZ-MATRIX-001 6 endpoint → 30 endpoint, 12 권한 어휘 회귀 검출)의 Given/When/Then 형식 인수 시나리오와 품질 게이트를 정의한다. 모든 시나리오는 통합 테스트(Testcontainers PostgreSQL 16 + Spring Boot 3.5 `@SpringBootTest` + `@AutoConfigureMockMvc`)로 검증 가능해야 한다.

본 SPEC은 SPEC-CMS-SECURITY-AUTHZ-MATRIX-001 v0.2 IT 인프라(`AuthorizationMatrixIT` + `JwtTestAuth.givenValidToken` + PII 더미 키 + Testcontainers PostgreSQL 16)를 그대로 재사용하며, 운영 코드 변경 0건이다.

각 endpoint는 3 시나리오(401 미인증 / 403 권한 부족 / 200/2xx 정상)로 검증되며, 권한 어휘 12종 모두 최소 1 endpoint 회귀 검출이 보장된다. `isAuthenticated()` 어휘는 권한 무관 정책으로 403 시나리오가 N/A이며, 401 vs 200 두 시나리오로만 검증한다.

---

## A. REQ-AM-EXP-001 — 30 endpoint × 3 시나리오 매트릭스 (~90 AC)

본 영역은 도메인별 `@Nested` 그룹 6개로 분해된다. 각 그룹은 권한 어휘 2~3개를 커버하며 endpoint 수는 도메인별 분포에 따라 가변적이다.

### §A.1 ContentDomainTests (~7 endpoint × 3 = ~21 AC)

권한 어휘 커버: `CONTENT:WRITE` (다른 컨트롤러 1건), `PAGE:WRITE` (1건), `PAGE:PUBLISH` (3건), `TEMPLATE:WRITE` (2건).

#### AC-AME-001-A1 — Banner GET (READ — `CONTENT:READ` 또는 인증 정책) 401 미인증

- **Given**: `AuthorizationMatrixExpandIT` 컨텍스트 부팅 완료. 운영 컨트롤러 endpoint(예: `BannerListController.list` 또는 동등 — Step 1 정밀 선정 후 확정) 적용
- **When**: `Authorization` 헤더 없이 호출
- **Then**: 응답 status 401 + body `code=AUTH_REQUIRED`

#### AC-AME-001-A2 — Banner READ 정상 권한 (200/2xx)

- **Given**: `JwtTestAuth.givenValidToken(roles=List.of("USER"), permissions=List.of("CONTENT:READ"))` stub
- **When**: `Authorization: Bearer fake-token` + READ endpoint 호출
- **Then**: 응답 status 200/2xx (또는 401/403 외 status)

#### AC-AME-001-A3 — Page Publish 권한 부족 (403 `PAGE:PUBLISH` 부재)

- **Given**: `JwtTestAuth.givenValidToken(roles=List.of("EDITOR"), permissions=List.of("PAGE:WRITE"))` stub (PAGE:PUBLISH 부재)
- **When**: `mockMvc.perform(post("/api/v1/content/pages/{id}/publish"...).header("Authorization", "Bearer fake-token-page-write"))` 호출
- **Then**: 응답 status 403 + body `code=AUTH_FORBIDDEN`. `PAGE:WRITE`와 `PAGE:PUBLISH`가 별개 권한 어휘임을 검증

#### AC-AME-001-A4 — Page Publish 정합 권한 (200/2xx `PAGE:PUBLISH` 보유)

- **Given**: `givenValidToken(roles=[], permissions=List.of("PAGE:PUBLISH"))` stub
- **When**: 동일 publish endpoint 호출
- **Then**: 응답 status 200/2xx

#### AC-AME-001-A5 — Template POST 권한 부족 (403 `TEMPLATE:WRITE` 부재)

- **Given**: `givenValidToken(roles=[], permissions=List.of("CONTENT:WRITE"))` stub (TEMPLATE:WRITE 부재)
- **When**: `mockMvc.perform(post("/api/v1/content/templates")...)` 호출
- **Then**: 응답 status 403 + body `code=AUTH_FORBIDDEN`

#### AC-AME-001-A6 — Template POST 정합 권한 (200/2xx `TEMPLATE:WRITE`)

- **Given**: `givenValidToken(roles=[], permissions=List.of("TEMPLATE:WRITE"))` stub
- **When**: 동일 endpoint 호출
- **Then**: 응답 status 200/2xx (또는 권한 통과 status)

### §A.2 BlockDomainTests — `BLOCK:WRITE` 권한 어휘 (~2 endpoint × 3 = ~6 AC)

#### AC-AME-001-B1 — ContentBlock POST 권한 부족 (403 `BLOCK:WRITE` 부재)

- **Given**: `givenValidToken(permissions=List.of("CONTENT:WRITE"))` stub (BLOCK:WRITE 부재)
- **When**: `mockMvc.perform(post("/api/v1/content/blocks")...)` 호출 (운영 컨트롤러 정확 경로는 Step 1 확정)
- **Then**: 응답 status 403 + body `code=AUTH_FORBIDDEN`

#### AC-AME-001-B2 — ContentBlock POST 정합 권한 (200/2xx `BLOCK:WRITE`)

- **Given**: `givenValidToken(permissions=List.of("BLOCK:WRITE"))` stub
- **When**: 동일 endpoint 호출
- **Then**: 응답 status 200/2xx

### §A.3 DashboardDomainTests (~3 endpoint × 3 = ~9 AC)

권한 어휘 커버: `hasAnyRole('SUPER_ADMIN','DEPT_ADMIN')` (다른 endpoint 1건), `SYSTEM:STATS` (2건).

#### AC-AME-001-D1 — Stats endpoint 권한 부족 (403 `SYSTEM:STATS` 부재)

- **Given**: `givenValidToken(roles=List.of("USER"), permissions=List.of())` stub
- **When**: `mockMvc.perform(get("/api/v1/dashboard/stats")...)` 또는 동등 호출
- **Then**: 응답 status 403 + body `code=AUTH_FORBIDDEN`

#### AC-AME-001-D2 — Stats endpoint 정합 권한 (200/2xx `SYSTEM:STATS`)

- **Given**: `givenValidToken(permissions=List.of("SYSTEM:STATS"))` stub
- **When**: 동일 endpoint 호출
- **Then**: 응답 status 200/2xx

#### AC-AME-001-D3 — multi-role `hasAnyRole` 부분 매칭 (200 DEPT_ADMIN 보유)

- **Given**: `givenValidToken(roles=List.of("DEPT_ADMIN"))` stub. 운영 정책 `@PreAuthorize("hasAnyRole('SUPER_ADMIN','DEPT_ADMIN')")` 적용된 dashboard 관련 endpoint
- **When**: 해당 endpoint 호출
- **Then**: 응답 status 200/2xx (multi-role 부분 매칭 검증 — DEPT_ADMIN 단독으로 통과)

### §A.4 AuthDomainTests (~4 endpoint × 3 = ~12 AC, isAuthenticated 어휘 N/A 제외)

권한 어휘 커버: `hasRole('SUPER_ADMIN')` (다른 endpoint 2건), `isAuthenticated()` (2건 — 401 vs 200만).

#### AC-AME-001-U1 — User force-logout 권한 부족 (403 SUPER_ADMIN 부재)

- **Given**: `givenValidToken(roles=List.of("ADMIN"))` stub (SUPER_ADMIN 부재)
- **When**: `mockMvc.perform(post("/api/v1/auth/users/{id}/force-logout")...)` 또는 동등 SUPER_ADMIN 전용 endpoint 호출
- **Then**: 응답 status 403 + body `code=AUTH_FORBIDDEN`

#### AC-AME-001-U2 — User force-logout 정합 권한 (200/2xx SUPER_ADMIN)

- **Given**: `givenValidToken(roles=List.of("SUPER_ADMIN"))` stub
- **When**: 동일 endpoint 호출
- **Then**: 응답 status 200/2xx

#### AC-AME-001-U3 — Me endpoint 401 미인증 (`isAuthenticated()` 어휘)

- **Given**: 운영 `@PreAuthorize("isAuthenticated()")` 적용된 endpoint(예: `/api/v1/me` 또는 동등)
- **When**: `Authorization` 헤더 없이 호출
- **Then**: 응답 status 401 + body `code=AUTH_REQUIRED`

#### AC-AME-001-U4 — Me endpoint 200 인증 통과 (`isAuthenticated()` 어휘 — 권한 무관)

- **Given**: `givenValidToken(roles=List.of("USER"), permissions=List.of())` stub (어떤 권한도 없음)
- **When**: `Authorization: Bearer fake-token` + `/api/v1/me` 호출
- **Then**: 응답 status 200/2xx. **403 시나리오는 N/A** (권한 무관 정책)

### §A.5 SystemDomainTests (~5 endpoint × 3 = ~15 AC)

권한 어휘 커버: `SYSTEM:CODE:READ` (2건), `SYSTEM:CODE:WRITE` (3건).

#### AC-AME-001-S1 — Code list 권한 부족 (403 `SYSTEM:CODE:READ` 부재)

- **Given**: `givenValidToken(roles=List.of("USER"), permissions=List.of())` stub
- **When**: `mockMvc.perform(get("/api/v1/system/codes")...)` 호출
- **Then**: 응답 status 403 + body `code=AUTH_FORBIDDEN`

#### AC-AME-001-S2 — Code list 정합 권한 (200 `SYSTEM:CODE:READ`)

- **Given**: `givenValidToken(permissions=List.of("SYSTEM:CODE:READ"))` stub
- **When**: 동일 endpoint 호출
- **Then**: 응답 status 200/2xx

#### AC-AME-001-S3 — Code POST 권한 부족 (403 `SYSTEM:CODE:WRITE` 부재 — READ만 보유)

- **Given**: `givenValidToken(permissions=List.of("SYSTEM:CODE:READ"))` stub (WRITE 부재)
- **When**: `mockMvc.perform(post("/api/v1/system/codes")...)` 호출
- **Then**: 응답 status 403 + body `code=AUTH_FORBIDDEN`. `SYSTEM:CODE:READ`와 `SYSTEM:CODE:WRITE`가 별개 권한 어휘임을 검증 (권한 어휘 분리 회귀)

#### AC-AME-001-S4 — Code POST 정합 권한 (200 `SYSTEM:CODE:WRITE`)

- **Given**: `givenValidToken(permissions=List.of("SYSTEM:CODE:WRITE"))` stub
- **When**: 동일 endpoint 호출
- **Then**: 응답 status 200/2xx

#### AC-AME-001-S5 — Code PUT 권한 부족 + 정합 (403 + 200/2xx)

- 401 시나리오, 403 시나리오, 200 시나리오 각 1건씩 — `SYSTEM:CODE:WRITE` 어휘 multi-endpoint 검증

### §A.6 GovernanceDomainTests (~3 endpoint × 3 = ~9 AC)

권한 어휘 커버: `hasRole('ADMIN')` (다른 endpoint 2건 — `RetentionPolicy` 외 `DataQuality`), `CONTENT:WRITE` (다른 컨트롤러 1건).

#### AC-AME-001-G1 — DataQuality 관리 endpoint 권한 부족 (403 ADMIN 부재)

- **Given**: `givenValidToken(roles=List.of("USER"))` stub
- **When**: `mockMvc.perform(post("/api/v1/governance/data-quality/...")...)` 또는 동등 ADMIN 전용 endpoint 호출
- **Then**: 응답 status 403 + body `code=AUTH_FORBIDDEN`. 클래스 레벨 `@PreAuthorize("hasRole('ADMIN')")` 회귀 검증

#### AC-AME-001-G2 — DataQuality 관리 endpoint 정합 권한 (200 ADMIN)

- **Given**: `givenValidToken(roles=List.of("ADMIN"))` stub
- **When**: 동일 endpoint 호출
- **Then**: 응답 status 200/2xx

#### AC-AME-001-G3 — RecoveryDrill 또는 Retention READ ADMIN 시나리오

- 401, 403, 200 시나리오 각 1건씩 — `hasRole('ADMIN')` multi-endpoint 회귀 검증

### §A.7 BoardMenuDomainTests (~5 endpoint × 3 = ~15 AC)

권한 어휘 커버: `MENU:WRITE` (3건), 추가 어휘(`hasRole('ADMIN')` 또는 `CONTENT:WRITE` board 외 컨트롤러 보강 2건).

#### AC-AME-001-M1 — Menu POST 권한 부족 (403 `MENU:WRITE` 부재)

- **Given**: `givenValidToken(permissions=List.of("CONTENT:WRITE"))` stub (MENU:WRITE 부재)
- **When**: `mockMvc.perform(post("/api/v1/menus")...)` 호출 (정확 경로 Step 1 확정)
- **Then**: 응답 status 403 + body `code=AUTH_FORBIDDEN`

#### AC-AME-001-M2 — Menu POST 정합 권한 (200 `MENU:WRITE`)

- **Given**: `givenValidToken(permissions=List.of("MENU:WRITE"))` stub
- **When**: 동일 endpoint 호출
- **Then**: 응답 status 200/2xx

#### AC-AME-001-M3 — Menu PUT 권한 부족 + 정합 (403 + 200/2xx)

- 추가 endpoint 분포 — `MENU:WRITE` 어휘 multi-endpoint 회귀 검증

#### AC-AME-001-M4 — Menu DELETE 권한 부족 + 정합 (403 + 200/2xx)

- `MENU:WRITE` 어휘 3 endpoint 분포 보강 — endpoint 다양성 회귀 신호

### §A.8 인증 매트릭스 401 종합 (모든 30 endpoint 공통)

각 endpoint의 401 시나리오 (Authorization 헤더 부재 → 401 `AUTH_REQUIRED`)는 §A.1~A.7에 분산되어 있으나, 공통 패턴으로 다음을 보장한다.

- 30 endpoint 각각에 대해 401 시나리오 1건 ≥ 30 AC
- 모든 401 응답은 운영 `SecurityConfig.authenticationEntryPoint` 정의에 의해 `code=AUTH_REQUIRED` 반환
- `AnonymousAuthenticationFilter` → `ExceptionTranslationFilter` 경로 통과 검증

---

## B. REQ-AM-EXP-002 — `AuthorizationMatrixExpandIT` 인프라 신설

### AC-AME-002-1 — `AuthorizationMatrixExpandIT` 컨텍스트 부팅 + JWT Mock 주입

- **Given**: `backend/src/test/java/kr/co/ircp/cms/security/AuthorizationMatrixExpandIT.java`가 신규 생성됨. AUTHZ-MATRIX-001 `AuthorizationMatrixIT`와 동일 어노테이션 구성: `@SpringBootTest(webEnvironment = WebEnvironment.MOCK)` + `@AutoConfigureMockMvc` + `@Testcontainers` + `@ActiveProfiles("integration-test")`. `static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")` 정의. `@MockitoBean JwtTokenProvider` + `@MockitoBean TokenBlacklistMapper` 정의. PII 더미 키(`pii.keyvault.keys.v1`, `pii.keyvault.hmac-key`) 주입
- **When**: `./gradlew :backend:integrationTest --tests "kr.co.ircp.cms.security.AuthorizationMatrixExpandIT"` 실행
- **Then**:
  - Spring 컨텍스트 로드 성공 (`ApplicationContext` startup 0 errors)
  - Testcontainers PostgreSQL 16 컨테이너 실행 + Flyway V24 포함 마이그레이션 적용
  - 운영 `SecurityConfig` Bean(`SecurityFilterChain`, `JwtAuthenticationFilter`, `PasswordEncoder`) 적재 검증 가능
  - `JwtTokenProvider`/`TokenBlacklistMapper` Mockito Mock으로 대체
  - 부팅 자체가 GREEN (예외 없이 완료)

### AC-AME-002-2 — `JwtTestAuth.givenValidToken` helper 재사용 검증

- **Given**: AUTHZ-MATRIX-001 RUN 1차에 신설된 `JwtTestAuth.givenValidToken(roles, permissions)` helper가 `backend/src/test/java/kr/co/ircp/cms/security/JwtTestAuth.java`에 존재
- **When**: `AuthorizationMatrixExpandIT`의 임의 시나리오에서 `JwtTestAuth.givenValidToken(roles=List.of("EDITOR"), permissions=List.of("PAGE:PUBLISH"))` 호출
- **Then**:
  - `JwtTokenProvider` Mock이 `JwtClaims(roles=List.of("EDITOR"), authorities=List.of("PAGE:PUBLISH"), ...)` 반환하도록 stub됨
  - 후속 `mockMvc.perform(...).header("Authorization", "Bearer ...")` 호출 시 운영 `JwtAuthenticationFilter`가 SecurityContext에 authenticated principal 설정
  - 운영 `JwtPrincipal.getAuthorities()` ROLE_ prefix 처리(AUTHZ-MATRIX-001 RUN 1차 정적 검증 완료)가 정상 작동

### AC-AME-002-3 — 도메인별 `@Nested` 그룹화 검증 (D2 + D4 채택)

- **Given**: `AuthorizationMatrixExpandIT.java` 클래스 본문
- **When**: 클래스 정의 검사
- **Then**:
  - 6개 `@Nested` inner class 존재: `ContentDomainTests`, `BlockDomainTests` 또는 통합 `ContentBlockDomainTests`, `DashboardDomainTests`, `AuthDomainTests`, `SystemDomainTests`, `GovernanceDomainTests`, `BoardMenuDomainTests` (그룹 5~6개 — 분포에 따라 가변)
  - 각 `@Nested` 그룹별 `@Test` 메소드 ≥ 6 (3 시나리오 × 2 endpoint 평균)
  - 클래스 헤더 JavaDoc에 30 endpoint × 12 권한 어휘 분포 표 명시

### AC-AME-002-4 — AUTHZ-MATRIX-001과의 검증 레이어 분리 명시

- **Given**: `AuthorizationMatrixExpandIT.java` 클래스 헤더 JavaDoc
- **When**: 문서 내용 검사
- **Then**:
  - JavaDoc에 다음 명시: "본 IT는 AUTHZ-MATRIX-001 `AuthorizationMatrixIT`와 검증 레이어가 동일하나(`@SpringBootTest`), endpoint 커버리지를 6 → 30으로 확장하여 12 권한 어휘 모두 회귀 검출 보장"
  - JavaDoc에 다음 명시: "메소드 레벨 슬라이스 회귀(`@WebMvcTest`)는 SPEC-CMS-SECURITY-CTRL-AUTHZ-COVERAGE-001 영역으로 본 IT와 직교"
  - AUTHZ-MATRIX-001 6 endpoint와 중복 endpoint 0건 (Step 1 정밀 선정 시 검증)

---

## C. REQ-AM-EXP-003 — 12 권한 어휘 회귀 검출

본 영역은 권한 어휘 12종 각각에 대한 회귀 검출 보장을 검증한다.

### AC-AME-003-1 — `hasRole('SUPER_ADMIN')` 어휘 회귀 검출

- **Given**: 본 IT에서 `hasRole('SUPER_ADMIN')` 정책이 적용된 endpoint ≥ 1건 (예: §A.4 AC-AME-001-U1/U2)
- **When**: 운영 컨트롤러에서 `@PreAuthorize("hasRole('SUPER_ADMIN')")`을 다른 어휘로 변경 또는 제거하는 가설 PR 시뮬레이션
- **Then**: 본 IT의 해당 시나리오가 RED 신호 발생 — 회귀 검출 가능

### AC-AME-003-2 — `hasRole('ADMIN')` 어휘 회귀 검출

- **Given**: §A.6 AC-AME-001-G1/G2/G3 등 ADMIN 정책 endpoint
- **When**: 어휘 변경/제거 가설 PR
- **Then**: RED 신호 발생

### AC-AME-003-3 — `hasAnyRole('SUPER_ADMIN','DEPT_ADMIN')` multi-role 회귀 검출

- **Given**: §A.3 AC-AME-001-D3 등 multi-role 정책 endpoint
- **When**: `hasAnyRole`을 `hasRole('SUPER_ADMIN')`으로 단축하는 가설 PR (DEPT_ADMIN 회귀)
- **Then**: AC-AME-001-D3가 RED 발생 (DEPT_ADMIN 토큰이 200 → 403로 변경)

### AC-AME-003-4 — `hasAuthority('CONTENT:WRITE')` 어휘 회귀 검출

- **Given**: §A.1 또는 §A.6의 다른 컨트롤러 `CONTENT:WRITE` 정책 endpoint
- **When**: 어휘 변경 가설 PR
- **Then**: RED 신호 발생

### AC-AME-003-5 — `hasAuthority('PAGE:WRITE')` + `PAGE:PUBLISH` 어휘 분리 회귀 검출

- **Given**: §A.1 AC-AME-001-A3/A4 등 PAGE 분리 정책 endpoint
- **When**: `PAGE:PUBLISH` 정책을 `PAGE:WRITE`로 변경하는 가설 PR
- **Then**: AC-AME-001-A3가 RED 발생 (`PAGE:WRITE`만 보유한 토큰이 403 → 200으로 변경 — 회귀 신호)

### AC-AME-003-6 — `hasAuthority('SYSTEM:CODE:READ')` 어휘 회귀 검출

- **Given**: §A.5 AC-AME-001-S1/S2 endpoint
- **When**: 어휘 변경 가설 PR
- **Then**: RED 신호 발생

### AC-AME-003-7 — `hasAuthority('SYSTEM:CODE:WRITE')` 어휘 회귀 검출

- **Given**: §A.5 AC-AME-001-S3/S4/S5 endpoint
- **When**: WRITE 정책을 READ로 변경하는 가설 PR
- **Then**: AC-AME-001-S3가 RED 발생 (READ 토큰이 403 → 200으로 변경)

### AC-AME-003-8 — `hasAuthority('SYSTEM:STATS')` 어휘 회귀 검출

- **Given**: §A.3 AC-AME-001-D1/D2 endpoint
- **When**: 어휘 변경 가설 PR
- **Then**: RED 신호 발생

### AC-AME-003-9 — `hasAuthority('MENU:WRITE')` 어휘 회귀 검출

- **Given**: §A.7 AC-AME-001-M1~M4 endpoint
- **When**: 어휘 변경 가설 PR
- **Then**: RED 신호 발생

### AC-AME-003-10 — `hasAuthority('BLOCK:WRITE')` 어휘 회귀 검출

- **Given**: §A.2 AC-AME-001-B1/B2 endpoint
- **When**: 어휘 변경 가설 PR
- **Then**: RED 신호 발생

### AC-AME-003-11 — `hasAuthority('TEMPLATE:WRITE')` 어휘 회귀 검출

- **Given**: §A.1 AC-AME-001-A5/A6 endpoint
- **When**: 어휘 변경 가설 PR
- **Then**: RED 신호 발생

### AC-AME-003-12 — `isAuthenticated()` 어휘 회귀 검출

- **Given**: §A.4 AC-AME-001-U3/U4 endpoint
- **When**: `isAuthenticated()`를 `permitAll()` 또는 `hasRole(...)`로 변경하는 가설 PR
- **Then**:
  - `permitAll()` 변경 시: AC-AME-001-U3가 RED 발생 (Authorization 헤더 없이 200 응답 — 401 회귀)
  - `hasRole(...)` 변경 시: AC-AME-001-U4가 RED 발생 (권한 없는 토큰이 200 → 403)

---

## D. Quality Gates

### D.1 통합 테스트 GREEN

- `./gradlew :backend:integrationTest --tests "kr.co.ircp.cms.security.AuthorizationMatrixExpandIT"` 실행 시 모든 시나리오(§A + §B + §C) GREEN
- 30 endpoint × 3 시나리오 ≥ ~90 AC + 인프라 검증 4 AC + 권한 어휘 회귀 검출 12 AC = 합계 ~106+ AC GREEN

### D.2 권한 어휘 12종 모두 커버 검증

- §C AC-AME-003-1 ~ AC-AME-003-12 모두 매핑된 endpoint 시나리오 ≥ 1건 존재
- 권한 어휘별 endpoint 분포를 IT JavaDoc 표 또는 README 표로 명시 (Step 4)
- 권한 어휘 커버리지 4/12 (AUTHZ-MATRIX-001) → 12/12 (본 SPEC 적용 후)

### D.3 AUTHZ-MATRIX-001 회귀 0건

- `./gradlew :backend:integrationTest --tests "kr.co.ircp.cms.security.AuthorizationMatrixIT"` 실행 시 19 AC 모두 GREEN 유지
- AUTHZ-MATRIX-001과 endpoint 중복 0건 (Step 1 정밀 선정 검증)

### D.4 CTRL-AUTHZ-COVERAGE-001 회귀 0건

- `./gradlew :backend:test` 실행 시 31 ControllerTest 메소드 레벨 시나리오 모두 GREEN 유지
- 본 SPEC IT가 `@WebMvcTest` 슬라이스에 영향 없음을 검증 (검증 레이어 분리 효과)

### D.5 다른 IT 회귀 0건

- `./gradlew :backend:integrationTest` 전체 실행 시 다음 IT GREEN 유지:
  - `SecurityConfigIntegrationTest` (PII-002 — 인증 흐름 4 시나리오)
  - PII-001/002/MASKING IT (PII 컬럼 암호화/해시/마스킹 회귀)
  - 기타 도메인 IT (Banner, Page 등)

### D.6 운영 코드 git diff 0건 강제

- `git diff --stat backend/src/main/` 출력은 0줄 (운영 코드 변경 없음)
- 본 SPEC 적용 commit은 `backend/src/test/` 경로만 수정해야 함
- `backend/src/main/java/kr/co/ircp/cms/config/SecurityConfig.java` 수정 0건
- `backend/src/main/java/kr/co/ircp/cms/security/JwtAuthenticationFilter.java` 수정 0건
- 운영 컨트롤러의 `@PreAuthorize` 어노테이션 수정 0건

### D.7 LSP 0 errors

- `AuthorizationMatrixExpandIT` 컴파일 0 errors, 0 warnings
- ArchUnit 규칙(존재 시) 회귀 0건

### D.8 응답 형식 회귀 기준선 유지

- 30 endpoint 각각의 `code=AUTH_REQUIRED`/`code=AUTH_FORBIDDEN` 회귀 검증 PASS
- 향후 운영 `SecurityConfig` 응답 body 형식 변경 PR 시 본 SPEC IT가 회귀 신호로 작동하여 PR 머지를 차단해야 함 (정상 동작)

### D.9 README 갱신 (D3 사용자 결정 채택)

- `backend/src/test/java/kr/co/ircp/cms/security/README.md` 신설 또는 갱신
- 다음 절차 명시:
  - "신규 `@PreAuthorize` 추가 시: (1) 권한 어휘가 12종 중 어느 것에 해당하는지 식별, (2) 해당하지 않는 새 어휘면 `AuthorizationMatrixExpandIT`의 새 `@Nested` 그룹 신설 또는 적합 그룹에 시나리오 추가, (3) 해당하면 적합 도메인 그룹의 endpoint 분포 확장 검토, (4) Step 4 회귀 검증 절차 재실행"
- 후속 SPEC `SPEC-...-AUTHZ-AUTODETECT-001`(가칭)에서 ArchUnit 자동 검출로 대체될 수 있음 명시

### D.10 시나리오 커버리지 종합

| 영역 | AC 수 |
|------|------|
| §A.1 ContentDomainTests | ~21 (7 endpoint × 3) |
| §A.2 BlockDomainTests | ~6 (2 endpoint × 3) |
| §A.3 DashboardDomainTests | ~9 (3 endpoint × 3) |
| §A.4 AuthDomainTests (isAuthenticated 401/200만) | ~12 (4 endpoint, 일부 시나리오 N/A) |
| §A.5 SystemDomainTests | ~15 (5 endpoint × 3) |
| §A.6 GovernanceDomainTests | ~9 (3 endpoint × 3) |
| §A.7 BoardMenuDomainTests | ~15 (5 endpoint × 3) |
| §B 인프라 신설 (REQ-AM-EXP-002) | 4 |
| §C 권한 어휘 회귀 검출 (REQ-AM-EXP-003) | 12 |
| **합계** | **~103+ AC** (목표 ~90 + 인프라 4 + 어휘 회귀 12 + α) |

---

## E. Definition of Done

본 SPEC은 다음 조건을 모두 만족할 때 RUN 1차 완료로 간주한다.

1. `AuthorizationMatrixExpandIT` 신규 IT 클래스가 활성화되어 부팅 가능 상태 (AC-AME-002-1 ~ AC-AME-002-4 GREEN)
2. 30 endpoint × 3 시나리오 ≥ ~90 AC GREEN (§A 모든 도메인 그룹)
3. 권한 어휘 12종 모두 회귀 검출 보장 (§C AC-AME-003-1 ~ AC-AME-003-12 매핑 endpoint 시나리오 ≥ 1건씩 존재)
4. AUTHZ-MATRIX-001 19 AC 회귀 0건 (D.3)
5. CTRL-AUTHZ-COVERAGE-001 31 ControllerTest 메소드 레벨 회귀 0건 (D.4)
6. PII/SecurityConfig 등 다른 IT 회귀 0건 (D.5)
7. 운영 코드 변경 0건 (D.6 강제)
8. LSP 0 errors (D.7)
9. 응답 형식 회귀 기준선 유지 (D.8)
10. README 갱신으로 신규 endpoint 추가 절차 안내 (D.9)
11. 권한 어휘 커버리지 4/12 → 12/12 (100%) 달성 (D.2)
12. 후속 SPEC 트랙 (`SPEC-...-AUTHZ-IT-EXPAND-002`, `-003`, `-AUTODETECT-001`) 명시 (spec.md §11)
13. 사용자 IT 실행 안내 (Java 17 환경): `./gradlew :backend:integrationTest --tests "kr.co.ircp.cms.security.AuthorizationMatrixExpandIT"` (본 SPEC 작성 세션에서는 Java 17 미설치로 IT 실행 검증을 사용자 환경 위임 — AUTHZ-MATRIX-001 v0.2와 동일 패턴)

---
