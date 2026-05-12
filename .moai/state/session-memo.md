# Session Memo — 2026-05-12 Security Tracks Session

## P1: Session Context

- session_id: 27cd9e30-649f-4b69-837f-dce613cc8e8e
- cwd: /home/sklee/moai/iroum-cms
- branch: feature/security-tracks-2026-05-12
- commits ahead of main: 12 (총 20 commits in branch)
- 마지막 commit: `5a0fa0b` (META v0.3 Evidence 강화)

## P2: 본 세션 종합 산출 (20 commits)

### 4 SPEC 완성 + 2 SPEC 회복

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

- Branch: feature/security-tracks-2026-05-12 (main + 12 commits)
- PR 보고서: `.moai/reports/pr-2026-05-12-security-tracks.md` (v3)
- GitHub remote 미설정 — URL 받으면 즉시 push + gh pr create 가능

## P7: META 정책 정식 적용 사례 (3건)

본 세션에서 정식 적용 사례 3건 완성:

1. **PII-FOLLOWUP-005 v0.3** — race condition 회피 (@DirtiesContext, 단독+통합 양쪽 GREEN)
2. **AUTHZ-IT-EXPAND-002 v0.3** — assertAuthzPassed helper (도메인 예외 처리)
3. **AUTHZ-IT-REGRESSION-001 v0.8** — 종합 회귀 검증 + 59→0 100% 회복

## P8: 핵심 발견 패턴 (다음 SPEC 참고)

1. **응답 코드 변경 회귀**: AUTH_FORBIDDEN → ACCESS_DENIED (GlobalExceptionHandler `AuthorizationDeniedException` 핸들러 추가 시점 `942b19e` 2026-05-06)
2. **@Valid validation 우선 발생**: 403 expected but 400 — @PreAuthorize 전에 @RequestBody/@RequestParam validation 실행
3. **@WebMvcTest Security 한계**: SecurityAutoConfiguration 제외 → SecurityFilterChain 없음 → anonymous → @PreAuthorize → 403 (운영 401과 다름)
4. **service 도메인 예외**: GlobalExceptionHandler가 IllegalArgumentException/SiteMultiDisabledException 미커버 → ServletException wrap → assertAuthzPassed helper
5. **race condition**: SyncTaskExecutor + @Async + @Transactional(REQUIRES_NEW) 통합 race → @DirtiesContext AFTER_EACH_TEST_METHOD

## P9: 통계

- Commits: 20 (feature branch, 12 ahead of main)
- Files changed: 27
- Lines: +2080 / -185
- Tests added/modified: 200+
- 운영 코드 변경: **0건**
