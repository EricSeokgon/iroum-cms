---
id: SPEC-CMS-SECURITY-AUTHZ-IT-EXPAND-005
version: 0.2.0
status: Implemented
created: 2026-06-02
updated: 2026-06-09
author: MoAI
priority: P1
parent: SPEC-CMS-SECURITY-AUTHZ-AUTODETECT-001
related:
  - SPEC-CMS-SECURITY-AUTHZ-IT-EXPAND-001 (인가 매트릭스 IT 확장 시리즈)
  - SPEC-CMS-SECURITY-AUTHZ-IT-EXPAND-002
  - SPEC-CMS-SECURITY-AUTHZ-IT-EXPAND-003
  - SPEC-CMS-SECURITY-AUTHZ-IT-EXPAND-004
  - SPEC-CMS-SECURITY-AUTHZ-AUTODETECT-001 (AuthorizationCoverageArchTest 회귀 가드)
issue_number: TBD
---

# SPEC-CMS-SECURITY-AUTHZ-IT-EXPAND-005 신규 엔드포인트 인가 IT 커버리지 복원

## HISTORY

- v0.1 / 2026-06-02 / MoAI / CI baseline 복구 작업 중 `AuthorizationCoverageArchTest`가 운영 `@PreAuthorize` 메서드 124건 vs baseline 113건(차이 11건)을 검출. 신규 엔드포인트가 인가 IT 시나리오(401/403 검증) 없이 추가된 보안 테스트 커버리지 갭. arch 테스트 3개 메서드를 `@Disabled`로 일시 격리하고 본 SPEC으로 복원을 추적. 격리된 메서드 재활성화가 완료 기준.
- v0.2 / 2026-06-09 / MoAI / Implemented (PR #6, commit 6dc5e24). `AuthorizationMatrixExpand5IT` 신설(88 @DisplayName 시나리오, BoardDomain/ContentDomain/SystemDomain/AuthUserDomain/DashboardPreferenceDomain 분류)로 IT 미커버 운영 @PreAuthorize 엔드포인트 28건의 401/403 인가 시나리오 추가. `AuthorizationCoverageArchTest` baseline 의도적 갱신: 메서드 레벨 카운트 113→124(+11), IT endpoint set 110→138(차집합 +28 — 메서드 카운트 증가분과 다른 것은 113 시점에도 IT 미커버였던 기존 엔드포인트 포함). `@Disabled` 3개 메서드(operational_preAuthorize_baselineCount / it_displayName_endpointBaselineCount / it_endpointSet_matchesBaseline88) 재활성화 완료, 4개 메서드 전부 GREEN 검증.

---

## 1. 배경

`AuthorizationCoverageArchTest`(SPEC-CMS-SECURITY-AUTHZ-AUTODETECT-001)는 운영 컨트롤러의 `@PreAuthorize` 어노테이션과 인가 매트릭스 IT(`AuthorizationMatrix*IT`)의 `@DisplayName` 시나리오 정합성을 ArchUnit으로 자동 검증하는 **보안 회귀 가드**다. 신규 인가 엔드포인트가 추가되면 대응 IT 시나리오를 강제한다.

장기간 백엔드 테스트 컴파일 실패(`compileTestJava`)로 이 가드가 실행되지 못하는 동안, 운영 `@PreAuthorize` 메서드가 baseline 113건 → **124건**으로 증가(11건 추가)했으나 인가 IT 시나리오와 baseline은 갱신되지 않았다.

컴파일 복구 후 가드가 재활성되며 RED가 되었고, baseline을 기계적으로 상향하는 것은 회귀 가드를 무력화하므로 금지된다(arch 테스트 주석 명시). 따라서 실패 3개 메서드를 일시 격리하고 본 SPEC으로 정식 복원을 추적한다.

## 2. 격리된 테스트 (재활성화 대상)

`backend/src/test/java/kr/co/ircp/cms/security/archunit/AuthorizationCoverageArchTest.java`:

| 메서드 | AC | 격리 사유 |
|--------|----|-----------|
| `operational_preAuthorize_baselineCount` | AC-AAD-001-1 | 운영 카운트 124 ≠ baseline 113 |
| `it_displayName_endpointBaselineCount` | AC-AAD-001-2 | IT 추출 endpoint set 불일치 |
| `it_endpointSet_matchesBaseline88` | AC-AAD-002-1 | baseline 110 endpoint ↔ IT set 불일치 |

`@Disabled("SPEC-CMS-SECURITY-AUTHZ-IT-EXPAND-005: ...")`로 표시됨. `AC-AAD-003-1`(권한 어휘 검증)은 통과 상태로 활성 유지.

## 3. 요구사항 (EARS)

- **REQ-AIE5-001 (신규 엔드포인트 식별 — Ubiquitous)**
  시스템 분석가는 baseline 113 → 124 사이에 추가된 운영 `@PreAuthorize` 엔드포인트 11건을 정확히 식별해야 한다.
- **REQ-AIE5-002 (인가 IT 시나리오 작성 — Event-driven)**
  WHEN 신규 엔드포인트가 식별되면, THEN 각 엔드포인트에 대해 인증 부재(401) 및 권한 부족(403) 검증 IT 시나리오를 `AuthorizationMatrixExpand*IT`에 추가해야 한다.
- **REQ-AIE5-003 (baseline 동시 갱신 — State-driven)**
  IF IT 시나리오가 추가되면, THEN `AuthorizationCoverageArchTest`의 `baselineEndpoints()` set과 카운트 baseline(113→124, 110→갱신값)을 의도적으로 함께 갱신해야 한다.
- **REQ-AIE5-004 (가드 재활성화 — Unwanted)**
  시스템은 격리된 3개 `@Disabled` 메서드의 어노테이션을 제거하기 전, 해당 테스트가 GREEN임을 검증해야 한다. RED인 채로 재활성화해서는 안 된다.

## 4. 완료 기준 (Acceptance)

- [x] baseline 113 → 124 차이 식별 — 메서드 레벨 카운트 +11, IT endpoint set 차집합 +28 (113 시점 미커버 포함)
- [x] 각 엔드포인트 401/403 인가 IT 시나리오 추가 (`AuthorizationMatrixExpand5IT` 88 @DisplayName)
- [x] `AuthorizationCoverageArchTest`의 baseline(카운트 124 + endpoint set 138) 의도적 갱신
- [x] 격리된 3개 메서드의 `@Disabled` 제거 후 4개 메서드 모두 GREEN
- [x] 표준 CI 환경에서 `./gradlew build` 통과 확인 (PR #6 머지)

## 5. 참고

- 본 SPEC은 `SPEC-CMS-DASHBOARD-REFRESH-001`(대시보드 자동 새로고침)과 무관한 사전 존재 보안 부채를 다룬다.
- ArchUnit 로컬 실행 시 `-PbuildDir` 비표준 경로는 `ONLY_INCLUDE_TESTS` 테스트 클래스 탐색을 깨뜨릴 수 있으므로, 검증은 표준 buildDir 또는 CI에서 수행할 것.
