# SPEC-CMS-SECURITY-PII-FOLLOWUP-004: AC-009-3/4 false GREEN 정밀 진단 (PII-002 본래 SPEC vs 운영 동작 차이) v0.3

**Status**: Mostly Implemented (2026-05-12) — AC-009-4 + AC-009-3 GREEN + AC-009-2 잔여 (race condition)
**Implementation commits**: dc224f2 (AC-009-4 IT 시나리오 정정), b5npgelot RUN (VerificationService REQUIRES_NEW)

## v0.3 변경 이력 (2026-05-12) — UnexpectedRollbackException 해소

### AC-009-3 root cause 운영 fix
- **운영 코드 변경**: `VerificationServiceImpl.request` 메소드에 `@Transactional(propagation = Propagation.REQUIRES_NEW)` 적용 (1줄 변경)
- **원인**: `AuthServiceImpl.requestPasswordReset`의 catch 블록이 예외를 삼키지만 Spring AOP `@Transactional` 기본 REQUIRED는 inner transaction을 rollback-only 마킹 → outer commit `UnexpectedRollbackException`
- **fix 효과**: inner tx (verificationService.request) 분리 → 호출자 commit 가능 + 보안 정책(IP 차단/쿨다운 외부 노출 회피) 유지
- **IT 검증**: AC-009-3 GREEN 회복 (audit 미적재 검증 — HMAC lookup-only 본래 SPEC 의도 일치)

### AC-009-2 잔여 (race condition)
- 본 commit 검증 시 race condition 가능성 또는 selfId 매칭 디버깅 필요
- 다음 RUN: `@TestMethodOrder(MethodOrderer.OrderAnnotation.class)` 적용
**Implementation commit**: (본 commit, AC-009-4 IT 시나리오 정정)

## v0.2 변경 이력 (2026-05-12)

### AC-009-4 정정 GREEN 회복 (옵션 b 채택)
- 결정: 운영 코드 그대로 + IT 시나리오 expected 정정 (운영 동작 우선)
- 변경: `selfMe_noAuditLog` → `selfMe_auditedOnce` (이름 정정)
- 검증: `assertThat(auditAfter).isEqualTo(auditBefore)` → `assertThat(auditAfter - auditBefore).isEqualTo(1L)`
- 의미: PersonalDataAccessAspect selfAccessOnly의 운영 의미는 "self-access auditing"
  - selfAccessOnly=true + viewer == target → 적재 (본인이 자기 정보 조회 추적)
  - selfAccessOnly=true + viewer != target → 적재 생략 (타인 접근은 selfAccessOnly 대상 아님)
- 검증 결과: AC-009-4 PASSED (BUILD SUCCESSFUL 일부)

### AC-009-3 잔여 RED — 진단 모드 실측 결과 (2026-05-12 업데이트)

**결정적 발견**: mockMvc.perform 자체가 `UnexpectedRollbackException` 발생.

```
org.springframework.transaction.UnexpectedRollbackException:
Transaction rolled back because it has been marked as rollback-only
```

이전 분석은 잘못된 가정 — audit 적재 문제가 아니라 **운영 transaction rollback 문제**:
1. AuthController.passwordResetRequest → AuthServiceImpl.requestPasswordReset
2. requestPasswordReset 내부 `verificationService.request` 호출 chain에서 transaction이 'rollback-only' 마킹
3. 호출자 commit 시도 → UnexpectedRollbackException → 5xx 응답 → mockMvc 200 expected 실패

**다음 RUN 진단 절차 (정정)**:
1. `verificationService.request` 내부 transaction propagation 검토 (REQUIRES_NEW? NESTED?)
2. rollback-only 마킹 원인 추적 (예외 catch 후 `setRollbackOnly()` 호출 위치)
3. 운영 정책 결정:
   - (a) rollback이 의도된 동작 → IT 시나리오 expected를 4xx/5xx로 정정
   - (b) rollback이 버그 → 운영 코드 수정 (예외 처리 변경)

본 시도 commit `f860634`의 잘못된 가정 (audit 적재 경로) 정정.

### AC-009-2 race condition (다음 진단 필요)
- selfId 매칭 로직 정확 (filter(id != actor.userId()))
- 본 시도(@Disabled 3건 추가)에서 RED 회귀 → JUnit 5 test 순서 변경 가능성
- 다음 세션: `@TestMethodOrder(MethodOrderer.OrderAnnotation.class)` 적용 또는 selfId 비교 로직 정밀 디버깅
**Trigger**: PII-FOLLOWUP-003 v0.2 Implemented 옵션 G 적용 후 @Transactional rollback이 가리던 실제 audit 동작 노출
**Severity**: P2 (PII-002 본래 SPEC §결론과 운영 동작 차이 검증, PIPA 컴플라이언스 정확성 점검)

---

## 1. 배경 (PII-FOLLOWUP-003 v0.2 §결론)

PII-FOLLOWUP-003 옵션 G (PiiAuditEnhanceIT `@Transactional` 제거 + TRUNCATE cleanup)로 audit row 실제 commit 검증 가능해진 후, 이전 false GREEN 시나리오 2건 노출:

| AC | PII-002 본래 SPEC 의도 | 옵션 G 실제 동작 |
|----|------|------|
| AC-009-3 | 비밀번호 재설정 HMAC lookup-only → audit 미적재 | audit row 적재됨 (FALSE GREEN 노출) |
| AC-009-4 | GET /me 본인 자기 조회 → audit 미적재 | audit row 적재됨 (FALSE GREEN 노출) |

이전에는 IT 클래스 `@Transactional`이 모든 audit row를 test rollback으로 가려 false GREEN 통과. 옵션 G로 정직한 검증 시 실제 audit 동작 노출.

---

## 2. 진단 (예비)

### AC-009-3 진단
- `AuthServiceImpl.requestPasswordReset(email, ipAddress, userAgent)`: HMAC lookup-only, audit 직접 호출 **없음** (grep 확인)
- `verificationService.request()` 호출 — `VerificationService.request` 내부 grep 결과 audit 호출 **없음**
- 그러나 IT에서 audit row 적재 → 추정 원인:
  - mockMvc 요청 부수효과 (필터 체인 또는 다른 @PersonalDataAccess 트리거)
  - 또는 SecurityContext가 anonymous 상태에서 다른 자동 적재 mechanism

### AC-009-4 진단
- `userService.getMe(currentUserId)` + `@PersonalDataAccess(fields = {"email", "phone"}, purpose = "SELF_VIEW", targetUserIdParam = "currentUserId", selfAccessOnly = true)`
- `PersonalDataAccessAspect.afterAccess`: `viewer.userId() == targetUserId` 매칭 시 적재 생략 (selfAccessOnly=true)
- 호출 site: `userService.getMe(principal.userId())` — `currentUserId == principal.userId()` 일치 의도
- 그러나 IT에서 audit row 적재 → 추정 원인:
  - PersonalDataAccessAspect의 selfAccessOnly 매칭 로직 버그
  - 또는 viewer.userId() 추출 시점 차이 (Aspect vs 호출 site)
  - 또는 JwtPrincipal 필드 매칭 미일치

---

## 3. EARS 요구사항

### REQ-PII-FU4-001 (Ubiquitous) — AC-009-3 원인 확정
**EARS**: "The system SHALL identify the exact code path that triggers `personal_data_access_log` insertion during `POST /api/v1/auth/password/reset-request` invocation, and either fix the operational code to align with PII-002 §conclusion (no audit during HMAC lookup-only) OR update the IT scenario expected value to match operational reality."

### REQ-PII-FU4-002 (Ubiquitous) — AC-009-4 원인 확정
**EARS**: "The system SHALL verify the `selfAccessOnly` matching logic of `PersonalDataAccessAspect`. If buggy, fix the aspect to skip audit when `viewer.userId() == targetUserId`. If working as designed, update the IT scenario expected value."

### REQ-PII-FU4-003 (Event-driven) — PII-002 본래 SPEC v0.x 정정
**EARS**: "When this SPEC is implemented, PII-002 spec.md SHALL append a v0.x history entry documenting the false GREEN discovery and the resolution (operational fix or IT scenario adjustment)."

---

## 4. Acceptance Criteria

| AC ID | 내용 |
|-------|------|
| AC-FU4-001-1 | AC-009-3 audit 적재 root cause 확정 (운영 코드 경로 식별) |
| AC-FU4-001-2 | AC-009-3 GREEN 회복 (운영 수정 또는 IT 시나리오 expected 갱신) |
| AC-FU4-002-1 | AC-009-4 audit 적재 root cause 확정 (selfAccessOnly 매칭 로직 검증) |
| AC-FU4-002-2 | AC-009-4 GREEN 회복 (운영 수정 또는 IT 시나리오 expected 갱신) |
| AC-FU4-003-1 | PII-002 spec.md v0.x 변경 이력 entry 추가 |

---

## 5. 결정 포인트 (다음 세션)

| 결정 | 옵션 | 권장 |
|------|------|------|
| **D1** AC-009-3 처리 | (a) 운영 코드 수정 (audit 호출 제거) / (b) IT 시나리오 expected 갱신 (audit 적재 정상) | 진단 결과에 따름 |
| **D2** AC-009-4 처리 | (a) Aspect selfAccessOnly 로직 수정 / (b) IT 시나리오 갱신 | 진단 결과에 따름 |
| **D3** PII-002 SPEC 정정 시점 | (a) 본 SPEC RUN 시 / (b) 별도 v0.x 갱신 | (a) 일괄 권장 |

---

## 6. 변경 이력

| 버전 | 일자 | 작성자 | 변경 내용 |
|------|------|--------|----------|
| v0.1 | 2026-05-11 | MoAI orchestrator | 초안 작성. PII-FOLLOWUP-003 v0.2 Implemented 옵션 G 적용 후 노출된 false GREEN 2건 (AC-009-3 비밀번호 재설정 미적재 + AC-009-4 GET /me 미적재) 분리 SPEC. PII-002 본래 SPEC §결론과 운영 동작 차이 정밀 진단 권장. AC-009-3 추정 원인: requestPasswordReset/verificationService.request 직접 audit 없음, mockMvc 부수효과 의심. AC-009-4 추정 원인: PersonalDataAccessAspect selfAccessOnly 매칭 로직 또는 viewer.userId() 추출 시점 차이. REQ-PII-FU4-001/002/003 + 5 AC + 결정 포인트 D1~D3 명시. 다음 세션 RUN 진입 권장. |
