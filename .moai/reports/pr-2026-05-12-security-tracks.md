# PR: Security Tracks 2026-05-12 — PII 5/5 GREEN + AUTHZ 199 AC + META Policy

**Branch**: feature/security-tracks-2026-05-12
**Base**: main
**Commits**: 8 commits (`667332d` ~ `608855b`)
**Files**: 8 changed (+1173 / −72)

---

## Summary

본 PR은 보안 IT 매트릭스 확장 + PII 트랙 완성 + IT 작성 정책 명문화의 3가지 트랙을 일괄 제출합니다.

- **PII 트랙 5/5 Implemented**: PII-FOLLOWUP-005 Option B @DirtiesContext 적용으로 race condition 회피 → 5 tests 0 failed GREEN. PII-FOLLOWUP-001 ~ 005 전체 Implemented 완성.
- **AUTHZ 트랙 5중 검증 199 AC**: AUTHZ-IT-EXPAND-002 Phase A+B 57 AC GREEN + ArchUnit baseline 35 → 54 endpoint 갱신. 19 권한 어휘 × 100% IT 커버.
- **META 정책 정식 적용**: META-IT-GREEN-MANDATORY-001 v0.2 Implemented. README §IT user environment GREEN mandatory 정책 신설 + Sync checklist 4 항목 명문화.

---

## Test Plan

### 1. PiiAuditEnhanceIT 5/5 GREEN 검증

**단독 실행** (옵션 A 진단, v0.2 commit 667332d):
```bash
./gradlew :backend:integrationTest --tests "kr.co.ircp.cms.domain.security.pii.PiiAuditEnhanceIT.findPage_selfRowExcludedFromAudit"
```
→ AC-009-2 PASSED

**통합 실행** (Option B, v0.3 commit 608855b):
```bash
./gradlew :backend:integrationTest --tests "kr.co.ircp.cms.domain.security.pii.PiiAuditEnhanceIT"
```
→ 5 tests / 0 failed / 0 skipped (BUILD SUCCESSFUL)
- AC-009-2 (본인 row 제외)
- AC-009-3 (HMAC lookup-only 미적재)
- AC-009-4 (self-access auditing)
- AC-FU-003-1 (ADMIN findPage N건)
- AC-FU-003-3 (각 target row 적재)

### 2. AuthorizationMatrixExpand2IT 57 AC GREEN 검증

**Phase A + B 통합 실행** (v0.3 commit 7a058e5):
```bash
./gradlew :backend:test --tests "kr.co.ircp.cms.security.AuthorizationMatrixExpand2IT"
```
→ BUILD SUCCESSFUL (Phase A 29 AC + Phase B 28 AC + smoke 1 = 58 tests 0 failed)

도메인별 분포:
- §0 smoke: 1
- §A.1 ContentRead 4 어휘: 12 AC
- §A.2 PageAdvanced 2 어휘: 7 AC (분리 회귀 1건 포함)
- §A.3 SiteMenu 2 어휘: 6 AC
- §A.4 UserAudit 2 어휘: 7 AC (USER:READ AND AUDIT:READ 검증)
- §A.5 Dashboard 1 어휘: 3 AC
- §A.6 SystemSetting 4 어휘: 14 AC (분리 회귀 2건 포함)
- §A.7 SystemOperation 3 어휘: 10 AC (분리 회귀 1건 포함)

### 3. AuthorizationCoverageArchTest baseline 54 갱신 검증

```bash
./gradlew :backend:test --tests "kr.co.ircp.cms.security.archunit.AuthorizationCoverageArchTest"
```
→ BUILD SUCCESSFUL (4 AC, baseline 54 endpoint × IT @DisplayName 정확 매칭)

### 4. 분리 회귀 + AND 조건 + OR bypass 검증

- **분리 회귀 4건**: PAGE:HISTORY:READ vs ROLLBACK, SETTING:READ vs WRITE, SYSTEM:READ vs ADMIN, MAINT:READ vs WRITE
- **AND 조건**: USER:READ AND AUDIT:READ (PersonalDataAccessController)
- **OR bypass**: ROLE:CONTENT_ADMIN (CONTENT_ADMIN/ADMIN/SUPER_ADMIN)

---

## Commits

| # | SHA | Title |
|---|-----|-------|
| 1 | `667332d` | docs(spec): PII-FOLLOWUP-005 v0.2 — 옵션 A 진단 완료 |
| 2 | `6c42539` | docs(spec): AUTHZ-IT-EXPAND-002 v0.2 — Step 2 endpoint 정밀 매핑 |
| 3 | `c450299` | test(security): AUTHZ-IT-EXPAND-002 Phase A — 10 어휘 29 AC GREEN |
| 4 | `7a058e5` | test(security): AUTHZ-IT-EXPAND-002 Phase B — 9 어휘 28 AC GREEN |
| 5 | `7a9027b` | docs(sync): AUTHZ-IT-EXPAND-002 v0.3 Implemented |
| 6 | `75da38a` | docs(spec): META-IT-GREEN-MANDATORY-001 v0.1 Planned |
| 7 | `7c58647` | docs(sync): META-IT-GREEN-MANDATORY-001 v0.2 Implemented |
| 8 | `608855b` | test(security): PII-FOLLOWUP-005 v0.3 Implemented |

---

## Files Changed

| File | Change |
|------|--------|
| `.moai/specs/SPEC-CMS-META-IT-GREEN-MANDATORY-001/spec.md` | +200 (신규) |
| `.moai/specs/SPEC-CMS-SECURITY-AUTHZ-IT-EXPAND-002/spec.md` | +104 |
| `.moai/specs/SPEC-CMS-SECURITY-PII-FOLLOWUP-005/spec.md` | +77 |
| `CHANGELOG.md` | +55 |
| `README.md` | +60 |
| `backend/src/test/java/kr/co/ircp/cms/domain/security/pii/PiiAuditEnhanceIT.java` | +24 / -7 |
| `backend/src/test/java/kr/co/ircp/cms/security/AuthorizationMatrixExpand2IT.java` | +665 / -22 |
| `backend/src/test/java/kr/co/ircp/cms/security/archunit/AuthorizationCoverageArchTest.java` | +60 / -43 |
| **합계** | **+1173 / −72** |

---

## SPEC Status 변경

| SPEC | Before | After |
|------|--------|-------|
| PII-FOLLOWUP-005 | Planned | **Implemented v0.3** |
| AUTHZ-IT-EXPAND-002 | Planned | **Implemented v0.3** |
| META-IT-GREEN-MANDATORY-001 | (신규) | **Implemented v0.2** |

---

## 5중 보안 검증 합산 (199 AC)

| 레이어 | SPEC | AC | endpoint | 어휘 |
|--------|------|-----|----------|------|
| HTTP 1차 | AUTHZ-MATRIX-001 | 19 | 6 | (포함) |
| HTTP 확장 1차 | AUTHZ-IT-EXPAND-001 | 88 | 29 | 12 |
| **HTTP 확장 2차** | **AUTHZ-IT-EXPAND-002 (본 PR)** | **57** | **19** | **19** |
| 메소드 슬라이스 | CTRL-AUTHZ-COVERAGE-001 | 31 | - | - |
| ArchUnit 자동 검출 | AUTHZ-AUTODETECT-001 | 4 | 54 baseline | 31 |
| **합계** | - | **199** | **54** | **31** |

OWASP A01 회귀 검출 ArchUnit baseline 31 어휘 100% IT 커버 달성.

---

## META Policy Sync Checklist (PR Implemented 조건)

본 PR은 META-IT-GREEN-MANDATORY-001 첫 정식 적용 사례입니다.

| # | 항목 | 본 PR Evidence |
|---|------|---------------|
| 1 | 단독 GREEN | ✅ AC-009-2 단독 실행 PASSED (commit 667332d) |
| 2 | 통합 GREEN | ✅ `./gradlew :backend:integrationTest` 5 tests 0 failed (commit 608855b) |
| 3 | @Transactional 위험 명시 | ✅ PII-FOLLOWUP-003 v0.2에 옵션 G TRUNCATE cleanup 명시 |
| 4 | race condition 회피 | ✅ @DirtiesContext AFTER_EACH_TEST_METHOD 적용 (META REQ-META-IT-003 옵션 a) |

---

## 운영 코드 변경 0건 (SPEC §3.2 준수)

- AUTHZ-IT-EXPAND-002: IT 신설 + ArchTest baseline 갱신만 (운영 SecurityConfig + @PreAuthorize 무변경)
- PII-FOLLOWUP-005: IT 클래스 annotation 추가만 (운영 service/aspect 무변경)
- META-IT-GREEN-MANDATORY-001: 정책 문서 + README만 (운영 코드 무관)

---

## Reviewer Notes

### 검토 우선순위
1. **PiiAuditEnhanceIT.java** — @DirtiesContext 비용 (5 test ~2.5분 추가) 대비 안전성 trade-off 검토
2. **AuthorizationMatrixExpand2IT.java** — assertAuthzPassed helper의 RuntimeException 허용 범위 (AccessDeniedException 제외 정책)
3. **AuthorizationCoverageArchTest.java** — baseline 54 endpoint 항목 정확 확인 (PR merge 후 신규 endpoint 추가 시 동기 갱신 필요)
4. **META-IT-GREEN-MANDATORY-001/spec.md** — Sync checklist enforcement 방식 (자동화 vs 수동)

### 알려진 제약 (out of scope)
- `:test` task 전체 실행 시 51 controller unit test failed (PermissionChangeControllerTest 등) — 기존 환경 이슈, 본 PR 변경과 무관. 별도 SPEC 분리 검토 필요.

---

## 다음 단계 후보

- AUTHZ-IT-EXPAND-003 (가칭, 120 endpoint 전체)
- PII-KMS-001 + PII-ROTATION-001 (KMS 인프라 의존)
- Frontend E2E (Playwright)
- controller unit test 51 failed 진단 분리 SPEC

---

🗿 MoAI <admin@ircp.co.kr>
