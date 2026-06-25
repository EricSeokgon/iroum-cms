---
id: SPEC-CMS-USER-APPROVAL-002
version: 0.1.3
status: Completed
created_at: 2026-06-25
updated_at: 2026-06-25
priority: medium
labels: [cms, user-approval, email-verification, reminder, bulk-action]
---

# SPEC-CMS-USER-APPROVAL-002 — 사용자 가입 승인 흐름 고도화

## HISTORY

- 2026-06-25 (v0.1.3): sync: status Implemented → Completed. CHANGELOG.md 및 product.md 업데이트.
- 2026-06-25 (v0.1.2): TDD RUN 완료(status Implemented). V59 마이그레이션(reminder_sent_at/email_verified_at + 설정 3종 + 이메일 템플릿 2종), register verifiedToken 게이트(400/403), ApprovalReminderJob(@Scheduled 02:00 리마인더+자동거절), 프론트 이메일 인증 컬럼. 백엔드 IT 15(RegisterEmailVerify 5 + ApprovalScheduler 8 + ApprovalSummaryVerify 2) + 컨트롤러 11 + 프론트 6 GREEN. MigrationOrderIT 57→58.
- 2026-06-25 (v0.1.1): plan-auditor REJECT/WARN 수정. REJECT-1: USER_APPROVAL_VERIFY_CODE 템플릿 제거(VerificationService OTP 채널로 대체), Section 1.1 결정 명시. REJECT-2: email_verified_at TIMESTAMPTZ 컬럼 추가. WARN 전체 반영(REQ 분리, NFR 추가, Exclusions 보강, HTTP 코드 확정).
- 2026-06-25 (v0.1.0): 최초 작성 (Draft). SPEC-CMS-USER-APPROVAL-001(게이트형 가입 승인)을 고도화하여 ① 가입 이메일 인증 코드, ② 승인 대기 리마인더/자동 만료 스케줄러, ③ 일괄 승인/거절 보강을 정의. 신규 인프라 구축이 아니라 기존 OTP(SPEC-CMS-002 VerificationService)·이메일 템플릿·`@Scheduled` 잡·승인 도메인 확장 원칙.

---

## 1. 개요 (Overview)

SPEC-CMS-USER-APPROVAL-001은 설정으로 켜고 끄는 게이트형 가입 승인 워크플로(`PENDING_APPROVAL` 상태, 대기열 조회, 단건/일괄 승인·거절, 거절 이메일)를 완성했다. 본 SPEC은 그 흐름을 운영 관점에서 고도화한다.

### 1.1 핵심 설계 결정 (Key Design Decisions)

본 SPEC은 **기존 인프라 확장**을 원칙으로 한다(신규 구축 금지).

1. **[HARD] 이메일 인증은 기존 OTP 시스템을 재사용한다.** `VerificationService.request/confirm`(SPEC-CMS-002, `V8__verification_schema.sql`)과 이미 존재하는 `VerificationPurpose.SIGNUP`을 사용한다. 신규 인증 코드 테이블/서비스를 만들지 않는다. 가입 요청(`POST /api/v1/auth/register`)은 OTP 검증으로 발급된 `verifiedToken`을 필수 입력으로 받아 `validateVerifiedToken(token, SIGNUP)`으로 검증한다(가입 전 인증). 이는 `confirmPasswordReset`(AuthServiceImpl)에서 검증된 패턴이다.
2. **[HARD] 리마인더/자동 만료는 기존 `@Scheduled` 잡 패턴을 따른다.** `QnaNotificationRetryJob`·`PostPublishJob` 스타일의 `@Component`+`@Scheduled` 잡을 신규 추가한다. Spring Batch를 도입하지 않는다. `@EnableScheduling`은 `AsyncConfig`에 이미 존재한다.
3. **[HARD] 모든 임계값 설정은 `system_setting`(V14) key-value로 저장한다.** 신규 config 테이블 금지. 키 `REGISTRATION_APPROVAL_REMINDER_DAYS`(INT), `REGISTRATION_APPROVAL_MAX_WAIT_DAYS`(INT). 조회는 기존 `SystemSettingService.get(key)` 재사용.
4. **[HARD] 신규 이메일 템플릿은 Flyway 시드한다.** `USER_APPROVAL_REMINDER`(리마인더), `USER_APPROVAL_AUTO_REJECTED`(자동 거절) 2종. `email_template`(V55) `ON CONFLICT (code, language) DO NOTHING` 패턴 계승. 발송은 `EmailTemplateResolver.resolveAndRender()` 단일 진입점, 실패 graceful. **이메일 인증 코드 발송은 기존 VerificationService OTP 채널을 재사용한다. 신규 이메일 템플릿(`USER_APPROVAL_VERIFY_CODE`) 추가 없음.**
5. **[HARD] 대기 메타데이터는 `users` 테이블 additive 컬럼으로 저장한다.** `reminder_sent_at TIMESTAMPTZ`(nullable). 자동 거절은 APPROVAL-001의 기존 `rejection_reason`/`approval_status_changed_at`을 재사용(처리자=시스템 = `approval_changed_by NULL`).
6. **[HARD] 일괄 승인/거절(Area 3)은 SPEC-CMS-USER-APPROVAL-001에 이미 구현되어 있다.** 본 SPEC은 이를 **재구현하지 않으며**, 기구현을 검증하고 경량 보강(부분 실패 상세, 인증 완료 여부 표시)만 한다.

### 1.2 용어 (Glossary)

- **가입 인증 코드(Sign-up Verification Code)**: 가입 전 이메일 소유 확인용 OTP. 기존 `VerificationPurpose.SIGNUP`.
- **verifiedToken**: OTP 검증 성공 시 발급되는 단기(5분) 토큰. 가입 요청 시 제출.
- **리마인더(Reminder)**: `PENDING_APPROVAL` 상태가 N일 경과한 사용자에게 보내는 안내(관리자/사용자 대상은 NFR에서 규정).
- **자동 만료/거절(Auto-Rejection)**: `PENDING_APPROVAL`이 max_wait_days를 초과하면 시스템이 `INACTIVE`로 전환(사유=자동).

---

## 2. 의존성 (Dependencies)

| SPEC | 관계 | 사유 |
|------|------|------|
| **SPEC-CMS-USER-APPROVAL-001** | [HARD] 필수 선행 | `PENDING_APPROVAL` 상태, `domain.approval` 도메인, V58 마이그레이션, 대기열·승인·거절·일괄 전부 본 SPEC 전제. **main 미머지 상태 — 머지 후에만 run 가능.** |
| SPEC-CMS-002 (auth) | [HARD] 필수 선행 | `VerificationService`(OTP), `VerificationPurpose.SIGNUP`, `verifiedToken`, `registerPublicUser`. (main 존재) |
| SPEC-CMS-EMAIL-TEMPLATE-001 | 필수 선행 | `EmailTemplateResolver`, `email_template` 시드 패턴. (APPROVAL-001과 함께 머지 대상) |
| SPEC-CMS-RBAC-001 | 필수 선행 | `permissions`/`role_permissions` 시드, `@PreAuthorize`. |
| SPEC-CMS-NOTIFICATION-CENTER-001 | 선택 | 리마인더 발생 시 `admin_notification` 적재 가능(graceful no-op). |

---

## 3. 범위 (Scope) 및 비범위 (Exclusions)

### 3.1 In Scope

- 가입 요청 시 이메일 인증 코드(OTP) 요구 및 `verifiedToken` 검증, 재발송 연동.
- 미인증 가입 차단(403) — OTP/`verifiedToken` 없는 register 거부.
- 승인 대기 N일 경과 리마인더 `@Scheduled` 잡 + 이메일.
- max_wait_days 초과 자동 거절 `@Scheduled` 잡 + 이메일.
- 리마인더/자동만료 임계값 `system_setting` 키 2종.
- 대기 메타 컬럼 `reminder_sent_at` 1종(additive) + 이메일 템플릿 3종 시드.
- 일괄 승인/거절(기구현) 검증 + 경량 프론트 보강.

## Exclusions (What NOT to Build)

- **인증 코드 인프라 신규 구축** — 기존 `VerificationService`/`V8` OTP를 재사용. 신규 코드 테이블·발송 채널·만료 로직 작성 금지.
- **이메일 인증 코드 전용 이메일 템플릿(`USER_APPROVAL_VERIFY_CODE`)** — `VerificationService` OTP 채널로 대체. 신규 템플릿 시드 없음.
- **SMS/휴대폰 인증 코드** — 본 SPEC은 이메일 채널만. SMS는 별도 SPEC.
- **일괄 승인/거절 API·UI 재구현** — SPEC-CMS-USER-APPROVAL-001에 이미 존재(`/bulk-approve`, `/bulk-reject`, ApprovalQueueView 일괄 UI, 사유 다이얼로그). 본 SPEC은 검증·경량 보강만.
- **가입 후 인증(별도 `PENDING_EMAIL` 상태)** — 가입 전 인증(register 진입 시 verifiedToken 필수)을 채택. 신규 상태 추가 금지.
- **리마인더/만료 잡의 분산 락·다중 노드 스케줄 조율** — 단일 노드 `@Scheduled` 가정. 분산 스케줄링은 제외.
- **자동 거절된 사용자의 자동 재가입/유예 복원** — 자동 거절은 `INACTIVE` 전환까지만. 복원은 수동(기존 사용자 관리).
- **관리자 대상 리마인더 이메일 발송** — 리마인더는 대기 중인 사용자에게만 발송하며 관리자 수신은 제외.
- **인증 코드 발송 통계/대시보드** — 발송 통계는 SPEC-CMS-NOTIFICATION-STAT-001 범위.
- **register API의 인증 외 정책 변경**(비밀번호 정책, 약관 동의 등) — 본 SPEC 범위 아님.

> **자동 거절 시 audit_log 기록 방침**: action=`UPDATE`, entity_type=`'User'`, entity_id=사용자ID 로 기록. `approval_changed_by`는 NULL(시스템 처리).

---

## 4. 기능 요구사항 (Functional Requirements — EARS)

### REQ-UA2-001a — 가입 인증 코드 발송 (Event-Driven)

WHEN 공개 사용자가 이메일 인증 코드 발송을 요청하면(`POST /api/v1/auth/verify/request`, `channel=EMAIL`, `purpose=SIGNUP`), the system shall 기존 `VerificationService.request()`를 통해 인증 코드를 생성·발송하고 요청 ID·만료 시각·쿨다운을 반환한다.

### REQ-UA2-001b — 가입 인증 코드 재발송 쿨다운 (Event-Driven / Unwanted Behavior)

IF 동일 사용자가 쿨다운 시간 내 재발송을 요청하면, THEN the system shall 기존 `VerificationService`의 쿨다운/IP 차단 정책에 따라 429 오류를 반환한다.

### REQ-UA2-002 — 인증 코드 확인 및 가입 접근 제어 (Event-Driven / Unwanted Behavior)

WHEN 사용자가 인증 코드를 제출하면(`POST /api/v1/auth/verify/confirm`), the system shall 기존 `VerificationService.confirm()`으로 검증하여 `verifiedToken`을 발급한다.
IF 가입 요청(`POST /api/v1/auth/register`)에 `verifiedToken` 필드 자체가 누락되면, THEN the system shall 400 Bad Request를 반환하며 사용자를 생성하지 않는다.
IF 가입 요청에 `verifiedToken`이 있으나 만료되었거나 purpose가 SIGNUP이 아니거나 미검증 상태이면, THEN the system shall 403 Forbidden을 반환하며 사용자를 생성하지 않는다.

### REQ-UA2-003 — 승인 대기 리마인더 스케줄러 (State-Driven)

WHILE `PENDING_APPROVAL` 상태인 사용자가 `REGISTRATION_APPROVAL_REMINDER_DAYS`(설정, INT)일을 경과하고 아직 리마인더가 발송되지 않았으면(`reminder_sent_at IS NULL`), the system shall 신규 `@Scheduled` 잡 실행 시 해당 사용자에게 `USER_APPROVAL_REMINDER` 이메일을 발송하고 `reminder_sent_at`을 기록한다.

### REQ-UA2-004 — 승인 대기 자동 만료/거절 (State-Driven / Unwanted Behavior)

WHILE `PENDING_APPROVAL` 상태인 사용자가 `REGISTRATION_APPROVAL_MAX_WAIT_DAYS`(설정, INT)일을 초과하면, the system shall 신규 `@Scheduled` 잡 실행 시 해당 사용자를 `INACTIVE`로 전환하고 `rejection_reason`(자동 거절 사유)·`approval_status_changed_at`을 기록하며 `USER_APPROVAL_AUTO_REJECTED` 이메일을 발송한다.
IF `REGISTRATION_APPROVAL_MAX_WAIT_DAYS` 설정이 없거나 0 이하이면, THEN the system shall 자동 거절을 수행하지 않는다(기능 비활성, 회귀 방지).

### REQ-UA2-005 — 관리자 일괄 승인 (Ubiquitous / 기구현 검증)

The system shall `POST /api/v1/users/approvals/bulk-approve`(SPEC-CMS-USER-APPROVAL-001 기구현)로 다수 `PENDING_APPROVAL` 사용자를 일괄 승인하고, 건별 성공/실패(상태 불일치 등) 결과(`BulkOperationResult`)를 반환한다. 본 SPEC은 이 동작을 재구현하지 않고 회귀 검증한다.

> (이 요구사항은 SPEC-CMS-USER-APPROVAL-001에서 기구현된 기능에 대한 회귀 검증 기준이다. 해당 기능의 재구현은 Exclusions 참조.)

### REQ-UA2-006 — 관리자 일괄 거절 + 사유 (Ubiquitous / Unwanted Behavior / 기구현 검증)

The system shall `POST /api/v1/users/approvals/bulk-reject`(기구현)로 다수 `PENDING_APPROVAL` 사용자와 공통 거절 사유를 받아 일괄 거절하고 건별 결과를 반환한다.
IF 거절 사유(`reason`)가 비어 있으면, THEN the system shall 요청을 거부하고 400을 반환한다(기구현 동작 유지·검증).

> (이 요구사항은 SPEC-CMS-USER-APPROVAL-001에서 기구현된 기능에 대한 회귀 검증 기준이다. 해당 기능의 재구현은 Exclusions 참조.)

### REQ-UA2-007 — 거절/자동거절/리마인더 이메일 알림 (Event-Driven / Unwanted Behavior)

WHEN 수동 거절, 자동 거절, 또는 리마인더 각 이벤트가 발생하면, the system shall 해당 사용자 이메일로 이벤트에 맞는 알림 이메일을 발송한다. 각 이벤트별 템플릿: 수동 거절 → `USER_APPROVAL_REJECTED`(기구현), 자동 거절 → `USER_APPROVAL_AUTO_REJECTED`, 리마인더 → `USER_APPROVAL_REMINDER`. 모두 `EmailTemplateResolver.resolveAndRender()` 경유. 거절 계열 템플릿에는 거절 사유(`rejectionReason`)를 주입한다.
IF 이메일 렌더링/발송이 실패하면, THEN the system shall 발송 실패 시 로그만 기록하고 주 작업 트랜잭션에는 영향을 주지 않으며 예외를 전파하지 않는다(graceful fallback).

### REQ-UA2-008 — 프론트엔드 일괄 선택/승인/거절 UI (Optional / 기구현 보강)

WHERE 관리자 승인 대기열 화면(`ApprovalQueueView.vue`, 기구현)이 표시되면, the system shall 기존 다중선택 체크박스·일괄 승인/거절 버튼·거절 사유 다이얼로그를 유지하고, 본 SPEC에서 ① 일괄 거절의 부분 실패(상태 불일치 건) 상세 표시와 ② 인증 완료/대기 경과일 표시 컬럼을 경량 추가한다.

---

## 5. 비기능 요구사항 (Non-Functional Requirements)

### 5.1 성능
- **NFR-UA2-P1**: 리마인더/자동 거절 스케줄러 기본 실행 주기: 매일 새벽 2시(`0 0 2 * * ?` cron). 운영 환경에서 `system_setting.APPROVAL_SCHEDULER_CRON` 키로 재정의 가능.
- **NFR-UA2-P2**: 리마인더/자동거절 잡의 대기열 조회는 `idx_users_status`(부분 인덱스) + `status`·경과일 조건을 활용하여 단일 쿼리로 후보를 선별한다.
- **NFR-UA2-P3**: 이메일 발송은 트랜잭션 커밋 후(after-commit) 또는 `@Async`로 처리하여 잡/요청 지연을 방지한다(APPROVAL-001 패턴 계승).

### 5.2 보안
- **NFR-UA2-S1**: 가입 인증 코드는 기존 OTP의 만료·쿨다운·시도횟수·IP 차단 정책을 그대로 적용한다(신규 완화 금지).
- **NFR-UA2-S2**: `verifiedToken`은 purpose=SIGNUP·5분 유효를 강제하며, 재사용/목적 불일치 토큰은 거부한다.
- **NFR-UA2-S3**: 리마인더/자동거절 잡과 일괄 API는 시스템/관리자 경로로만 트리거되며, 사용자 입력으로 직접 실행할 수 없다. 일괄 API는 `@PreAuthorize("hasAnyRole('SUPER_ADMIN','DEPT_ADMIN')")`.
- **NFR-UA2-S4**: 거절/자동거절 사유는 이메일로 전달되므로 템플릿 렌더러가 변수값을 이스케이프한다.

### 5.3 호환성/회귀 방지
- **NFR-UA2-C1**: 가입 인증 요구는 게이트와 독립적이되, 기존 가입 동작을 깨지 않도록 인증 요구 활성 여부 자체를 `system_setting`(`REGISTRATION_EMAIL_VERIFY_REQUIRED`, BOOL, 기본 운영 정책에 따름)로 제어 가능해야 한다.
- **NFR-UA2-C2**: 리마인더/자동거절은 설정 미지정 또는 0 이하 시 비활성(기존 무동작 유지).
- **NFR-UA2-C3**: 마이그레이션은 단일 Flyway 파일에 통합한다(additive 컬럼 + 설정 시드 + 이메일 템플릿 3종 시드). **마이그레이션 번호는 APPROVAL-001 머지 후 최신 번호의 다음 값**으로 run 직전 확정한다(잠정 V61).

---

## 6. 데이터 모델 변경 (Data Model)

### 6.1 `users` 테이블 (additive)

| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| `reminder_sent_at` | TIMESTAMPTZ | nullable | 승인 대기 리마인더 발송 시각(미발송=NULL) |
| `email_verified_at` | TIMESTAMPTZ | DEFAULT NULL | 이메일 인증 완료 시각(NULL=미인증). register 요청 성공 AND verifiedToken 검증 성공 시 기록. |

(자동 거절은 APPROVAL-001의 `rejection_reason`/`approval_status_changed_at`/`approval_changed_by` 재사용. 시스템 처리 시 `approval_changed_by=NULL`.)

### 6.2 `system_setting` 시드 (idempotent)

| key | value | value_type | 설명 |
|-----|-------|-----------|------|
| `REGISTRATION_APPROVAL_REMINDER_DAYS` | `3` | INT | 대기 N일 경과 시 리마인더 |
| `REGISTRATION_APPROVAL_MAX_WAIT_DAYS` | `0` | INT | 초과 시 자동 거절(0=비활성) |
| `REGISTRATION_EMAIL_VERIFY_REQUIRED` | `false` | BOOL | 가입 시 이메일 인증 코드 필수 여부 |

(`ON CONFLICT (key) DO NOTHING`)

### 6.3 `email_template` 시드 (ko/en, `ON CONFLICT (code, language) DO NOTHING`)

- `USER_APPROVAL_REMINDER` — 변수 `name`, `pendingDays`.
- `USER_APPROVAL_AUTO_REJECTED` — 변수 `name`, `rejectionReason`.

(이메일 인증 코드 발송은 기존 VerificationService OTP 채널이 자체 처리하므로 `USER_APPROVAL_VERIFY_CODE` 템플릿은 추가하지 않는다. Section 1.1 [4] 참조.)

---

## 7. API 설계 (API Surface)

| 메서드 | 경로 | 상태 | 비고 |
|--------|------|------|------|
| POST | `/api/v1/auth/verify/request` | 기구현(SPEC-CMS-002) | SIGNUP 목적으로 호출 |
| POST | `/api/v1/auth/verify/confirm` | 기구현 | verifiedToken 발급 |
| POST | `/api/v1/auth/register` | 수정 | `verifiedToken` 필수화(설정 ON 시) |
| GET/POST | `/api/v1/users/approvals*` | 기구현(APPROVAL-001) | 일괄 포함, 재구현 없음 |
| (없음) | 리마인더/자동거절 | 신규 `@Scheduled` 잡 | API 아님, 내부 트리거 |

---

## 8. 수용 기준 요약 (Acceptance Criteria Summary)

각 REQ의 상세 Given-When-Then 시나리오는 `acceptance.md`에 정의한다(REQ당 3~4개, 총 24개 이상). 핵심:

- 인증 코드 발송/확인 → `verifiedToken` 발급, 쿨다운/IP 정책 유지.
- `verifiedToken` 없는 register → 403/400, 사용자 미생성.
- 대기 N일 경과 + `reminder_sent_at IS NULL` → 잡 실행 시 리마인더 1회 발송 + 컬럼 기록(재실행 시 중복 미발송).
- max_wait_days 초과 → `INACTIVE` 전환 + 자동거절 이메일. 설정 0 → 무동작.
- 일괄 승인/거절(기구현) 회귀 통과, 빈 사유 거절 → 400.
- 이메일 실패 → 상태 커밋, 발송만 실패 로그.

---

## 9. 구현 작업 (Implementation Tasks, 우선순위)

| ID | 작업 | 우선순위 |
|----|------|---------|
| T0 | 마이그레이션(잠정 V61): `reminder_sent_at` 컬럼 + `email_verified_at` 컬럼 + 설정 3종 + 이메일 템플릿 2종 시드(`USER_APPROVAL_REMINDER`, `USER_APPROVAL_AUTO_REJECTED`) | High |
| T1 | register 흐름에 `verifiedToken`(SIGNUP) 필수화 분기(설정 게이트) | High |
| T2 | 리마인더 `@Scheduled` 잡 + 대기열 쿼리 + 이메일 | High |
| T3 | 자동 거절 `@Scheduled` 잡 + 상태 전환 + 이메일 | High |
| T4 | 일괄 승인/거절 기구현 회귀 검증(테스트 보강) | Medium |
| T5 | 프론트 `ApprovalQueueView` 경량 보강(부분실패·경과일/인증 표시) | Medium |
| T6 | acceptance.md Given-When-Then 작성 | High |
