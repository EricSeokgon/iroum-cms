# SPEC-CMS-USER-APPROVAL-002 — 수용 기준 (acceptance)

Given-When-Then 시나리오. REQ당 3~4개, 총 27개.

---

## REQ-UA2-001a — 가입 인증 코드 발송

- **AC-UA2-001a-1**: Given 공개 사용자가 유효한 이메일을 입력했을 때, When `POST /api/v1/auth/verify/request`(channel=EMAIL, purpose=SIGNUP)를 호출하면, Then 요청 ID·만료 시각·쿨다운 초가 반환되고 인증 코드 이메일이 발송된다.
- **AC-UA2-001a-2**: Given 동일 IP가 단시간 다수 요청했을 때, When IP 차단 임계 초과 시 재요청하면, Then `VerificationIpBlockedException` 정책으로 차단된다.
- **AC-UA2-001a-3**: Given 쿨다운이 지난 정상 상황에서, When 재발송을 요청하면, Then 새 인증 코드가 발송되고 이전 코드는 무효화된다(기존 OTP 동작).

## REQ-UA2-001b — 가입 인증 코드 재발송 쿨다운

- **AC-UA2-001b-1**: Given 직전 발송 후 쿨다운 시간 내일 때, When 동일 대상이 재발송을 요청하면, Then `VerificationCooldownException` 정책에 따라 429가 반환된다.

## REQ-UA2-002 — 인증 코드 확인 및 가입 접근 제어

- **AC-UA2-002-1**: Given 발송된 인증 코드를 보유한 사용자가, When `POST /api/v1/auth/verify/confirm`에 올바른 코드를 제출하면, Then purpose=SIGNUP·5분 유효의 `verifiedToken`이 발급된다.
- **AC-UA2-002-2**: Given `REGISTRATION_EMAIL_VERIFY_REQUIRED=true`이고 `verifiedToken` 필드가 요청에 누락될 때, When `POST /api/v1/auth/register`를 호출하면, Then 400 Bad Request가 반환되고 사용자가 생성되지 않는다.
- **AC-UA2-002-3**: Given `REGISTRATION_EMAIL_VERIFY_REQUIRED=true`이고 `verifiedToken`이 있으나 만료(5분 초과)되었거나 purpose가 SIGNUP이 아니거나 미검증 상태일 때, When register에 제출하면, Then 403 Forbidden이 반환되고 사용자가 생성되지 않는다.
- **AC-UA2-002-4**: Given `REGISTRATION_EMAIL_VERIFY_REQUIRED=false`일 때, When `verifiedToken` 없이 register를 호출하면, Then 기존 가입 동작(설정에 따라 ACTIVE 또는 PENDING_APPROVAL)이 정상 수행된다(회귀 없음).

## REQ-UA2-003 — 승인 대기 리마인더 스케줄러

- **AC-UA2-003-1**: Given `REGISTRATION_APPROVAL_REMINDER_DAYS=3`이고 4일 전 가입한 `PENDING_APPROVAL` 사용자(`reminder_sent_at IS NULL`)가 있을 때, When 리마인더 잡이 실행되면, Then 해당 사용자에게 `USER_APPROVAL_REMINDER` 이메일이 발송되고 `reminder_sent_at`이 기록된다.
- **AC-UA2-003-2**: Given 이미 `reminder_sent_at`이 기록된 사용자가 있을 때, When 잡이 재실행되면, Then 동일 사용자에게 리마인더가 중복 발송되지 않는다(멱등).
- **AC-UA2-003-3**: Given 가입 후 2일(임계 3일 미만) 경과한 사용자일 때, When 잡이 실행되면, Then 리마인더가 발송되지 않는다.
- **AC-UA2-003-4**: Given `PENDING_APPROVAL`이 아닌(이미 승인/거절된) 사용자일 때, When 잡이 실행되면, Then 대상에서 제외된다.

## REQ-UA2-004 — 승인 대기 자동 만료/거절

- **AC-UA2-004-1**: Given `REGISTRATION_APPROVAL_MAX_WAIT_DAYS=14`이고 15일 경과한 `PENDING_APPROVAL` 사용자가 있을 때, When 자동거절 잡이 실행되면, Then 상태가 `INACTIVE`로 전환되고 `rejection_reason`(자동)·`approval_status_changed_at`이 기록되며 `USER_APPROVAL_AUTO_REJECTED` 이메일이 발송된다.
- **AC-UA2-004-2**: Given `REGISTRATION_APPROVAL_MAX_WAIT_DAYS=0`(비활성)일 때, When 잡이 실행되면, Then 어떤 사용자도 자동 거절되지 않는다.
- **AC-UA2-004-3**: Given 임계 미만(예: 10일/임계 14일) 경과 사용자일 때, When 잡이 실행되면, Then 자동 거절되지 않고 대기 상태가 유지된다.
- **AC-UA2-004-4**: Given 자동 거절 처리 시, When 상태가 전환되면, Then `approval_changed_by`는 NULL(시스템 처리)로 기록된다.

## REQ-UA2-005 — 관리자 일괄 승인 (기구현 검증)

- **AC-UA2-005-1**: Given `PENDING_APPROVAL` 사용자 3명일 때, When `POST /api/v1/users/approvals/bulk-approve`에 3 ID를 전달하면, Then 3명이 `ACTIVE`로 전환되고 `BulkOperationResult`에 성공 3건이 집계된다.
- **AC-UA2-005-2**: Given 일부 ID가 이미 처리되어 `PENDING_APPROVAL`이 아닐 때, When 일괄 승인하면, Then 대기 상태인 건만 승인되고 나머지는 실패(상태 불일치)로 집계된다(부분 처리, 전체 롤백 없음).
- **AC-UA2-005-3**: Given `SUPER_ADMIN`/`DEPT_ADMIN` 외 역할일 때, When 일괄 승인을 호출하면, Then 403이 반환된다.

## REQ-UA2-006 — 관리자 일괄 거절 + 사유 (기구현 검증)

- **AC-UA2-006-1**: Given `PENDING_APPROVAL` 사용자 다수와 공통 사유가 주어졌을 때, When `POST /api/v1/users/approvals/bulk-reject`를 호출하면, Then 대상이 `INACTIVE`로 전환되고 각자 `rejection_reason`이 기록되며 거절 이메일이 발송된다.
- **AC-UA2-006-2**: Given 거절 사유(`reason`)가 빈 문자열/누락일 때, When 일괄 거절을 호출하면, Then 400이 반환되고 상태 변경이 없다.
- **AC-UA2-006-3**: Given 일부 ID가 대기 상태가 아닐 때, When 일괄 거절하면, Then 대기 건만 거절되고 나머지는 실패로 집계된다.

## REQ-UA2-007 — 이메일 알림 (거절/자동거절/리마인더)

- **AC-UA2-007-1**: Given 수동 거절이 확정될 때, When 이메일이 발송되면, Then `USER_APPROVAL_REJECTED` 템플릿에 `rejectionReason`이 주입되어 발송된다.
- **AC-UA2-007-2**: Given 자동 거절이 확정될 때, When 이메일이 발송되면, Then `USER_APPROVAL_AUTO_REJECTED` 템플릿이 사유와 함께 발송된다.
- **AC-UA2-007-3**: Given 이메일 렌더링/발송이 실패할 때, When 상태 전환이 이미 커밋되었으면, Then 발송 실패만 로그로 남고 예외가 전파되지 않으며 상태는 유지된다.
- **AC-UA2-007-4**: Given 리마인더 발송 시, When 템플릿이 렌더링되면, Then `name`·`pendingDays` 변수가 정상 치환되어 발송된다.

## REQ-UA2-008 — 프론트엔드 일괄/표시 UI (기구현 보강)

- **AC-UA2-008-1**: Given 대기열 화면이 표시될 때, When 행을 다중 선택하면, Then 기존 일괄 승인/거절 버튼과 거절 사유 다이얼로그가 동작한다(회귀).
- **AC-UA2-008-2**: Given 일괄 거절 결과에 부분 실패가 있을 때, When 응답을 수신하면, Then 성공/실패 건수와 실패 사유 상세가 화면에 표시된다.
- **AC-UA2-008-3**: Given 대기열 행이 렌더링될 때, When 화면을 보면, Then 각 사용자의 대기 경과일 컬럼과 이메일 인증 완료 여부 컬럼(`email_verified_at IS NOT NULL` → 인증 완료 표시)이 표시된다.

---

## Definition of Done

- [ ] 8개 REQ 전부 위 AC로 검증(총 27 AC) GREEN.
- [ ] 마이그레이션(잠정 V65) Flyway 적용 PASS, idempotent 재실행 PASS.
- [ ] register 인증 게이트 OFF 시 기존 가입 회귀 없음.
- [ ] 리마인더/자동거절 잡 멱등성·설정 0 비활성 검증.
- [ ] 일괄 승인/거절 기구현 회귀 GREEN.
- [ ] 이메일 실패 graceful(상태 커밋 유지) 검증.
- [ ] TRUST 5 게이트 통과, 백엔드/프론트 테스트 GREEN.

## Quality Gate

- 커버리지: 신규 코드 85%+ (스케줄러 서비스 메서드·register 분기 단위 테스트 포함).
- 통합 테스트: `UserApprovalIT` 계열에 리마인더/자동거절/인증 게이트 IT 추가.
- 보안: 인증 코드 정책 완화 없음, 일괄 API `@PreAuthorize` 검증.
