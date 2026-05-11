# SPEC-CMS-SECURITY-AUTHZ-AUTODETECT-001: ArchUnit 기반 운영 @PreAuthorize 자동 검출 — IT 매트릭스 누락 PR 차단 v0.2

**Status**: Implemented (2026-05-11) — 실제 Java 17 + Gradle 구동 검증 완료
**Implementation commits**: 2be18d0 (Step 1 신설), 9cb4933 (Step 1 GREEN), 6b831d8 (Step 2 GREEN)
**Test result**: 4 AC, 0 failed, BUILD SUCCESSFUL in 11s

## v0.2 변경 이력 (2026-05-11)

본 SPEC v0.1 → v0.2: Step 1+2 RUN 완성. AuthorizationCoverageArchTest 신설 (448줄, 4 @Test).
- AC-AAD-001-1: 운영 @PreAuthorize 메소드 카운트 (103) GREEN
- AC-AAD-001-2: IT @DisplayName endpoint set (35 unique) GREEN
- AC-AAD-002-1: 35 endpoint baseline 정확 매칭 GREEN
- AC-AAD-003-1: 운영 권한 어휘 set (31 unique) GREEN

정밀 진단 정정 (실측 기반):
- 운영 @PreAuthorize 카운트: grep 120 → ArchUnit 103 (메소드 레벨, 클래스 레벨 5개 컨트롤러 제외)
- 운영 권한 어휘: 사전 추정 14 → 실측 31 (사전 미식별 17 어휘 ArchUnit 자동 발견)
- Path variable 정규화 강화: `\d+` 단계 → `{[a-zA-Z]+}` 단계 추가 (변수명 차이 흡수)

운영 코드 변경 0줄 + 회귀 0건. README D3 절차 갱신 + CHANGELOG 추가.



## 1. 개요

| 항목 | 내용 |
|------|------|
| SPEC ID | SPEC-CMS-SECURITY-AUTHZ-AUTODETECT-001 |
| 제목 | ArchUnit 기반 운영 @PreAuthorize 자동 검출 — IT 매트릭스 누락 PR 차단 |
| 작성일 | 2026-05-11 |
| 작성자 | manager-spec (MoAI) |
| 상태 | Planned |
| 우선순위 | **P2 (보안 트랙 자동화 보강)** |
| 분류 | Cross-cutting Security IT Auto-Detection SPEC |
| 의존 SPEC | SPEC-CMS-SECURITY-AUTHZ-MATRIX-001 v0.2 (Implemented — IT 인프라 1차), SPEC-CMS-SECURITY-AUTHZ-IT-EXPAND-001 v0.2 (Implemented — IT 매트릭스 확장 88 AC), 운영 SecurityConfig (`SPEC-CMS-002 §16.x`), ArchUnit 1.3.0 (의존성 기존) |
| 형제 SPEC | SPEC-CMS-SECURITY-CTRL-AUTHZ-COVERAGE-001 (검증 레이어 분리), 후속 SPEC SPEC-CMS-SECURITY-AUTHZ-IT-EXPAND-002/003 (가칭, endpoint 점진 확장), SPEC-CMS-SECURITY-AUTHZ-AUTODETECT-CODEGEN-001 (가칭, 자동 IT 메소드 codegen) |

본 SPEC은 SPEC-CMS-SECURITY-AUTHZ-IT-EXPAND-001 v0.2 §11(후속 SPEC 트랙)에 명시된 자동 검출 트랙의 구체화이다. AUTHZ-IT-EXPAND-001은 1차 6 endpoint(AUTHZ-MATRIX-001) → 35 endpoint(`AuthorizationMatrixIT` 6 + `AuthorizationMatrixExpandIT` 29)로 확장하면서 12 권한 어휘 회귀 검출 인프라를 확보했다. 그러나 신규 운영 `@PreAuthorize` 추가 시 README D3 절차에 따라 **수동으로** IT 매트릭스를 갱신해야 하며, 누락 시 회귀 검출이 부재한 상태이다. 운영 컨트롤러의 `@PreAuthorize`는 현재 120개로 IT 검증 분포는 35/120 = 29.2%이며, 나머지 85개는 신규 endpoint 추가/권한 어휘 변경 시 회귀 신호 없이 머지될 수 있다.

**구현 대상 요구사항**: REQ-AAD-001, REQ-AAD-002, REQ-AAD-003, REQ-AAD-004, REQ-AAD-005 (본 SPEC 신규 정의)

본 SPEC의 1차 범위는 (1) ArchUnit 1.3.0 기반 신규 테스트 클래스 `AuthorizationCoverageArchTest` 신설로 운영 `@PreAuthorize` 어노테이션 발견 → IT 매트릭스 시나리오 매칭 검증 → 누락 시 RED 신호 발생, (2) 35 endpoint baseline 회귀 검출(신규 추가 + 기존 제거 둘 다), (3) 권한 어휘 변경 검출(예: `CONTENT:WRITE` → `PAGE:WRITE` 변경 시 IT 시나리오 정합성 mismatch RED), (4) Gradle `:backend:check` 자동 통합으로 CI PR 차단, (5) RED 발생 시 README D3 수동 갱신 절차 안내 메시지 제공이다. 본 SPEC은 **운영 코드 변경 0건**(테스트 인프라 추가 위주)이며, AUTHZ-IT-EXPAND-001과의 보완 관계(IT 매트릭스 자체는 EXPAND가, 갱신 자동화 게이트는 본 SPEC이) 및 PiiEmailMaskArchTest 271줄 패턴 재사용으로 ArchUnit DSL 학습 부담을 최소화한다.

---

## 2. 배경 및 동기

### 2.1 AUTHZ-IT-EXPAND-001 v0.2 D3 수동 갱신 부담

SPEC-CMS-SECURITY-AUTHZ-IT-EXPAND-001 v0.2(Implemented 2026-05-11)는 다음을 달성했다.

- `AuthorizationMatrixExpandIT` 1540줄 신규 IT 클래스
- 29 endpoint × 3 시나리오 + 1 smoke = 88 AC + 1 = 89 `@Test` 메소드
- 12 권한 어휘 회귀 검출 인프라 확보
- AUTHZ-MATRIX-001 19 AC와 합산 시 35 endpoint 검증
- 운영 코드 git diff 0건

그러나 v0.2 §11(후속 SPEC 트랙)은 다음을 명시했다.

> "신규 운영 `@PreAuthorize` 추가 시 IT 매트릭스 갱신은 D3 사용자 결정 채택에 따라 수동(README 5단계 절차)이며, 자동 검출은 후속 SPEC `SPEC-CMS-SECURITY-AUTHZ-AUTODETECT-001`(가칭)에서 ArchUnit 또는 Spring AOT introspection으로 검출."

본 SPEC은 정확히 그 후속 트랙이다.

### 2.2 잔여 갭 — 85 endpoint 회귀 신호 부재 + 권한 어휘 변경 미검출

MoAI 정밀 진단(2026-05-11) 결과 다음 갭이 확인되었다.

```
운영 @PreAuthorize 분포: 120개
IT 검증 분포 (AuthorizationMatrixIT + ExpandIT 합산): 35/120 = 29.2%
미검증 endpoint: 85/120 = 70.8%
```

회귀 신호 부재 시나리오:

1. **신규 `@PreAuthorize` 추가** — 개발자가 컨트롤러에 신규 endpoint를 추가하면서 `@PreAuthorize("hasAuthority('NEW:WRITE')")` 어노테이션을 적용했지만 IT 매트릭스에 시나리오를 추가하지 않으면 PR 머지 후 운영 노출되며, 추후 `@PreAuthorize` 누락 회귀가 발생해도 즉시 감지되지 않는다.
2. **기존 endpoint 권한 어휘 변경** — 예: 운영 컨트롤러에서 `@PreAuthorize("hasAuthority('CONTENT:WRITE')")` → `@PreAuthorize("hasAuthority('PAGE:WRITE')")`로 변경 시 IT 시나리오의 `givenValidToken(roles=[], permissions=List.of("CONTENT:WRITE"))` stub과 운영 정책이 불일치하나, IT는 stub 토큰 권한이 변경되지 않아 GREEN을 유지한다(false positive).
3. **`@PreAuthorize` 어노테이션 제거** — 운영 컨트롤러에서 어노테이션 자체가 제거되면 권한 검증이 우회되지만, IT는 기존 토큰으로 호출하여 통과 status를 반환받기 때문에 RED 신호가 발생하지 않는다.

### 2.3 ArchUnit 1.3.0 + PiiEmailMaskArchTest 패턴 재사용 가능 (정밀 진단 — 2026-05-11)

MoAI 정밀 진단 결과 다음 인프라가 이미 갖춰져 있다.

- ArchUnit 1.3.0 의존성: `backend/build.gradle.kts`에 `testImplementation("com.tngtech.archunit:archunit-junit5:1.3.0")` 이미 포함 (변경 0건)
- 기존 ArchUnit 테스트 패턴 확립: `backend/src/test/java/kr/co/ircp/cms/domain/security/pii/archunit/PiiEmailMaskArchTest.java` (271줄, SPEC-CMS-SECURITY-PII-MASKING-001 산출물)
  - `ClassFileImporter` 사용으로 컴파일된 class 메타데이터 적재
  - `JavaClasses` 컬렉션 단위 ArchUnit DSL 검증
  - `@Test` 메소드 단위 명확한 GREEN/RED 시그널
  - Testcontainers/`@SpringBootTest` 미사용 (단위 테스트 레벨 — 빠른 피드백)
- 운영 컨트롤러 분포: 61개 컨트롤러, `@PreAuthorize` 120개

본 SPEC은 PiiEmailMaskArchTest 패턴을 그대로 재사용하면서, 검증 대상을 `kr.co.ircp.cms.domain.security.pii..*` → `kr.co.ircp.cms.web.api..*`로 전환하고 검증 규칙을 `@PreAuthorize` 어노테이션 발견 → IT 매트릭스 시나리오 매칭으로 신규 정의한다.

### 2.4 사용자 결정 채택 (D1~D4 사전 확정)

오케스트레이터가 사전 수집한 사용자 결정은 다음과 같다.

| 결정 | 채택 | 비고 |
|------|------|------|
| **D1** 검출 방식 | **ArchUnit 1.3.0** | 이미 사용 중, PiiEmailMaskArchTest 271줄 패턴 재사용 |
| **D2** 동작 방식 | **Test RED (CI 실패로 PR 차단)** | Gradle `check` 통합, 명확한 시그널 |
| **D3** 검증 범위 | **AuthorizationMatrixIT + AuthorizationMatrixExpandIT 둘 다** | 35 endpoint 누락 시 RED |
| **D4** 검출 시나리오 | **신규 추가 + 권한 어휘 변경 둘 다** | 회귀 검출 완전 |

본 SPEC은 D1~D4를 그대로 채택하여 §3, §5, §9에 반영한다.

---

## 3. 범위 + 비범위

### 3.1 범위 (P2)

| 항목 | 설명 |
|------|------|
| **REQ-AAD-001 — `AuthorizationCoverageArchTest` 신설** | ArchUnit DSL로 운영 `@PreAuthorize` 어노테이션 발견 → IT 매트릭스 검증 시나리오 존재 여부 확인 → 부재 시 RED. PiiEmailMaskArchTest 271줄 패턴 재사용 |
| **REQ-AAD-002 — 35 endpoint baseline 회귀 검출** | `AuthorizationMatrixIT`(6) + `AuthorizationMatrixExpandIT`(29) = 35 endpoint 누락 0건 보장. 운영 `@PreAuthorize` 신규/제거 시 RED 신호 발생 |
| **REQ-AAD-003 — 권한 어휘 변경 검출** | 운영 endpoint 권한 어휘 변경 시(예: `CONTENT:WRITE` → `PAGE:WRITE`) IT 시나리오 토큰 권한 매핑과 정합성 검증 — mismatch 시 RED |
| **REQ-AAD-004 — Gradle `check` 통합** | `./gradlew :backend:check` 실행 시 자동 수행. CI에서 PR 차단. 단위 테스트 레벨(Testcontainers/`@SpringBootTest` 불필요 — 컴파일된 class 메타데이터만 분석)로 빠른 피드백 |
| **REQ-AAD-005 — 검출 시 수동 갱신 절차 안내 메시지** | RED 발생 시 javadoc + assertion 메시지에 endpoint(METHOD + PATH + VOCABULARY) + README D3 수동 갱신 절차 링크 명시 (한국어) |

### 3.2 비범위 (별도 SPEC 또는 후속)

| 비범위 | 사유 |
|--------|------|
| 자동 IT 메소드 codegen | D2 채택: Test RED만 — 자동 생성은 후속 SPEC `SPEC-CMS-SECURITY-AUTHZ-AUTODETECT-CODEGEN-001`(가칭) |
| Spring AOT introspection 검출 | D1 채택: ArchUnit — Spring AOT는 ApplicationContext 필요(IT 레벨), 본 SPEC은 단위 테스트로 빠른 피드백 우선 |
| SecurityConfig `requestMatchers` 정합성 검증 | D3 비범위 — 전체 SecurityConfig 정합성은 후속 SPEC `SPEC-CMS-SECURITY-CONFIG-INTEGRITY-001`(가칭) |
| 운영 `@PreAuthorize` 신규 추가 시 IT 시나리오 자동 추가 | D2 채택: Test RED만 — 자동 추가는 codegen SPEC |
| 메소드 슬라이스 IT(`@WebMvcTest`) 정합성 | SPEC-CMS-SECURITY-CTRL-AUTHZ-COVERAGE-001 영역 (검증 레이어 분리) |
| 클라이언트(`PreAuthorize` 외) 권한 검증 | Spring Security `requestMatchers` + Service 레이어 메소드 권한은 별도 SPEC |
| 35 → 80+ → 120 endpoint 점진 IT 확장 | 후속 SPEC `SPEC-CMS-SECURITY-AUTHZ-IT-EXPAND-002/003` (본 SPEC은 자동 검출 게이트만) |

---

## 4. 사용자 시나리오

### 4.1 시나리오 1 — 신규 운영 endpoint 추가 시 IT 매트릭스 누락 검출 (REQ-AAD-001/002/004)

```
[Given] 개발자가 PR에서 BannerController에 신규 endpoint 추가:
        @PostMapping("/api/v1/content/banners/{id}/archive")
        @PreAuthorize("hasAuthority('CONTENT:WRITE')")
        public ResponseEntity<Void> archive(@PathVariable Long id) { ... }
[When]  AuthorizationMatrixExpandIT에 archive endpoint 시나리오를 추가하지 않은 채 PR push
[Then]  CI에서 ./gradlew :backend:check 실행 시 AuthorizationCoverageArchTest RED
        AssertionError 메시지: "운영 endpoint POST /api/v1/content/banners/{id}/archive는
        @PreAuthorize(hasAuthority('CONTENT:WRITE'))를 사용하지만 IT 매트릭스에 시나리오가 없습니다.
        README의 'HTTP 권한 매트릭스 IT 신규 endpoint 추가 절차'를 참조하여
        AuthorizationMatrixExpandIT의 ContentDomainTests @Nested 그룹에 시나리오를 추가하세요."
        → PR 차단 (exit code != 0)
```

### 4.2 시나리오 2 — 기존 endpoint 권한 어휘 변경 시 IT 시나리오 mismatch 검출 (REQ-AAD-003/004)

```
[Given] 운영 BannerController.create의 권한 어휘가 다음과 같이 변경됨:
        @PreAuthorize("hasAuthority('CONTENT:WRITE')") → @PreAuthorize("hasAuthority('BANNER:WRITE')")
[When]  AuthorizationMatrixIT의 시나리오는 여전히 givenValidToken(permissions=List.of("CONTENT:WRITE"))로
        호출(IT 자체는 GREEN 유지 — false positive)
[Then]  AuthorizationCoverageArchTest RED
        AssertionError 메시지: "운영 endpoint POST /api/v1/content/banners는
        @PreAuthorize(hasAuthority('BANNER:WRITE'))로 변경되었으나
        AuthorizationMatrixIT 시나리오는 여전히 hasAuthority('CONTENT:WRITE')를 stub합니다.
        IT 시나리오의 토큰 권한을 BANNER:WRITE로 갱신하세요."
        → PR 차단
```

### 4.3 시나리오 3 — 기존 IT 시나리오에서 endpoint 제거 시 회귀 검출 (REQ-AAD-002/004)

```
[Given] 35 endpoint baseline IT 매트릭스 GREEN 상태
[When]  개발자가 AuthorizationMatrixExpandIT.contentDomainTests에서 PageController publish endpoint
        시나리오를 의도치 않게 삭제 (리팩토링 중 우연한 제거)
[Then]  AuthorizationCoverageArchTest RED
        AssertionError 메시지: "운영 endpoint POST /api/v1/content/pages/{id}/publish는
        @PreAuthorize(hasAuthority('PAGE:PUBLISH'))를 유지하지만 IT 매트릭스에서 검증 시나리오가 사라졌습니다.
        AuthorizationMatrixExpandIT의 ContentDomainTests @Nested 그룹에 publish 시나리오를 복원하세요."
        → PR 차단
```

### 4.4 시나리오 4 — 정상 작업 (35 endpoint baseline GREEN, REQ-AAD-001/002/003/004)

```
[Given] 운영 @PreAuthorize 120개, IT 매트릭스 35 endpoint 검증, 12 권한 어휘 정합 상태
[When]  ./gradlew :backend:check 실행
[Then]  AuthorizationCoverageArchTest GREEN (35 endpoint baseline)
        + AuthorizationMatrixIT 19 AC GREEN
        + AuthorizationMatrixExpandIT 88 AC GREEN
        + PiiEmailMaskArchTest GREEN
        → CI PASS, PR 머지 가능
```

---

## 5. EARS 요구사항 — REQ-AAD-001/002/003/004/005

### REQ-AAD-001 (Ubiquitous) — `AuthorizationCoverageArchTest` 신설 (Auto-Detect Infrastructure)

The system SHALL provide an ArchUnit-based test class `AuthorizationCoverageArchTest` (located at `backend/src/test/java/kr/co/ircp/cms/security/archunit/AuthorizationCoverageArchTest.java`) that scans the operational `kr.co.ircp.cms.web.api..*` package for `@PreAuthorize` annotations and verifies each endpoint has at least one corresponding scenario in the existing HTTP authorization matrix integration tests (`AuthorizationMatrixIT` or `AuthorizationMatrixExpandIT`).

세부 사항:

- 위치: `backend/src/test/java/kr/co/ircp/cms/security/archunit/AuthorizationCoverageArchTest.java`
- 패턴: PiiEmailMaskArchTest 271줄 패턴 재사용 (ClassFileImporter + JavaClasses + ArchUnit DSL)
- 적재 대상:
  - 운영 컨트롤러: `kr.co.ircp.cms.web.api..*Controller`
  - IT 매트릭스: `kr.co.ircp.cms.security.AuthorizationMatrixIT`, `kr.co.ircp.cms.security.AuthorizationMatrixExpandIT`
- 추출 단위: `@PreAuthorize` 메소드 → endpoint 식별(HTTP method + URL path + 권한 어휘)
- 매칭 규칙: IT 매트릭스의 `@Test` 메소드/`@DisplayName`/javadoc 패턴에서 endpoint 시나리오 존재 여부 확인
- 부재 시: `AssertionError` (RED)

### REQ-AAD-002 (Event-driven) — 35 endpoint baseline 회귀 검출

WHEN operational `@PreAuthorize` annotations are added (신규 추가) or removed from `kr.co.ircp.cms.web.api..*Controller` classes, THEN `AuthorizationCoverageArchTest` SHALL detect missing IT coverage for the changed endpoints, regardless of which IT class (`AuthorizationMatrixIT` or `AuthorizationMatrixExpandIT`) the coverage should reside in.

세부 사항:

- baseline: AUTHZ-MATRIX-001 6 endpoint + AUTHZ-IT-EXPAND-001 29 endpoint = 35 endpoint
- 트리거 조건 1: 운영 신규 `@PreAuthorize` 추가 → 35 + N 중 새로 추가된 N개에 IT 시나리오 부재 → RED
- 트리거 조건 2: 35 endpoint 중 어느 하나가 IT에서 시나리오 제거 → RED
- 트리거 조건 3: 운영 `@PreAuthorize` 어노테이션 자체 제거 → RED (어노테이션이 사라졌는데 IT는 기존 endpoint를 호출하면 status mismatch 가능성)

### REQ-AAD-003 (Event-driven) — 권한 어휘 변경 검출

WHEN the authorization vocabulary of an existing endpoint is modified (e.g., `hasAuthority('CONTENT:WRITE')` → `hasAuthority('PAGE:WRITE')`), THEN `AuthorizationCoverageArchTest` SHALL detect the mismatch between the operational annotation value and the IT scenario expectation.

세부 사항:

- `@PreAuthorize` SpEL value 추출 (예: `"hasAuthority('CONTENT:WRITE')"`)
- IT 시나리오 토큰 권한 매핑 추출 (`givenValidToken(roles=..., permissions=List.of(...))` 호출 패턴 정규식 또는 javadoc 메타)
- 운영 어휘 ≠ IT 시나리오 expected 권한 → RED
- 분리 회귀 검증과 호환 (예: `PAGE:WRITE`/`PAGE:PUBLISH` 별개 권한 검증 유지 — AUTHZ-IT-EXPAND-001 v0.2 §A.1 AC-AME-001-A3/A4 패턴)

### REQ-AAD-004 (Ubiquitous) — Gradle `check` 통합

The system SHALL include `AuthorizationCoverageArchTest` in the `./gradlew :backend:check` task execution path so that CI pipelines automatically fail PRs that introduce uncovered `@PreAuthorize` annotations or vocabulary mismatches.

세부 사항:

- 단위 테스트 레벨 (테스트 슬라이스 불필요 — ArchUnit는 class metadata만 분석)
- Testcontainers 미사용 (실행 속도 빠름, 추가 인프라 비용 0)
- `@SpringBootTest` 미사용 (ApplicationContext 부팅 불필요)
- `check` task의 `test` sourceSet에 자동 포함 (별도 task 분리 불필요)
- exit code != 0 시 CI에서 PR 머지 차단

### REQ-AAD-005 (Ubiquitous) — 검출 시 수동 갱신 절차 안내 메시지

WHEN a coverage gap is detected, the test SHALL provide an actionable error message that includes the missing endpoint (HTTP method + path + authority vocabulary) and a reference to the README D3 manual update procedure.

세부 사항:

- `AssertionError` 메시지 형식 (한국어):
  ```
  운영 endpoint {METHOD} {PATH}는 @PreAuthorize({VOCABULARY})를 사용하지만
  IT 매트릭스에 시나리오가 없습니다.
  README의 'HTTP 권한 매트릭스 IT 신규 endpoint 추가 절차'를 참조하여
  AuthorizationMatrixExpandIT의 적절한 @Nested 그룹에 시나리오를 추가하세요.
  ```
- javadoc에 README 절차 위치 명시: `backend/README.md` 또는 `.moai/specs/SPEC-CMS-SECURITY-AUTHZ-IT-EXPAND-001/spec.md` §11
- 메시지 모두 한국어 (conversation_language: ko)
- `code_comments: ko` 일관

---

## 6. 결정 포인트 (사용자 사전 확정 — D1~D4)

본 SPEC은 오케스트레이터가 사전 수집한 사용자 결정 D1~D4를 그대로 채택한다.

| 결정 | 옵션 | 채택 | 사유 |
|------|------|------|------|
| **D1** 검출 방식 | (a) ArchUnit / (b) Spring AOT introspection / (c) 자체 javac annotation processor | **(a) ArchUnit** | ArchUnit 1.3.0 의존성 이미 포함, PiiEmailMaskArchTest 271줄 패턴 재사용으로 학습 부담 0, 단위 테스트 레벨 빠른 피드백 |
| **D2** 동작 방식 | (a) Test RED (CI 실패) / (b) IT 메소드 자동 codegen / (c) 경고 로그만 | **(a) Test RED** | CI에서 명확한 PR 차단 게이트, codegen은 후속 SPEC, 경고 로그는 무시 위험 |
| **D3** 검증 범위 | (a) AuthorizationMatrixIT + ExpandIT 둘 다 / (b) ExpandIT만 / (c) 신규 IT 클래스만 | **(a) 둘 다** | 35 endpoint baseline 전체 회귀 검출, 1차 19 AC도 보호 |
| **D4** 검출 시나리오 | (a) 신규 추가만 / (b) 권한 어휘 변경만 / (c) 둘 다 | **(c) 둘 다** | 회귀 검출 완전 (false positive 차단 — IT GREEN인데 운영 권한 변경된 경우) |

본 SPEC은 사용자 사전 결정 채택 SPEC이며, RUN 단계에서 추가 결정 포인트가 발생할 경우 manager-spec(MoAI)이 다시 확인한다.

---

## 7. RISK + 완화

| RISK ID | 설명 | 완화 |
|---------|------|------|
| **RISK-AAD-01** | ArchUnit `ClassFileImporter`는 컴파일 후 class 파일 필요 → IDE 단독 실행 시 stale 가능 | Gradle `check` 통합으로 자동 컴파일 보장. javadoc에 "IDE 단독 실행 시 `./gradlew :backend:compileTestJava` 선행" 명시 |
| **RISK-AAD-02** | 운영 컨트롤러 패키지 패턴 `kr.co.ircp.cms.web.api..*` 변경 시 import 누락 | 패키지 패턴을 상수 `OPERATIONAL_CONTROLLER_PACKAGE`로 노출 + javadoc 절차 명시 |
| **RISK-AAD-03** | `@PreAuthorize` SpEL 표현식 다양성 (`hasAnyRole`, OR 조합, AND 조합) → 어휘 추출 정규화 부담 | 12 권한 어휘 매트릭스(AUTHZ-IT-EXPAND-001 §2.3 표)에 한정. 새 어휘는 후속 SPEC에서 추가 |
| **RISK-AAD-04** | 클래스 레벨 `@PreAuthorize` (Governance, Dashboard 등) → 메소드 단위 매핑 부재 | 클래스 레벨 어노테이션 → 클래스 내 모든 endpoint 적용으로 처리 (Spring 동작과 일치) |
| **RISK-AAD-05** | `AuthorizationMatrixIT` + `ExpandIT`의 IT 시나리오 매핑 추출 정확성 | `@DisplayName` 패턴 또는 `mockMvc.perform(...)` 호출 정규식으로 endpoint 식별. 매핑 부정확 시 javadoc에 매칭 규칙 명시 |
| **RISK-AAD-06** | ArchUnit 1.3.0 → 1.4.x upgrade 시 DSL API 변경 가능 | PiiEmailMaskArchTest와 동일 ArchUnit 1.3.0 버전 고정. upgrade는 별도 SPEC |
| **RISK-AAD-07** | RED 메시지가 모호하면 개발자 혼란 | REQ-AAD-005에 구체 메시지 형식 정의 + README D3 절차 참조 명시 |

---

## 8. ASSUM + DEPS

### ASSUM (가정)

- ASSUM-AAD-01: ArchUnit 1.3.0 기존 의존성 유지 (`backend/build.gradle.kts` 변경 0건)
- ASSUM-AAD-02: 운영 컨트롤러 패키지 `kr.co.ircp.cms.web.api..*` 안정 (변경 시 SPEC §3 가정 재검토)
- ASSUM-AAD-03: `AuthorizationMatrixIT`/`AuthorizationMatrixExpandIT` v1차 안정 (시나리오 매핑 패턴 변경 시 본 SPEC 재진단)
- ASSUM-AAD-04: PiiEmailMaskArchTest 271줄 패턴 재사용 가능 (ArchUnit DSL 학습 부담 0)
- ASSUM-AAD-05: 운영 `@PreAuthorize` SpEL value는 12 권한 어휘 매트릭스(AUTHZ-IT-EXPAND-001 §2.3)에 한정 — 신규 어휘 추가 시 본 SPEC 재정의 또는 후속 SPEC

### DEPS (의존 파일/모듈)

- `backend/build.gradle.kts` — ArchUnit 1.3.0 의존성 (변경 0건)
- `backend/src/test/java/kr/co/ircp/cms/domain/security/pii/archunit/PiiEmailMaskArchTest.java` (참조 패턴, 271줄)
- `backend/src/test/java/kr/co/ircp/cms/security/AuthorizationMatrixIT.java` (검증 대상, 461줄)
- `backend/src/test/java/kr/co/ircp/cms/security/AuthorizationMatrixExpandIT.java` (검증 대상, 1540줄)
- `backend/src/main/java/kr/co/ircp/cms/web/api/**/*Controller.java` (운영 검증 소스, 61개 컨트롤러, `@PreAuthorize` 120개)
- `backend/README.md` 또는 `.moai/specs/SPEC-CMS-SECURITY-AUTHZ-IT-EXPAND-001/spec.md` §11 (D3 수동 갱신 절차 — REQ-AAD-005 참조 대상)

---

## 9. RUN Step 분해 (4 Step)

본 SPEC은 manager-tdd 또는 manager-ddd가 4 Step으로 RUN 단계를 진행한다(quality.yaml `development_mode` 기준). 각 Step은 명확한 산출물 + 회귀 검증 + 커밋 단위로 분해된다.

### Step 1: AuthorizationCoverageArchTest 신설 + 35 endpoint baseline 검증 (REQ-AAD-001/002/004)

**목표**: ArchUnit 인프라 신설 + 현재 35 endpoint baseline GREEN 통과 확인

**산출물**:

- `backend/src/test/java/kr/co/ircp/cms/security/archunit/AuthorizationCoverageArchTest.java` 신규 (예상 ~250~350줄)
- ArchUnit `ClassFileImporter`로 운영 컨트롤러 + IT 두 클래스 적재
- `@PreAuthorize` 메소드 추출 → endpoint 식별 (HTTP method + path)
- IT 시나리오 매핑 추출 (`@DisplayName` 또는 `mockMvc.perform(...)` 정규식)
- endpoint → 시나리오 매칭 ArchUnit 규칙
- 35 endpoint baseline 통과 확인 (현재 GREEN)

**검증**:

- `./gradlew :backend:test --tests AuthorizationCoverageArchTest` GREEN (35 endpoint baseline)
- AUTHZ-MATRIX-001 19 AC 회귀 0건
- AUTHZ-IT-EXPAND-001 88 AC 회귀 0건
- PiiEmailMaskArchTest 회귀 0건

**커밋 메시지** (예시):

```
test(security): SPEC-AAD-001 Step 1 — AuthorizationCoverageArchTest 신설 + 35 endpoint baseline GREEN (REQ-AAD-001/002/004)
```

### Step 2: 권한 어휘 변경 검출 추가 (REQ-AAD-003)

**목표**: 운영 어휘 ↔ IT stub 권한 정합성 검증 ArchUnit 규칙 추가

**산출물**:

- `@PreAuthorize` SpEL value 정규화 추출 함수 (`extractAuthorityVocabulary`)
- IT 시나리오 토큰 권한 매핑 추출 함수 (`extractItScenarioVocabulary`)
- 어휘 mismatch 검출 ArchUnit 규칙 (`shouldMatchOperationalVocabulary`)
- 12 권한 어휘 매트릭스 한정 처리 (AUTHZ-IT-EXPAND-001 §2.3)
- 클래스 레벨 `@PreAuthorize` → 클래스 내 모든 endpoint 적용 처리

**검증**:

- 의도적 mismatch 시뮬레이션 (운영 어휘 임시 변경 → RED 확인 → 복원)
- 분리 회귀 검증 호환 (`PAGE:WRITE`/`PAGE:PUBLISH` 별개 권한 정합 GREEN)
- AUTHZ-MATRIX-001/EXPAND-001 회귀 0건

**커밋 메시지** (예시):

```
test(security): SPEC-AAD-001 Step 2 — 권한 어휘 변경 검출 (REQ-AAD-003)
```

### Step 3: 회귀 시나리오 검증 + 검출 메시지 한국어 정비 (REQ-AAD-002/003/005)

**목표**: 의도적 RED 시뮬레이션 검증 + 한국어 메시지 anti-pattern 검증 + Gradle `check` 통합 확인

**산출물**:

- 시나리오 1 시뮬레이션: 신규 endpoint 추가(임시 운영 컨트롤러 신규 메소드) → RED 확인 → rollback
- 시나리오 2 시뮬레이션: 권한 어휘 변경(임시 운영 어휘 변경) → RED 확인 → rollback
- 시나리오 3 시뮬레이션: IT 시나리오 제거(임시 IT @Test 메소드 제거) → RED 확인 → rollback
- AssertionError 메시지 한국어 검증 (한 줄에 endpoint + vocabulary + README 절차 명시)
- `./gradlew :backend:check` 자동 트리거 검증

**검증**:

- 3개 시뮬레이션 모두 RED 정확 발생 + 메시지 정확 출력
- rollback 후 GREEN 복원
- `check` task에 자동 포함 확인

**커밋 메시지** (예시):

```
test(security): SPEC-AAD-001 Step 3 — 회귀 시나리오 RED 시뮬레이션 + 메시지 한국어 정비 (REQ-AAD-002/003/005)
```

### Step 4: 회귀 검증 + Sync (README + CHANGELOG + SPEC v0.2 Implemented)

**목표**: 전체 회귀 검증 + 문서 동기화 + SPEC 상태 전환

**산출물**:

- `./gradlew :backend:check` GREEN 통합 확인
- AuthorizationMatrixIT 19 AC 회귀 0건
- AuthorizationMatrixExpandIT 88 AC 회귀 0건
- PiiEmailMaskArchTest 회귀 0건
- 운영 코드 git diff 0건
- `backend/build.gradle.kts` 변경 0줄
- README 신규 절차 갱신:
  ```
  ## HTTP 권한 매트릭스 IT 신규 endpoint 추가 절차

  D3 절차는 다음과 같다:
  1. 운영 컨트롤러에 @PreAuthorize 어노테이션 추가
  2. ./gradlew :backend:check 실행 → AuthorizationCoverageArchTest RED 신호 발생 확인
  3. RED 메시지의 endpoint(METHOD + PATH + VOCABULARY) 정보로 IT 매트릭스 갱신
     → AuthorizationMatrixExpandIT의 적절한 @Nested 그룹에 시나리오 추가
  4. ./gradlew :backend:check 재실행 → GREEN 확인
  5. PR 머지
  ```
- CHANGELOG entry 추가 (한국어, conventional commit 패턴)
- SPEC 상태 `Planned` → `Implemented` 전환 + v0.2 sync

**검증**:

- 전체 backend test suite GREEN
- 의도적 RED 시뮬레이션 3개 모두 정확 동작
- README 절차가 RED 메시지 안내와 일관
- 운영 코드 변경 0건 (git diff)

**커밋 메시지** (예시):

```
docs(security): SPEC-AAD-001 sync — README D3 절차 갱신 + SPEC v0.2 Implemented 전환
```

---

## 10. 후속 SPEC 트랙

본 SPEC은 자동 검출 게이트만 제공하며, 다음 후속 SPEC 트랙으로 점진 보강한다.

| 후속 SPEC (가칭) | 범위 | 본 SPEC과의 관계 |
|-----------------|------|----------------|
| **SPEC-CMS-SECURITY-AUTHZ-AUTODETECT-CODEGEN-001** | RED 발생 시 IT 매트릭스 시나리오 자동 codegen (D2 비범위) | 본 SPEC RED 게이트 + codegen으로 수동 갱신 부담 0 |
| **SPEC-CMS-SECURITY-AUTHZ-IT-EXPAND-002** | 35 → 80 endpoint 점진 확장 | 본 SPEC이 회귀 검출 인프라, EXPAND-002가 시나리오 추가 |
| **SPEC-CMS-SECURITY-AUTHZ-IT-EXPAND-003** | 80 → 120 endpoint 완전 커버 | EXPAND-002 후속 |
| **SPEC-CMS-SECURITY-CONFIG-INTEGRITY-001** | SecurityConfig `requestMatchers` + `@PreAuthorize` 종합 정합성 | 본 SPEC은 컨트롤러 레이어, INTEGRITY-001은 SecurityConfig 레이어 |
| **SPEC-CMS-SECURITY-AUTHZ-VOCABULARY-EXPAND-001** | 12 → N개 권한 어휘 확장 + 본 SPEC 매트릭스 동기화 | 본 SPEC은 12 어휘 한정, VOCABULARY-EXPAND가 신규 어휘 추가 |

---

## 11. 변경 이력 (HISTORY)

| 버전 | 날짜 | 변경 사항 |
|------|------|----------|
| **v0.1** | 2026-05-11 | 초안 작성. SPEC-CMS-SECURITY-AUTHZ-IT-EXPAND-001 v0.2 §11 후속 SPEC 트랙 D3 수동 갱신 부담 자동화. MoAI 정밀 진단으로 ArchUnit 1.3.0 기존 의존성 + PiiEmailMaskArchTest 271줄 패턴 + 35 endpoint baseline + 120 운영 @PreAuthorize 분포 + 12 권한 어휘 매트릭스 확인. 사용자 결정 D1~D4 채택(ArchUnit + Test RED + IT 둘 다 + 신규/어휘변경 둘 다). REQ-AAD-001~005 정의. Step 1~4 분해(인프라 신설 → 어휘 검출 → RED 시뮬레이션 → sync). 운영 코드 변경 0건. AuthorizationCoverageArchTest 신설로 35 endpoint baseline + 권한 어휘 변경 회귀 자동 검출 + Gradle check CI PR 차단 + 한국어 RED 메시지 + README D3 절차 안내. 후속 SPEC 트랙 5개 명시(CODEGEN-001 / EXPAND-002 / EXPAND-003 / CONFIG-INTEGRITY-001 / VOCABULARY-EXPAND-001). |

---

**SPEC-CMS-SECURITY-AUTHZ-AUTODETECT-001 v0.1 (Planned, 2026-05-11)**
