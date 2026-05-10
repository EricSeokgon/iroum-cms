# SPEC-CMS-SECURITY-CTRL-AUTHZ-COVERAGE-001 — 인수 기준 (Acceptance Criteria)

본 문서는 SPEC-CMS-SECURITY-CTRL-AUTHZ-COVERAGE-001 ControllerTest 메소드 레벨 권한 거부 시나리오 보강(`@WebMvcTest` 슬라이스 401/403 회귀 커버리지)의 Given/When/Then 형식 인수 시나리오와 품질 게이트를 정의한다. 모든 시나리오는 `@WebMvcTest` 슬라이스(`WebMvcTestInfraConfig.testSecurityFilterChain` 적용 + `@EnableMethodSecurity`)로 검증 가능해야 한다.

본 SPEC은 SPEC-CMS-SECURITY-AUTHZ-MATRIX-001(commit `f0ae970` RUN, `e14204f` sync)의 HTTP 매트릭스 IT 인프라와 검증 레이어가 분리되어 있어 상호 보완적이다. 운영 코드 변경 0건 + 신규 파일 0건(기존 `*ControllerTest`에 메소드만 추가)이 강제된다.

---

## A. REQ-CTRL-AUTHZ-COVERAGE-001 — 메소드 레벨 401/403 보강 (시나리오 패턴)

본 섹션은 31 ControllerTest 보강에 사용되는 표준 시나리오 패턴 2종을 정의한다. RUN 시 각 ControllerTest의 `@PreAuthorize` 보호 endpoint별로 본 패턴을 적용한다.

### AC-COV-001-1 — 401 미인증 시나리오 패턴 (no authentication)

- **Given**: ControllerTest에 `@WebMvcTest(TargetController.class)` + `@Import(WebMvcTestInfraConfig.class)` 또는 동등 설정 적용. 대상 endpoint는 `@PreAuthorize` 또는 클래스 레벨 권한 정책 적용 (운영 보호 endpoint). 테스트 메소드에 인증 어노테이션(`@WithMockUser` 등) 미부착 — SecurityContext가 비어있는 상태
- **When**: `mockMvc.perform(get|post|put|delete("/api/v1/...").contentType(MediaType.APPLICATION_JSON).content(validBodyOrEmpty))` 호출 (Authorization 헤더 부재 + `@WithMockUser` 부재)
- **Then**:
  - 응답 status는 401 Unauthorized
  - `WebMvcTestInfraConfig.testSecurityFilterChain`의 `AnonymousAuthenticationFilter`가 anonymous principal을 부착한 뒤 `@PreAuthorize` 인터셉터가 `AccessDeniedException`을 throw하고 `ExceptionTranslationFilter`가 `Http403ForbiddenEntryPoint`(또는 default EntryPoint)를 호출하여 401 응답을 작성
  - jsonPath 검증 (선택적): 응답 body가 비어있거나 표준 에러 형식 — 본 SPEC 비검증 (status code만 검증)
  - 검증 코드 예: `mockMvc.perform(get("/api/v1/...")).andExpect(status().isUnauthorized());`

### AC-COV-001-2 — 403 권한 부족 시나리오 패턴 (insufficient authority)

- **Given**: ControllerTest에 `@WebMvcTest(TargetController.class)` + `@Import(WebMvcTestInfraConfig.class)` 또는 동등 설정 적용. 대상 endpoint는 `@PreAuthorize("hasAuthority('REQUIRED:AUTH')")` 또는 `@PreAuthorize("hasRole('REQUIRED_ROLE')")` 정책 적용. 테스트 메소드에 `@WithMockUser(authorities = {"WRONG_AUTHORITY"})` 또는 `@WithMockUser(roles = {"WRONG_ROLE"})` 어노테이션 부착 — 권한 어휘는 운영 정책과 일치하지 않는 임의 값
- **When**: `mockMvc.perform(get|post|put|delete("/api/v1/...").contentType(MediaType.APPLICATION_JSON).content(validBodyOrEmpty))` 호출
- **Then**:
  - 응답 status는 403 Forbidden
  - `@PreAuthorize` 인터셉터가 SecurityContext의 MockUser principal에 대해 `hasAuthority`/`hasRole` 평가 수행 → false → `AccessDeniedException` throw → `accessDeniedHandler`가 403 응답 작성
  - 검증 코드 예: `mockMvc.perform(post("/api/v1/...").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());`
  - `@WithMockUser` 권한 어휘 매핑 가이드:
    - 운영 `@PreAuthorize("hasAuthority('CONTENT:WRITE')")` → 본 시나리오 `@WithMockUser(authorities = {"WRONG_AUTH"})` (또는 `authorities = {}` 빈 배열)
    - 운영 `@PreAuthorize("hasRole('SUPER_ADMIN')")` → 본 시나리오 `@WithMockUser(roles = {"USER"})` (USER 역할은 SUPER_ADMIN 정책 미충족)
    - 운영 `@PreAuthorize("hasAnyRole('SUPER_ADMIN','DEPT_ADMIN')")` → 본 시나리오 `@WithMockUser(roles = {"USER"})` (USER는 두 역할 모두 미보유)

---

## B. REQ-CTRL-AUTHZ-COVERAGE-002 — 도메인별 4 Step batch GREEN 검증

각 Step은 독립 verification 가능하다. Step 종료 시 해당 도메인 ControllerTest 전체 GREEN을 확인하면 다음 Step으로 진행한다.

### AC-COV-002-1 — Step 1 GREEN (governance 6 + auth 5 = 11 ControllerTest)

- **Given**: Step 1 대상 11 ControllerTest 보강 완료
  - governance: `BatchExecutionLogControllerTest`, `DataQualityControllerTest`, `DictionaryControllerTest`, `GovernanceStatsControllerTest`, `RecoveryDrillControllerTest`, `RetentionPolicyControllerTest`
  - auth: `MeControllerTest`, `MyPersonalDataAccessControllerTest`, `PermissionChangeControllerTest`, `RoleControllerTest`, `UserControllerTest`
- **When**:
  - `./gradlew test --tests "kr.co.ircp.cms.governance.*ControllerTest"` 실행
  - `./gradlew test --tests "kr.co.ircp.cms.auth.*ControllerTest"` 실행
- **Then**:
  - 11 ControllerTest 모두 BUILD SUCCESSFUL
  - 신규 추가 401/403 시나리오 모두 GREEN
  - 기존 정합 권한 시나리오 회귀 0건 (Step 1 추가 전 vs 후 동일 케이스 GREEN 유지)
  - 다른 도메인(policy/safety/board/dashboard/system/content) 회귀 0건

### AC-COV-002-2 — Step 2 GREEN (policy 5 + safety 5 = 10 ControllerTest)

- **Given**: Step 2 대상 10 ControllerTest 보강 완료
  - policy: `PolicyDispatchControllerTest`, `PolicyMatchingControllerTest`, `PolicyProgramControllerTest`, `PolicyNotificationSubscriptionControllerTest`, `PolicyTrackingControllerTest`
  - safety: `SafetyIncidentControllerTest`, `SafetyKeywordControllerTest`, `SafetyProfileControllerTest`, `SafetyReportControllerTest`, `SafetyTemplateControllerTest`
- **When**:
  - `./gradlew test --tests "kr.co.ircp.cms.policy.*ControllerTest"` 실행
  - `./gradlew test --tests "kr.co.ircp.cms.safety.*ControllerTest"` 실행
- **Then**:
  - 10 ControllerTest 모두 BUILD SUCCESSFUL
  - 신규 추가 401/403 시나리오 모두 GREEN
  - Step 1 결과 회귀 0건

### AC-COV-002-3 — Step 3 GREEN (board 4 + dashboard 3 = 7 ControllerTest)

- **Given**: Step 3 대상 7 ControllerTest 보강 완료
  - board: `AttachmentControllerTest`, `BbsMasterControllerTest`, `CommentControllerTest`, `PostControllerTest`
  - dashboard: `DashboardLayoutControllerTest`, `ExportControllerTest`, `SavedViewControllerTest`
- **When**:
  - `./gradlew test --tests "kr.co.ircp.cms.board.*ControllerTest"` 실행
  - `./gradlew test --tests "kr.co.ircp.cms.dashboard.*ControllerTest"` 실행
- **Then**:
  - 7 ControllerTest 모두 BUILD SUCCESSFUL
  - 신규 추가 401/403 시나리오 모두 GREEN
  - Step 1, 2 결과 회귀 0건

### AC-COV-002-4 — Step 4 GREEN (system 2 + content 1 = 3 ControllerTest)

- **Given**: Step 4 대상 3 ControllerTest 보강 완료
  - system: `AccessLogControllerTest`, `system/stats/DashboardControllerTest`
  - content: `content/sitemap/SitemapControllerTest`
- **When**:
  - `./gradlew test --tests "kr.co.ircp.cms.system.*ControllerTest"` 실행
  - `./gradlew test --tests "kr.co.ircp.cms.content.sitemap.*ControllerTest"` 실행
- **Then**:
  - 3 ControllerTest 모두 BUILD SUCCESSFUL
  - 신규 추가 401/403 시나리오 모두 GREEN
  - Step 1, 2, 3 결과 회귀 0건
  - `HealthControllerTest`는 보강 대상 미포함 (비범위 — `/api/v1/health/**` permitAll 화이트리스트)

---

## C. REQ-CTRL-AUTHZ-COVERAGE-003 — 회귀 0건 + AUTHZ-MATRIX-001 보완

### AC-COV-003-1 — 31 ControllerTest 전체 회귀 0건

- **Given**: 본 SPEC RUN Step 1~4 모두 완료. 31 ControllerTest 모두 401/403 시나리오 보강됨
- **When**: `./gradlew test` 전체 단위 테스트 실행
- **Then**:
  - 31 ControllerTest 모두 GREEN
  - 본 SPEC 적용 전 통과한 모든 단위 테스트(50건 + ArchUnit 5건 등 기존 테스트) GREEN 유지
  - 신규 추가 401/403 시나리오 약 60~120건(31 ControllerTest × 평균 2~4 시나리오) 모두 GREEN
  - 기존 정합 권한 시나리오 회귀 0건

### AC-COV-003-2 — AUTHZ-MATRIX-001 IT 회귀 0건

- **Given**: 본 SPEC + AUTHZ-MATRIX-001 모두 적용된 상태. AUTHZ-MATRIX-001은 commit `f0ae970` RUN, `e14204f` sync 완료
- **When**: `./gradlew integrationTest --tests "kr.co.ircp.cms.security.AuthorizationMatrixIT"` 실행
- **Then**:
  - `AuthorizationMatrixIT` 19 AC(AC-AM-001-1 ~ AC-AM-003-4) 모두 GREEN
  - 본 SPEC 적용으로 인한 운영 컨텍스트 회귀 0건
  - 검증 레이어 분리 검증: 본 SPEC `@WebMvcTest` 슬라이스 변경이 AUTHZ-MATRIX-001 `@SpringBootTest` 컨텍스트에 영향 없음 확인

### AC-COV-003-3 — 검증 레이어 분리 명시 검증

- **Given**: 본 SPEC 31 ControllerTest 보강 + AUTHZ-MATRIX-001 19 AC IT 양립
- **When**: 코드베이스 분석 (Glob `*ControllerTest.java` + `AuthorizationMatrixIT.java` 비교)
- **Then**:
  - 본 SPEC ControllerTest는 `@WebMvcTest` + `WebMvcTestInfraConfig.testSecurityFilterChain` 적용 (메소드 레벨 권한 검증 슬라이스)
  - AUTHZ-MATRIX-001 IT는 `@SpringBootTest` + 운영 `SecurityConfig.SecurityFilterChain` 적재 (HTTP 매트릭스 + JWT 통합)
  - 동일 컨트롤러(예: `UserController`, `RetentionPolicyController`)가 양 SPEC에서 다뤄지더라도 검증 레이어가 다르므로 중복 없음
  - 회귀 신호 분리: 본 SPEC은 `@PreAuthorize` 어노테이션 변경 즉시 회귀, AUTHZ-MATRIX-001은 `SecurityConfig.requestMatchers().permitAll()` 매트릭스 변경 즉시 회귀

### AC-COV-003-4 — 5/7 코드 리뷰 C1 메소드 레벨 갭 100% 커버

- **Given**: 본 SPEC RUN 완료 + AUTHZ-MATRIX-001 적용 완료
- **When**: 5/7 코드 리뷰 C1 항목(`.moai/plans/twinkling-spinning-toucan-agent-a7f98f3b374ef2270.md`) 메소드 레벨 갭 분석
- **Then**:
  - SecurityAutoConfiguration exclude 58 ControllerTest 중:
    - 본 SPEC 적용 전: 31 ControllerTest 검증 (commit `f80f95e`/`132d2c2` 보강)
    - 본 SPEC 적용 후: 31 (기존) + 31 (본 SPEC 보강) = 62 검증 보강 (단, 일부 중복 가능)
    - 비범위: `HealthControllerTest` 1개 (permitAll 화이트리스트)
  - 메소드 레벨 권한 거부 검증 커버리지: 31/58 → 58/58 (HealthController 비범위 1 제외 시 57/57 = 100%)
  - 5/7 C1 메소드 레벨 갭 완전 해소

---

## D. Quality Gates

### D.1 단위 테스트 GREEN

- `./gradlew test` 전체 실행 시 31 ControllerTest 모두 GREEN
- 신규 추가 401/403 시나리오 모두 GREEN
- 기존 단위 테스트 회귀 0건

### D.2 통합 테스트 회귀 0건

- `./gradlew integrationTest` 전체 실행 시 `AuthorizationMatrixIT` 19 AC GREEN
- 기존 IT(SecurityConfigIntegrationTest 등) 회귀 0건

### D.3 운영 코드 git diff 0건 강제

- `git diff --stat backend/src/main/` 출력은 0줄 (운영 코드 변경 없음)
- 본 SPEC 적용 commit은 `backend/src/test/` 경로만 수정
- 운영 컨트롤러(`*Controller.java`) 수정 0건
- `SecurityConfig.java` 수정 0건
- `WebMvcTestInfraConfig.java` 수정 0건 (기존 인프라 그대로 활용)

### D.4 신규 파일 0건 강제

- `git status --porcelain backend/src/test/` 출력에 `??` (untracked) 신규 ControllerTest 파일 0건
- 본 SPEC 적용은 기존 `*ControllerTest.java`에 메소드 추가만 수행 (신규 파일 신설 금지)
- 별도 헬퍼/유틸 파일 신설 금지 (`@WithMockUser`는 Spring Security 표준 어노테이션 사용)

### D.5 LSP 0 errors

- 보강된 31 ControllerTest 컴파일 0 errors, 0 warnings
- ArchUnit 규칙(존재 시) 회귀 0건

### D.6 도메인별 Step batch 적용 검증

- Step 1 (governance+auth, 11 ControllerTest) BUILD SUCCESSFUL
- Step 2 (policy+safety, 10 ControllerTest) BUILD SUCCESSFUL
- Step 3 (board+dashboard, 7 ControllerTest) BUILD SUCCESSFUL
- Step 4 (system+content, 3 ControllerTest) BUILD SUCCESSFUL
- 각 Step별 commit 또는 단일 commit 모두 허용 (도메인별 grouping 보존)

### D.7 시나리오 커버리지

- 31 ControllerTest × 평균 2~4 시나리오 = 약 60~120 신규 401/403 시나리오 (1차 권장 최소 60건)
- 컨트롤러당 최소 1개 401 시나리오 (필수)
- 컨트롤러당 최소 1개 403 시나리오 (보호 endpoint 존재 시 권장)
- 단순 권한 컨트롤러는 401만 추가 가능, WRITE/DELETE 권한 컨트롤러는 401+403 모두 추가 권장

### D.8 회귀 검출 기준선 고정

- `@PreAuthorize` 어노테이션 회귀 검증 PASS (D.1 포함되나 강조)
- 향후 컨트롤러 `@PreAuthorize` 변경 PR 시 본 SPEC ControllerTest가 회귀 신호로 작동하여 PR 머지를 차단해야 함 (정상 동작)

---

## E. Definition of Done

본 SPEC은 다음 조건을 모두 만족할 때 RUN 1차 완료로 간주한다.

1. 31 ControllerTest 모두 401 미인증 시나리오 추가 (AC-COV-001-1 패턴 적용)
2. 보호 endpoint를 가진 ControllerTest 모두 403 권한 부족 시나리오 추가 (AC-COV-001-2 패턴 적용)
3. 도메인별 4 Step batch 적용 완료 (Step 1 → 2 → 3 → 4 순 또는 병렬, 각 Step BUILD SUCCESSFUL)
4. `HealthControllerTest` 비범위 명시 (보강 대상 미포함)
5. 31 ControllerTest 전체 회귀 0건 (D.1)
6. AUTHZ-MATRIX-001 19 AC 회귀 0건 (D.2)
7. 운영 코드 변경 0건 (D.3 강제)
8. 신규 파일 0건 (D.4 강제)
9. LSP 0 errors (D.5)
10. 5/7 코드 리뷰 C1 메소드 레벨 갭 100% 커버 (AC-COV-003-4)
11. 사용자 테스트 실행 안내 (Java 17 환경): `./gradlew :backend:test --tests "kr.co.ircp.cms.*ControllerTest"` (본 SPEC 작성 세션에서는 Java 17 미설치로 단위 테스트 실행 검증을 사용자 환경 위임)

---
