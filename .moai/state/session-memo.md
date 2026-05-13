# Session Memo — 2026-05-12 Security Tracks Session (Final)

## P1: Session Context

- session_id: 27cd9e30-649f-4b69-837f-dce613cc8e8e
- cwd: /home/sklee/moai/iroum-cms
- branch: feature/security-tracks-2026-05-12
- commits ahead of main: **28** (총 36 commits in branch)
- 마지막 commit: `7606e37` (PR Summary v5)

## P2: 본 세션 종합 산출 (36 commits)

### 5 SPEC Implemented + 2 SPEC 회복 + AUTHZ 305 AC + EXPAND-003 신규

| SPEC | Status | 핵심 산출 |
|------|--------|-----------|
| PII-FOLLOWUP-005 | Implemented v0.3 | @DirtiesContext 5/5 GREEN |
| AUTHZ-IT-EXPAND-002 | Implemented v0.3 | 19 어휘 × 57 AC |
| **AUTHZ-IT-EXPAND-003** | **Implemented v0.4 (신규)** | **8 도메인 × 106 AC + baseline 88** |
| META-IT-GREEN-MANDATORY-001 | Implemented v0.3 | Evidence 10건 + REQ 신설 |
| AUTHZ-IT-REGRESSION-001 | Fully Implemented v0.8 | 59 RED → 0 (100% 회복) |
| PII-FOLLOWUP-004 | Implemented v0.4 (회복) | AC-009-2 후속 해결 |
| AUTHZ-IT-EXPAND-001 | Implemented (회복) | v0.2 회귀 회복 |
| AUTHZ-MATRIX-001 | Implemented (회복) | v0.8 회복 |

### AUTHZ 트랙 종합 검증 (356 tests / 0 failures)

| IT Class | Tests | Status |
|----------|-------|--------|
| AuthorizationMatrixIT (1차) | 19 | ✅ |
| AuthorizationMatrixExpandIT (2차) | 87 | ✅ |
| AuthorizationMatrixExpand2IT (3차) | 58 | ✅ |
| **AuthorizationMatrixExpand3IT (4차)** | **107** | **✅ 신규** |
| AuthorizationCoverageArchTest (ArchUnit) | 4 | ✅ baseline 88 |
| Controller unit tests (12종) | 81 | ✅ |
| **합계** | **356** | **100% GREEN** |

### 6중 OWASP A01 검증 (305 AC)

| 레이어 | AC | endpoint |
|--------|-----|----------|
| HTTP 1+2+3+4차 (MATRIX + EXPAND-001/002/003) | 270 | 89 |
| 메소드 슬라이스 (CTRL-AUTHZ-COVERAGE-001) | 31 | - |
| ArchUnit 자동 검출 (AUTODETECT-001) | 4 | 88 baseline |
| **합계** | **305** | **88** |

운영 114 controller / IT 88 = **79% 커버**

### 4 SPEC 완성 + 2 SPEC 회복 (legacy 표)

| SPEC | Status | 주요 산출 |
|------|--------|-----------|
| PII-FOLLOWUP-005 | Implemented v0.3 | @DirtiesContext 5/5 GREEN |
| AUTHZ-IT-EXPAND-002 | Implemented v0.3 | 19 어휘 × 57 AC + ArchUnit baseline 54 |
| META-IT-GREEN-MANDATORY-001 | Implemented v0.3 | HARD 정책 + Sync checklist + Evidence 10건 |
| AUTHZ-IT-REGRESSION-001 | Fully Implemented v0.8 | 59 RED → 0 (100% 회복) |
| AUTHZ-IT-EXPAND-001 | Implemented (회복) | v0.2 회귀 100% 회복 |
| AUTHZ-MATRIX-001 | Implemented (회복) | v0.8 회복 |

### AUTHZ 트랙 종합 검증

`./gradlew test --tests "AuthorizationMatrix*" --tests "AuthorizationCoverageArchTest"` → **249 tests / 0 failures**

| IT Class | Tests | Failures |
|----------|-------|----------|
| AuthorizationMatrixIT | 19 | 0 |
| AuthorizationMatrixExpandIT | 87 | 0 |
| AuthorizationMatrixExpand2IT | 58 | 0 |
| AuthorizationCoverageArchTest | 4 | 0 |
| Controller unit tests (12종) | 81 | 0 |
| **합계** | **249** | **0** |

## P3: 회귀 회복 단계 (REGRESSION-001)

| Step | Commit | RED → GREEN |
|------|--------|-------------|
| Step 1 회귀 추적 | `ecb9f59` | `942b19e` (2026-05-06) 확정 |
| Step 2 Phase A | `3624c1c` | 31 → 23 (응답 코드 28건) |
| Step 2 Phase B1-B5 | `5eef304` + `22e2ab2` | 23 → 0 (DTO body + helper) |
| Step 4 Controller | `9e52912` | 12종 정정 |
| Step 5 Sync v0.7 | `be4b370` | 51→0 명시 |
| Step 6 MatrixIT v0.8 | `401931e` | 8 추가 → 59→0 (100%) |

## P4: 운영 코드 변경 0건 (본 세션 일관)

- IT 시나리오 정정 + SPEC 문서 + 정책 + ArchTest baseline만 변경
- 운영 controller / service / SecurityConfig / GlobalExceptionHandler 무변경

## P5: 다음 세션 후보

| 우선순위 | 작업 | 비용 |
|---------|------|------|
| **즉시** | GitHub remote URL → `gh pr create` | URL 입력 1회 |
| P2 | AUTHZ-IT-EXPAND-003 (가칭, 120 endpoint 전체) | 대규모 |
| P2 | Frontend E2E (Playwright) | 대규모 인프라 |
| P3 | PII-KMS-001 + PII-ROTATION-001 | KMS 인프라 의존 |

## P6: PR 준비 상태

- Branch: feature/security-tracks-2026-05-12 (main + 28 commits)
- PR 보고서: `.moai/reports/pr-2026-05-12-security-tracks.md` (**v5**)
- GitHub remote 미설정 — URL 받으면 즉시 push + gh pr create 가능

## P7: META 정책 정식 적용 사례 (4건)

본 세션에서 정식 적용 사례 4건 완성:

1. **PII-FOLLOWUP-005 v0.3** — race condition 회피 (@DirtiesContext, 단독+통합 양쪽 GREEN)
2. **AUTHZ-IT-EXPAND-002 v0.3** — assertAuthzPassed helper (도메인 예외 처리)
3. **AUTHZ-IT-REGRESSION-001 v0.8** — 종합 회귀 검증 + 59→0 100% 회복
4. **AUTHZ-IT-EXPAND-003 v0.4** — 8 도메인 일괄 활성화 + ArchUnit baseline 88 동기

## P8: 핵심 발견 패턴 (다음 SPEC 참고)

1. **응답 코드 변경 회귀**: AUTH_FORBIDDEN → ACCESS_DENIED (GlobalExceptionHandler `AuthorizationDeniedException` 핸들러 추가 시점 `942b19e` 2026-05-06)
2. **@Valid validation 우선 발생**: 403 expected but 400 — @PreAuthorize 전에 @RequestBody/@RequestParam validation 실행
3. **@WebMvcTest Security 한계**: SecurityAutoConfiguration 제외 → SecurityFilterChain 없음 → anonymous → @PreAuthorize → 403 (운영 401과 다름)
4. **service 도메인 예외**: GlobalExceptionHandler가 IllegalArgumentException/SiteMultiDisabledException 미커버 → ServletException wrap → assertAuthzPassed helper
5. **race condition**: SyncTaskExecutor + @Async + @Transactional(REQUIRES_NEW) 통합 race → @DirtiesContext AFTER_EACH_TEST_METHOD

## P9: 통계

- Commits: **36** (feature branch, **28 ahead of main**)
- Files changed: **35**
- Lines: **+4027 / -420**
- Tests added/modified: **356+** (AUTHZ 트랙 100% GREEN)
- 운영 코드 변경: **0건**
- 신규 IT class: **2개** (Expand2IT 832줄 + Expand3IT 1100줄)
- ArchUnit baseline: 35 → **88 endpoint**
- 회귀 회복: **59 RED → 0 (100%)**

## P10: 다음 작업 후보 (별도 세션 권장)

| 우선순위 | 작업 | 비용 |
|---------|------|------|
| **즉시** | GitHub URL → `gh pr create` (36 commits) | URL 입력 1회 |
| P2 | AUTHZ-IT-EXPAND-004 (가칭, 잔여 26 endpoint → 100% 커버) | 중규모 |
| P3 | PII-KMS-001 RUN (D1-D5 결정 + KMS 인프라) | 대규모 |
| P3 | PII-ROTATION-001 RUN (PII-KMS-001 의존) | 대규모 |
| P2 | Frontend E2E (Playwright) | 대규모 인프라 |
