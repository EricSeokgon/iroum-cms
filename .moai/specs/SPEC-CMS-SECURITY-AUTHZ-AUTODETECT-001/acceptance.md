# SPEC-CMS-SECURITY-AUTHZ-AUTODETECT-001 — 인수 기준 (Acceptance Criteria)

본 문서는 SPEC-CMS-SECURITY-AUTHZ-AUTODETECT-001 ArchUnit 기반 운영 `@PreAuthorize` 자동 검출(IT 매트릭스 누락 PR 차단)의 Given/When/Then 형식 인수 시나리오와 품질 게이트를 정의한다. 모든 시나리오는 ArchUnit 1.3.0 단위 테스트(`./gradlew :backend:test --tests AuthorizationCoverageArchTest`)로 검증 가능해야 하며, Testcontainers/`@SpringBootTest` 인프라 없이 컴파일된 class 메타데이터만으로 동작한다.

본 SPEC은 SPEC-CMS-SECURITY-AUTHZ-IT-EXPAND-001 v0.2의 35 endpoint baseline과 12 권한 어휘 매트릭스를 그대로 보호 대상으로 하며, 운영 코드 변경 0건 + ArchUnit 의존성 변경 0줄을 보장한다.

검출 시나리오는 (1) 신규 운영 `@PreAuthorize` 추가 시 IT 매트릭스 누락 RED, (2) 기존 endpoint 권한 어휘 변경 시 IT stub 권한 mismatch RED, (3) IT 시나리오 의도치 않은 제거 시 baseline 회귀 RED, (4) `@PreAuthorize` 어노테이션 자체 제거 시 RED 4종을 모두 커버한다.

---

## A. REQ-AAD-001 — `AuthorizationCoverageArchTest` 인프라 (3 AC)

본 영역은 ArchUnit 단위 테스트 클래스 신설 + 운영/IT 클래스 동시 적재 + `@PreAuthorize` 메소드 추출 정확성을 검증한다.

### AC-AAD-001-1 — `AuthorizationCoverageArchTest` 클래스 신설 + ArchUnit 적재 GREEN

- **Given**: ArchUnit 1.3.0 의존성이 `backend/build.gradle.kts`에 이미 포함된 상태. `backend/src/test/java/kr/co/ircp/cms/security/archunit/` 디렉토리 존재 (또는 신설)
- **When**: `./gradlew :backend:test --tests AuthorizationCoverageArchTest` 실행
- **Then**:
  - `AuthorizationCoverageArchTest.java` 클래스 신설 확인 (예상 ~250~350줄)
  - PiiEmailMaskArchTest 271줄 패턴 일관 (`ClassFileImporter` + `JavaClasses` + ArchUnit DSL)
  - 테스트 실행 정상 완료 (실행 자체 GREEN)
  - 컴파일 에러 0건

### AC-AAD-001-2 — 운영 controller 패키지 + IT 두 클래스 동시 적재 GREEN

- **Given**: `kr.co.ircp.cms.web.api..*Controller` 패키지에 61개 컨트롤러 + `kr.co.ircp.cms.security.AuthorizationMatrixIT` (461줄) + `kr.co.ircp.cms.security.AuthorizationMatrixExpandIT` (1540줄) 모두 컴파일된 상태
- **When**: `AuthorizationCoverageArchTest`가 `ClassFileImporter`로 다음을 모두 적재
  - `kr.co.ircp.cms.web.api..*` (운영 컨트롤러)
  - `kr.co.ircp.cms.security.AuthorizationMatrixIT`
  - `kr.co.ircp.cms.security.AuthorizationMatrixExpandIT`
- **Then**:
  - 적재 자체 GREEN (`JavaClasses` 컬렉션 정상 구성)
  - 운영 컨트롤러 클래스 수 ≥ 61 (운영 코드 변경 시 ≥ 갱신)
  - IT 두 클래스 적재 확인

### AC-AAD-001-3 — `@PreAuthorize` 메소드 추출 정확성 (120개 모두 매칭)

- **Given**: 운영 `@PreAuthorize` 어노테이션 분포 120개 (MoAI 정밀 진단 — 2026-05-11)
- **When**: ArchUnit DSL로 `@PreAuthorize` 어노테이션 보유 메소드 + 클래스 추출
  - 메소드 레벨 어노테이션
  - 클래스 레벨 어노테이션 → 클래스 내 모든 endpoint로 확산 처리 (Spring 동작 일치)
- **Then**:
  - 추출 메소드 수 = 120개 (메소드 레벨) + 클래스 레벨 확산분
  - 추출 결과 누락/중복 0건
  - SpEL value 추출 정확 (예: `"hasAuthority('CONTENT:WRITE')"`)

---

## B. REQ-AAD-002 — 35 endpoint baseline 회귀 검출 (4 AC)

본 영역은 현재 35 endpoint baseline 통과 확인 + 신규 추가/제거 시 RED 발생을 검증한다.

### AC-AAD-002-1 — 현재 35 endpoint baseline GREEN

- **Given**: AUTHZ-MATRIX-001 6 endpoint + AUTHZ-IT-EXPAND-001 29 endpoint = 35 endpoint × 3 시나리오 = baseline 운영 정합 상태
- **When**: `./gradlew :backend:test --tests AuthorizationCoverageArchTest` 실행
- **Then**:
  - GREEN (35 endpoint 모두 IT 매트릭스 시나리오 매칭 확인)
  - AssertionError 0건
  - 메시지 출력 0건 (정상 통과)

### AC-AAD-002-2 — 운영 신규 `@PreAuthorize` 추가 시 RED (시뮬레이션)

- **Given**: 임시로 운영 컨트롤러(예: `BannerController`)에 신규 endpoint 추가
  ```java
  @PostMapping("/api/v1/content/banners/{id}/archive")
  @PreAuthorize("hasAuthority('CONTENT:WRITE')")
  public ResponseEntity<Void> archive(@PathVariable Long id) { ... }
  ```
  IT 매트릭스에 archive endpoint 시나리오는 추가하지 않음
- **When**: `./gradlew :backend:test --tests AuthorizationCoverageArchTest` 실행
- **Then**:
  - RED (`AssertionError`)
  - 메시지에 `POST /api/v1/content/banners/{id}/archive` + `hasAuthority('CONTENT:WRITE')` + README D3 절차 참조 포함
  - 시뮬레이션 후 임시 endpoint rollback → 재실행 GREEN 복원

### AC-AAD-002-3 — `AuthorizationMatrixIT/ExpandIT` 시나리오 누락 시 RED

- **Given**: 임시로 `AuthorizationMatrixExpandIT.contentDomainTests`에서 `PageController publish` endpoint 시나리오를 의도적으로 제거(주석 처리 또는 삭제)
- **When**: `./gradlew :backend:test --tests AuthorizationCoverageArchTest` 실행
- **Then**:
  - RED (`AssertionError`)
  - 메시지에 `POST /api/v1/content/pages/{id}/publish` + `hasAuthority('PAGE:PUBLISH')` + 시나리오 복원 안내 포함
  - 시뮬레이션 후 IT 시나리오 rollback → 재실행 GREEN 복원

### AC-AAD-002-4 — 35 endpoint 중 운영 `@PreAuthorize` 어노테이션 자체 제거 시 RED

- **Given**: 임시로 운영 컨트롤러(예: `BannerController.create`)에서 `@PreAuthorize("hasAuthority('CONTENT:WRITE')")` 어노테이션을 의도적으로 제거 (어노테이션 줄만 삭제)
- **When**: `./gradlew :backend:test --tests AuthorizationCoverageArchTest` 실행
- **Then**:
  - RED (`AssertionError`)
  - 메시지에 어노테이션 부재 + IT 시나리오 존재 mismatch 명시
  - 시뮬레이션 후 어노테이션 rollback → 재실행 GREEN 복원
  - false positive 차단(IT GREEN인데 운영 권한 우회된 경우 RED 신호 발생)

---

## C. REQ-AAD-003 — 권한 어휘 변경 검출 (3 AC)

본 영역은 운영 어휘 ↔ IT stub 토큰 권한 정합성 검증을 다룬다.

### AC-AAD-003-1 — 운영 `@PreAuthorize` 어휘 변경 시 RED

- **Given**: 임시로 운영 `BannerController.create`의 권한 어휘를 변경
  ```java
  @PreAuthorize("hasAuthority('CONTENT:WRITE')") → @PreAuthorize("hasAuthority('BANNER:WRITE')")
  ```
  IT 시나리오는 여전히 `givenValidToken(permissions=List.of("CONTENT:WRITE"))` stub
- **When**: `./gradlew :backend:test --tests AuthorizationCoverageArchTest` 실행
- **Then**:
  - RED (`AssertionError`)
  - 메시지에 운영 어휘(`BANNER:WRITE`) ≠ IT stub 권한(`CONTENT:WRITE`) mismatch 명시
  - IT 시나리오 토큰 권한 갱신 안내 포함
  - 시뮬레이션 후 운영 어휘 rollback → 재실행 GREEN 복원

### AC-AAD-003-2 — IT 시나리오 토큰 권한 변경 시 RED

- **Given**: 임시로 `AuthorizationMatrixIT.bannerCreate` 시나리오의 stub 토큰 권한을 변경
  ```java
  givenValidToken(permissions=List.of("CONTENT:WRITE")) → givenValidToken(permissions=List.of("BANNER:WRITE"))
  ```
  운영 컨트롤러는 여전히 `@PreAuthorize("hasAuthority('CONTENT:WRITE')")`
- **When**: `./gradlew :backend:test --tests AuthorizationCoverageArchTest` 실행
- **Then**:
  - RED (`AssertionError`)
  - 메시지에 IT stub(`BANNER:WRITE`) ≠ 운영 어휘(`CONTENT:WRITE`) mismatch 명시
  - 시뮬레이션 후 IT 시나리오 rollback → 재실행 GREEN 복원

### AC-AAD-003-3 — 클래스 레벨 `@PreAuthorize` 정확 매핑

- **Given**: 클래스 레벨 `@PreAuthorize`가 적용된 컨트롤러(예: `GovernanceController`, `DashboardController` 등 — Step 1 정밀 식별 후 확정)
- **When**: ArchUnit이 클래스 레벨 어노테이션 → 클래스 내 모든 endpoint로 확산 처리
- **Then**:
  - 클래스 내 모든 endpoint에 동일 권한 어휘 적용 (Spring 동작 일치)
  - IT 시나리오 매칭 시 클래스 레벨 권한 어휘 기준 검증
  - 메소드 레벨 `@PreAuthorize`가 클래스 레벨을 override하는 경우 메소드 레벨 우선 (Spring 동작 일치)

---

## D. REQ-AAD-004 — Gradle `check` 통합 (2 AC)

본 영역은 CI 자동 트리거 + PR 차단 시그널을 검증한다.

### AC-AAD-004-1 — `./gradlew :backend:check` 실행 시 자동 트리거

- **Given**: `AuthorizationCoverageArchTest` 클래스가 `backend/src/test/java/kr/co/ircp/cms/security/archunit/`에 신설된 상태. Gradle `test` sourceSet에 포함됨
- **When**: `./gradlew :backend:check` 실행 (별도 task 분리 없이 표준 check 경로)
- **Then**:
  - `AuthorizationCoverageArchTest` 자동 실행 확인
  - 별도 `--tests` 인자 불필요
  - 다른 `:backend:test`/`:backend:integrationTest` task와 함께 실행
  - Testcontainers 미사용 (실행 속도 빠름 — PiiEmailMaskArchTest와 동등)

### AC-AAD-004-2 — CI PR 차단 시그널 명확 (RED 시 exit code != 0)

- **Given**: REQ-AAD-002/003 시뮬레이션 중 RED 발생 상태
- **When**: `./gradlew :backend:check` 종료 후 exit code 확인
- **Then**:
  - exit code != 0 (실패 신호)
  - CI 시스템(GitHub Actions / Jenkins 등)에서 PR 머지 차단
  - 표준 Gradle test report에 `AuthorizationCoverageArchTest` RED 표시
  - 메시지가 CI 로그에 명확 출력 (한국어, REQ-AAD-005 형식)

---

## E. REQ-AAD-005 — 검출 메시지 안내 (2 AC)

본 영역은 RED 발생 시 개발자가 즉시 행동 가능한 메시지 형식을 검증한다.

### AC-AAD-005-1 — `AssertionError` 메시지에 endpoint 정보 포함

- **Given**: REQ-AAD-002/003 RED 발생 상태
- **When**: 메시지 내용 검사
- **Then**: 메시지에 다음 모두 포함
  - HTTP method (예: `POST`)
  - URL path (예: `/api/v1/content/banners/{id}/archive`)
  - 권한 어휘 (예: `hasAuthority('CONTENT:WRITE')`)
  - 메시지 형식 일관 (다른 endpoint도 동일 패턴):
    ```
    운영 endpoint {METHOD} {PATH}는 @PreAuthorize({VOCABULARY})를 사용하지만
    IT 매트릭스에 시나리오가 없습니다.
    ```
  - 한국어 메시지 (`code_comments: ko`, `conversation_language: ko` 일관)

### AC-AAD-005-2 — README D3 절차 참조 명시

- **Given**: REQ-AAD-002/003 RED 발생 상태
- **When**: 메시지 내용 검사
- **Then**: 메시지에 README D3 절차 참조 포함
  - 명시 형식 예:
    ```
    README의 'HTTP 권한 매트릭스 IT 신규 endpoint 추가 절차'를 참조하여
    AuthorizationMatrixExpandIT의 적절한 @Nested 그룹에 시나리오를 추가하세요.
    ```
  - javadoc에도 동일 절차 위치 명시 (`backend/README.md` 또는 SPEC-CMS-SECURITY-AUTHZ-IT-EXPAND-001 §11)
  - 개발자가 즉시 행동 가능 (어디를 갱신해야 하는지 명확)

---

## F. Quality Gates

본 영역은 Step 1~4 완료 후 회귀 검증 + 운영 정책 보호 + Gradle build 안정성을 보장한다.

### F.1 — `AuthorizationCoverageArchTest` GREEN (35 endpoint baseline)

- 35 endpoint baseline 모두 IT 매트릭스 시나리오 매칭 확인
- AssertionError 0건
- `./gradlew :backend:test --tests AuthorizationCoverageArchTest` GREEN

### F.2 — AUTHZ-MATRIX-001 19 AC 회귀 0건

- `AuthorizationMatrixIT` 19 AC 모두 GREEN 유지
- 본 SPEC 작업으로 인한 IT 매트릭스 변경 0건
- 운영 SecurityFilterChain 회귀 검출 인프라 보존

### F.3 — AUTHZ-IT-EXPAND-001 88 AC 회귀 0건

- `AuthorizationMatrixExpandIT` 88 AC + 1 smoke = 89 `@Test` 모두 GREEN 유지
- 도메인별 `@Nested` 그룹(content/dashboard/auth/system/governance/board) 변경 0건
- 12 권한 어휘 회귀 검출 인프라 보존

### F.4 — `PiiEmailMaskArchTest` 회귀 0건

- 기존 ArchUnit 테스트 271줄 GREEN 유지
- ArchUnit 1.3.0 의존성 충돌 0건
- 패턴 재사용으로 인한 부작용 0건

### F.5 — 운영 코드 git diff 0줄

- `backend/src/main/java/**/*.java` 변경 0건
- `backend/src/main/resources/**` 변경 0건
- 운영 SecurityConfig 변경 0건
- `@PreAuthorize` 어노테이션 변경 0건 (Step 3 시뮬레이션은 rollback 필수)

### F.6 — `backend/build.gradle.kts` ArchUnit 의존성 변경 0줄

- ArchUnit 1.3.0 버전 유지
- 신규 의존성 추가 0건
- 기존 의존성 제거 0건

### F.7 — `./gradlew :backend:check` 전체 GREEN

- 본 SPEC `AuthorizationCoverageArchTest` GREEN
- 기존 모든 단위 테스트 + IT GREEN
- exit code 0 (CI PASS)
- Gradle build report 정상

---

## G. Definition of Done

본 SPEC 완료 기준 — Step 1~4 모두 통과해야 SPEC 상태를 `Implemented`로 전환한다.

| 기준 | 상태 |
|------|------|
| **G.1** `AuthorizationCoverageArchTest` 신설 (~250~350줄) | Step 1 완료 |
| **G.2** 35 endpoint baseline GREEN | Step 1 완료 |
| **G.3** 권한 어휘 변경 검출 ArchUnit 규칙 추가 | Step 2 완료 |
| **G.4** 신규 추가 / 어휘 변경 / 시나리오 제거 / 어노테이션 제거 4종 RED 시뮬레이션 검증 | Step 3 완료 |
| **G.5** 한국어 RED 메시지 형식 정비 (REQ-AAD-005) | Step 3 완료 |
| **G.6** Gradle `check` 자동 통합 + CI PR 차단 시그널 | Step 1~3 완료 |
| **G.7** README 신규 절차 갱신 (수동 → ArchUnit RED 신호 + GREEN 확인 5단계) | Step 4 완료 |
| **G.8** AUTHZ-MATRIX-001 19 AC + AUTHZ-IT-EXPAND-001 88 AC 회귀 0건 | Step 1~4 회귀 검증 |
| **G.9** PiiEmailMaskArchTest 회귀 0건 | Step 1~4 회귀 검증 |
| **G.10** 운영 코드 git diff 0줄 + `build.gradle.kts` 변경 0줄 | Step 4 sync 검증 |
| **G.11** CHANGELOG entry 추가 (한국어, conventional commit) | Step 4 sync |
| **G.12** SPEC 상태 `Planned` → `Implemented` 전환 + v0.2 sync | Step 4 sync |

---

**SPEC-CMS-SECURITY-AUTHZ-AUTODETECT-001 acceptance.md v0.1 (2026-05-11)**
