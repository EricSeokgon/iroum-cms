# SPEC-CMS-TEST-INFRA-RECONFIG-001 — 인수 기준 (Acceptance Criteria)

본 문서는 SPEC-CMS-TEST-INFRA-RECONFIG-001 테스트 인프라 잔여 갭 해소(JaCoCo report integrationTest 통합 + check task 통합 + CI workflow integrationTest 실행 보장)의 Given/When/Then 형식 인수 시나리오와 품질 게이트를 정의한다. 모든 시나리오는 `backend/build.gradle.kts` + `.github/workflows/ci.yml` 변경으로 검증 가능해야 한다.

본 SPEC은 운영 코드(`backend/src/main/java`) 변경 0건 + 신규 IT 작성 0건이 강제된다. 5/7 코드 리뷰 C2 권고 4건 중 3건은 commit `0b3d05e` (PII-002 Step 4) 시점 이전 적용 완료되었으며, 본 SPEC은 잔여 갭 3건(REQ-TIR-001/002/003) 해소에 한정된다.

---

## A. REQ-TIR-001 — JaCoCo executionData integrationTest 통합

### AC-TIR-001-1 — JaCoCo report executionData에 integrationTest.exec 포함 (Docker 가용 환경)

- **Given**: `backend/build.gradle.kts`의 `tasks.jacocoTestReport`에 `executionData(fileTree(layout.buildDirectory.dir("jacoco")) { include("*.exec") })` 또는 동등 패턴 적용 + `dependsOn(tasks.test, "integrationTest")` 추가. 사용자 환경에 Docker 가용 (Testcontainers PostgreSQL 16 컨테이너 시작 가능)
- **When**: `./gradlew :backend:test integrationTest jacocoTestReport` 실행
- **Then**:
  - `> Task :backend:test` BUILD SUCCESSFUL → `build/jacoco/test.exec` 생성
  - `> Task :backend:integrationTest` BUILD SUCCESSFUL → `build/jacoco/integrationTest.exec` 생성
  - `> Task :backend:jacocoTestReport` BUILD SUCCESSFUL → `build/reports/jacoco/test/html/index.html` 생성
  - HTML 보고서에 통합 경로 커버리지 반영 — IT 코드 경로(예: `PiiAuditEnhanceIT`가 호출하는 운영 PII 감사 코드) 라인 커버리지 측정값 가시화
  - XML 보고서(`build/reports/jacoco/test/jacocoTestReport.xml`)에 통합 경로 커버리지 반영

### AC-TIR-001-2 — Docker 미가용 환경 fallback (jacocoTestReport 정상 생성)

- **Given**: `backend/build.gradle.kts` AC-TIR-001-1 동일 적용. 사용자 환경에 Docker 미설치 또는 Docker daemon 미실행
- **When**: `./gradlew :backend:test jacocoTestReport` 실행 (또는 `./gradlew :backend:check` REQ-TIR-002 적용 시)
- **Then**:
  - `> Task :backend:test` BUILD SUCCESSFUL → `build/jacoco/test.exec` 생성
  - `> Task :backend:integrationTest` SKIPPED (또는 BUILD SUCCESSFUL with `Assumptions.assumeTrue(false)`) — IT 컨테이너 시작 불가로 SKIP
  - `build/jacoco/integrationTest.exec` 미생성 (정상)
  - `> Task :backend:jacocoTestReport` BUILD SUCCESSFUL — `executionData` fileTree 패턴이 `integrationTest.exec` 부재를 허용 → `test.exec`만으로 보고서 정상 생성
  - HTML 보고서 정상 생성 (단위 테스트 경로만 반영)

### AC-TIR-001-3 — jacocoTestReport.dependsOn에 integrationTest 포함

- **Given**: AC-TIR-001-1 동일 적용
- **When**: `./gradlew :backend:jacocoTestReport --dry-run` 실행
- **Then**:
  - 출력에 `:backend:test SKIPPED`, `:backend:integrationTest SKIPPED`, `:backend:jacocoTestReport SKIPPED` 순으로 task graph 표시
  - `jacocoTestReport`가 `test` + `integrationTest` 양쪽 의존 확인

---

## B. REQ-TIR-002 — check task integrationTest 의존 추가

### AC-TIR-002-1 — check task DAG에 integrationTest 포함 (--dry-run 검증)

- **Given**: `backend/build.gradle.kts`에 `tasks.named("check") { dependsOn("integrationTest") }` 추가
- **When**: `./gradlew :backend:check --dry-run` 실행
- **Then**:
  - 출력 task graph에 `:backend:test SKIPPED`, `:backend:integrationTest SKIPPED`, `:backend:check SKIPPED` 포함
  - `check` task가 `test` + `integrationTest` + (jacocoTestCoverageVerification — 이미 적용된 경우) 모두 의존 확인

### AC-TIR-002-2 — ./gradlew check 실행 시 integrationTest 자동 실행 (Docker 가용)

- **Given**: AC-TIR-002-1 동일 적용. Docker 가용 환경
- **When**: `./gradlew :backend:check` 실행 (사용자 Java 17 환경)
- **Then**:
  - 출력에 `> Task :backend:test`, `> Task :backend:integrationTest`, `> Task :backend:check` 순으로 실행
  - `> Task :backend:integrationTest` 출력에 `PiiAuditEnhanceIT`, `AuthorizationMatrixIT`, `PiiEmailIntegrationTest`, `MigrationOrderIT` 등 기존 IT 클래스 실행 확인
  - 모든 IT GREEN → `> Task :backend:check` BUILD SUCCESSFUL

### AC-TIR-002-3 — ./gradlew build 실행 시 integrationTest 자동 실행

- **Given**: AC-TIR-002-1 동일 적용. Docker 가용 환경
- **When**: `./gradlew :backend:build` 실행
- **Then**:
  - Gradle build → check → integrationTest 의존 체인으로 IT 자동 실행
  - `> Task :backend:integrationTest` 실행 후 `> Task :backend:check`, `> Task :backend:assemble`, `> Task :backend:build` 순으로 BUILD SUCCESSFUL
  - `build/jacoco/integrationTest.exec` 생성 확인

### AC-TIR-002-4 — Docker 미가용 환경 check 통과

- **Given**: AC-TIR-002-1 동일 적용. Docker 미설치 환경
- **When**: `./gradlew :backend:check` 실행
- **Then**:
  - `> Task :backend:integrationTest` 진입 → `AbstractIntegrationTest:58 Assumptions.assumeTrue(...)` 평가 → false → 모든 IT 메소드 SKIP
  - `> Task :backend:integrationTest` BUILD SUCCESSFUL (테스트 수 0건 또는 SKIPPED 표시)
  - `> Task :backend:check` BUILD SUCCESSFUL

### AC-TIR-002-5 — shouldRunAfter 유지 (test → integrationTest 순서)

- **Given**: AC-TIR-002-1 동일 적용. `tasks.integrationTest { shouldRunAfter(tasks.test) }` 기존 코드 유지
- **When**: `./gradlew :backend:check --dry-run` 실행
- **Then**:
  - task graph에 `test` → `integrationTest` 순서 유지 (Gradle DAG hint)
  - `dependsOn`은 의존 강제, `shouldRunAfter`는 순서 hint — 양쪽 모두 유지로 표준 Gradle 패턴 준수

---

## C. REQ-TIR-003 — CI workflow integrationTest 실행 보장

### AC-TIR-003-1 — ci.yml backend-test job에서 integrationTest 자동 실행 (옵션 1 채택)

- **Given**: `.github/workflows/ci.yml` 변경 없음 (옵션 1 — D4 채택). REQ-TIR-002 적용으로 `./gradlew build`가 `check`를 거쳐 `integrationTest` 자동 실행
- **When**: PR 또는 push to main/develop branches → backend-test job 트리거
- **Then**:
  - GitHub Actions backend-test job 로그에 `> Task :backend:integrationTest` 출력 확인
  - 모든 IT GREEN → `> Task :backend:build` BUILD SUCCESSFUL → backend-test job GREEN
  - `> Task :backend:jacocoTestReport` 실행 → 통합 보고서 생성 확인

### AC-TIR-003-2 — ci.yml 명시적 integrationTest task 호출 (옵션 2 fallback)

- **Given**: `.github/workflows/ci.yml`의 backend-test job에서 `./gradlew build jacocoTestReport` → `./gradlew build integrationTest jacocoTestReport`로 변경 (옵션 2 — REQ-TIR-002 미적용 또는 비활성화 시 fallback)
- **When**: PR 또는 push to main/develop branches → backend-test job 트리거
- **Then**:
  - 동일 결과 (옵션 1과 동일) — `> Task :backend:integrationTest` 명시적 실행
  - REQ-TIR-002 비활성화 시에도 IT 실행 보장

### AC-TIR-003-3 — JaCoCo 통합 보고서 artifact 업로드

- **Given**: AC-TIR-003-1 (옵션 1) 또는 AC-TIR-003-2 (옵션 2) 적용. ci.yml의 artifact 업로드 단계 유지
- **When**: backend-test job 완료
- **Then**:
  - artifact 업로드 결과(`backend/build/reports/jacoco/test/html/`)에 통합 보고서 포함
  - artifact 다운로드 후 `index.html` 열어 IT 코드 경로 라인 커버리지 반영 확인

### AC-TIR-003-4 — IT 회귀 시 PR 게이트 차단

- **Given**: AC-TIR-003-1 적용. 가상 PR이 IT를 RED로 만드는 운영 코드 변경 포함 (예: `JwtAuthenticationFilter` 정책 변경으로 `AuthorizationMatrixIT` 실패)
- **When**: PR 트리거 → backend-test job 실행
- **Then**:
  - `> Task :backend:integrationTest` RED → `> Task :backend:check` RED → `> Task :backend:build` RED → backend-test job RED
  - PR check status RED → merge 차단
  - 본 SPEC 적용 후 IT 회귀 검출 가시화 (정상 동작)

---

## D. Quality Gates

### D.1 빌드 스크립트 변경 적용

- `backend/build.gradle.kts`의 `tasks.jacocoTestReport`에 `executionData(...)` 명시 + `dependsOn("integrationTest")` 추가 (REQ-TIR-001)
- `backend/build.gradle.kts`에 `tasks.named("check") { dependsOn("integrationTest") }` 추가 (REQ-TIR-002)
- `backend/build.gradle.kts`의 `tasks.integrationTest { shouldRunAfter(tasks.test) }` 기존 코드 유지

### D.2 CI workflow 변경 적용 (또는 변경 없음)

- 옵션 1 채택 시: `.github/workflows/ci.yml` 변경 0줄 (REQ-TIR-002로 자동 처리)
- 옵션 2 채택 시: `.github/workflows/ci.yml` backend-test job에 `integrationTest` task 명시 추가

### D.3 운영 코드 변경 0건 강제

- `git diff --stat backend/src/main/` 출력은 0줄 (운영 코드 변경 없음)
- 본 SPEC 적용 commit은 `backend/build.gradle.kts` + (옵션 2 시) `.github/workflows/ci.yml`만 수정
- 운영 컨트롤러(`*Controller.java`) 수정 0건
- `SecurityConfig.java`, `WebMvcTestInfraConfig.java` 수정 0건
- 데이터베이스 스키마 / Flyway 마이그레이션 수정 0건

### D.4 jacocoTestCoverageVerification 임계치 통과

- `./gradlew :backend:check` 실행 시 `> Task :backend:jacocoTestCoverageVerification` BUILD SUCCESSFUL
- minimum 0.80 (현재 정책 — `build.gradle.kts:210`) 통과 유지
- 통합 경로 추가로 임계치 상승 또는 유지 (감소 가능성 낮음 — IT가 운영 코드 추가 라인 커버)

### D.5 다른 IT 회귀 0건

본 SPEC 적용 후 다음 IT 모두 GREEN 유지:

- `./gradlew :backend:integrationTest --tests "kr.co.ircp.cms.security.PiiEmailIntegrationTest"` GREEN (PII-FOLLOWUP-001 회귀 0건)
- `./gradlew :backend:integrationTest --tests "kr.co.ircp.cms.security.AuthorizationMatrixIT"` GREEN (AUTHZ-MATRIX-001 19 AC 회귀 0건)
- `./gradlew :backend:integrationTest --tests "kr.co.ircp.cms.security.PiiAuditEnhanceIT"` GREEN (PII-002 Step 4 회귀 0건)
- `./gradlew :backend:integrationTest --tests "kr.co.ircp.cms.governance.MigrationOrderIT"` GREEN
- 기존 단위 테스트 회귀 0건 (`./gradlew :backend:test` GREEN)

### D.6 신규 파일 0건 강제

- `git status --porcelain` 출력에 `??` (untracked) 신규 IT 파일 0건
- `??` 신규 운영 코드 파일 0건
- 본 SPEC 적용은 `backend/build.gradle.kts` + (옵션 2 시) `.github/workflows/ci.yml` 수정만 수행

### D.7 LSP 0 errors

- `backend/build.gradle.kts` 변경 후 Gradle Kotlin DSL 컴파일 0 errors
- ArchUnit 규칙(존재 시) 회귀 0건

### D.8 Step 단위 검증

- Step 1 (build.gradle.kts JaCoCo + check 통합) 적용 후 `./gradlew :backend:check --dry-run` task graph에 `integrationTest` 포함 확인
- Step 2 (CI workflow — 옵션 1 채택 시 변경 없음) 적용 후 또는 즉시 Step 3 진행
- Step 3 (회귀 검증) — 사용자 Java 17 환경에서 `./gradlew :backend:check` BUILD SUCCESSFUL + JaCoCo 통합 보고서 생성 확인

### D.9 통합 커버리지 가시화 검증

- `build/reports/jacoco/test/html/index.html` 열어 IT 코드 경로 라인 커버리지 반영 확인
- 적용 전 vs 적용 후 통합 커버리지 측정값 비교 — 통합 경로 추가로 보고서 라인 커버리지 가시화

---

## E. Definition of Done

본 SPEC은 다음 조건을 모두 만족할 때 RUN 1차 완료로 간주한다.

1. **잔여 갭 3건 모두 해소** (REQ-TIR-001 + REQ-TIR-002 + REQ-TIR-003)
   - REQ-TIR-001: jacocoTestReport에 integrationTest exec 통합 (AC-TIR-001-1, AC-TIR-001-2, AC-TIR-001-3)
   - REQ-TIR-002: check task에 integrationTest 의존 추가 (AC-TIR-002-1 ~ AC-TIR-002-5)
   - REQ-TIR-003: CI workflow에서 integrationTest 자동 실행 (AC-TIR-003-1 또는 AC-TIR-003-2)
2. **JaCoCo 통합 보고서에 integrationTest 반영** (AC-TIR-001-1 + D.9)
   - HTML 보고서(`build/reports/jacoco/test/html/index.html`)에 IT 코드 경로 라인 커버리지 반영
   - XML 보고서(`build/reports/jacoco/test/jacocoTestReport.xml`)에 통합 경로 커버리지 반영
3. **CI PR 게이트에서 integrationTest 자동 실행** (AC-TIR-003-1 + AC-TIR-003-4)
   - GitHub Actions backend-test job에서 IT 자동 실행
   - IT 회귀 시 PR check status RED → merge 차단
4. **운영 코드 0건 변경** (D.3 강제)
   - `git diff --stat backend/src/main/` 출력 0줄
5. **신규 IT 작성 0건** (D.6 강제)
   - 본 SPEC은 기존 IT 인프라 통합 — 신규 IT 작성 없음
6. **다른 IT 회귀 0건** (D.5)
   - PII-FOLLOWUP-001, AUTHZ-MATRIX-001, PII-002 Step 4, CTRL-AUTHZ-COVERAGE-001 모두 GREEN 유지
7. **jacocoTestCoverageVerification 임계치 통과** (D.4)
   - minimum 0.80 통과 유지
8. **LSP 0 errors** (D.7)
9. **5/7 코드 리뷰 C2 잔여 갭 100% 해소** (§9.2)
   - 5/7 권고 4건 중 3건은 commit `0b3d05e` 시점 적용 완료
   - 잔여 갭 3건 본 SPEC으로 해소
10. **사용자 테스트 실행 안내** (Java 17 환경): 본 SPEC 작성 세션에서는 Java 17 미설치로 단위/통합 테스트 실행 검증을 사용자 환경 위임
    - `./gradlew :backend:check --dry-run` (task graph 검증 — Java 미필요)
    - `./gradlew :backend:check` (사용자 Java 17 환경 — 실제 IT 실행)
    - `./gradlew :backend:build` (사용자 Java 17 환경 — JaCoCo 통합 보고서 생성)

---
