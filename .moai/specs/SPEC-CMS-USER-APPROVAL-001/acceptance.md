# SPEC-CMS-USER-APPROVAL-001 — 수용 기준 (Acceptance Criteria)

각 요구사항별 Given-When-Then 시나리오. 최소 2개/요구사항.

---

## 3.1 가입 게이트 (Registration Gate)

### REQ-UA-001 — 게이트 ON 시 PENDING_APPROVAL + JWT 미발급

- **AC-UA-001-1**
  - Given `REGISTRATION_APPROVAL_REQUIRED=true` 이고 미가입 이메일로
  - When `POST /api/v1/auth/register` 를 호출하면
  - Then 사용자가 `status=PENDING_APPROVAL` 로 생성되고, 응답은 202 Accepted + 안내 메시지이며 JWT 쿠키/바디가 없다.
- **AC-UA-001-2**
  - Given 게이트가 ON 이고 가입 요청이 접수되면
  - When 서비스가 사용자를 INSERT 하면
  - Then `MEMBER` 역할이 부여되지 않고 access/refresh 토큰이 발급되지 않는다.

### REQ-UA-002 — 게이트 OFF 시 기존 즉시 활성 동작

- **AC-UA-002-1**
  - Given `REGISTRATION_APPROVAL_REQUIRED=false`
  - When 공개 가입을 요청하면
  - Then 사용자가 `status=ACTIVE` 로 생성되고 `MEMBER` 역할 부여 + JWT(201 Created + Set-Cookie)가 발급된다.
- **AC-UA-002-2**
  - Given 게이트 OFF
  - When 가입 응답을 받으면
  - Then `RegisterResult.Approved` 로 LoginResponse(accessToken)가 포함된다.

### REQ-UA-003 — 설정 미존재 시 게이트 OFF 간주(회귀 방지)

- **AC-UA-003-1**
  - Given `system_setting` 에 `REGISTRATION_APPROVAL_REQUIRED` 키가 없을 때
  - When 가입을 요청하면
  - Then 게이트를 `false` 로 간주하여 ACTIVE 즉시 활성 가입이 진행된다.
- **AC-UA-003-2**
  - Given 설정 조회가 예외(NoSuchElementException)를 던질 때
  - When 가입 게이트를 평가하면
  - Then 예외를 흡수하고 OFF(기존 동작)로 처리한다.

### REQ-UA-004 — PENDING_APPROVAL 로그인 차단

- **AC-UA-004-1**
  - Given `status=PENDING_APPROVAL` 사용자가
  - When `POST /api/v1/auth/login` 을 시도하면
  - Then 비밀번호 검증 이전에 거부되고 403(UserPendingApprovalException)을 반환한다.
- **AC-UA-004-2**
  - Given 승인 대기 로그인 시도
  - When 로그인이 거부되면
  - Then `login_history` 에 `failureReason=PENDING_APPROVAL` 로 기록된다.

---

## 3.2 설정 관리 (Gate Configuration)

### REQ-UA-005 — 기존 SystemSettingController 재사용

- **AC-UA-005-1**
  - Given 관리자가 게이트 값을 변경할 때
  - When `PUT /api/v1/system/settings` (기존 경로)를 호출하면
  - Then 신규 설정 API 없이 기존 경로로 처리된다.
- **AC-UA-005-2**
  - Given 게이트 설정 조회 요청
  - When `GET /api/v1/system/settings` 를 호출하면
  - Then `REGISTRATION_APPROVAL_REQUIRED` 값이 포함되어 반환된다.

### REQ-UA-006 — BOOL value_type 직렬화

- **AC-UA-006-1**
  - Given V58 시드가 적용되면
  - When `system_setting` 을 조회하면
  - Then `value_type=BOOL`, `value='false'` 로 저장되어 있다.
- **AC-UA-006-2**
  - Given 값이 `'true'`/`'false'` 문자열일 때
  - When 게이트를 평가하면
  - Then `Boolean.parseBoolean` 으로 해석된다.

---

## 3.3 승인 대기열 조회 (Approval Queue)

### REQ-UA-007 — 대기열 목록 페이지네이션

- **AC-UA-007-1**
  - Given `PENDING_APPROVAL` 사용자가 다수 존재할 때
  - When `GET /api/v1/users/approvals` 를 호출하면
  - Then 대기 사용자만 가입일시 오름차순으로 페이지네이션되어 반환된다.
- **AC-UA-007-2**
  - Given ACTIVE/INACTIVE 사용자가 섞여 있을 때
  - When 대기열을 조회하면
  - Then `PENDING_APPROVAL` 상태만 포함되고 그 외는 제외된다.

### REQ-UA-008 — 검색 필터

- **AC-UA-008-1**
  - Given 이름/이메일 키워드가 주어질 때
  - When `GET /api/v1/users/approvals?keyword=홍길동` 을 호출하면
  - Then 이름 또는 아이디(=이메일)에 키워드가 포함된 대기자만 반환된다.
- **AC-UA-008-2**
  - Given keyword 가 비어 있을 때
  - When 대기열을 조회하면
  - Then 전체 대기자가 반환된다.

### REQ-UA-009 — 대기 사용자 상세

- **AC-UA-009-1**
  - Given 대기 사용자 ID 가 주어질 때
  - When `GET /api/v1/users/approvals/{id}` 를 호출하면
  - Then 가입일시/이름/이메일/조직 상세가 반환된다.
- **AC-UA-009-2**
  - Given 대기 상태가 아닌 사용자 ID 일 때
  - When 상세를 조회하면
  - Then 409(UserNotPendingApprovalException)를 반환한다.

---

## 3.4 단건 승인/거절

### REQ-UA-010 — 단건 승인

- **AC-UA-010-1**
  - Given `PENDING_APPROVAL` 사용자가
  - When `POST /api/v1/users/approvals/{id}/approve` 를 호출하면
  - Then `status=ACTIVE` 전환 + `MEMBER` 역할 부여 + 처리자/시각 기록 + 확인 이메일 발송이 일어난다.
- **AC-UA-010-2**
  - Given 승인 대상이 이미 `MEMBER` 역할을 가질 때
  - When 승인하면
  - Then 역할은 중복 부여되지 않는다.

### REQ-UA-011 — 단건 거절

- **AC-UA-011-1**
  - Given `PENDING_APPROVAL` 사용자 + 거절 사유가
  - When `POST /api/v1/users/approvals/{id}/reject` 를 호출하면
  - Then `status=INACTIVE` 전환 + `rejection_reason` 저장 + 거절 이메일(사유 포함) 발송이 일어난다.
- **AC-UA-011-2**
  - Given 거절 처리가 완료되면
  - When `users` 행을 조회하면
  - Then `approval_changed_by`/`approval_status_changed_at` 가 영속화되어 있다.

### REQ-UA-012 — 거절 사유 필수

- **AC-UA-012-1**
  - Given 거절 요청 본문에 `reason` 이 비어 있을 때
  - When reject 를 호출하면
  - Then 400 Bad Request 를 반환한다.
- **AC-UA-012-2**
  - Given 일괄 거절에 공통 사유가 비어 있을 때
  - When bulk-reject 를 호출하면
  - Then 400 Bad Request 를 반환한다.

### REQ-UA-013 — 비대기 상태 처리 거부(409)

- **AC-UA-013-1**
  - Given 이미 승인된(ACTIVE) 사용자에 대해
  - When 다시 approve 를 호출하면
  - Then 409 Conflict 를 반환한다.
- **AC-UA-013-2**
  - Given 비대기 사용자에 대해
  - When reject 를 호출하면
  - Then 409 Conflict 를 반환하고 상태가 변경되지 않는다.

---

## 3.5 일괄 승인/거절

### REQ-UA-014 — 일괄 승인

- **AC-UA-014-1**
  - Given 대기 + 비대기 사용자 ID 가 섞인 목록이 주어질 때
  - When `POST /api/v1/users/approvals/bulk-approve` 를 호출하면
  - Then 대기 상태만 승인되고 건별 성공/실패가 집계되어 반환된다.
- **AC-UA-014-2**
  - Given 일괄 승인 결과가 반환되면
  - When 결과를 확인하면
  - Then `successCount`/`failureCount`/`failures[]` 가 정확히 채워진다.

### REQ-UA-015 — 일괄 거절

- **AC-UA-015-1**
  - Given 다수 ID + 공통 거절 사유가 주어질 때
  - When bulk-reject 를 호출하면
  - Then 대기 상태 대상만 공통 사유로 거절되고 건별 결과가 반환된다.
- **AC-UA-015-2**
  - Given 공통 사유가 비어 있을 때
  - When bulk-reject 를 호출하면
  - Then 400 Bad Request 를 반환한다.

### REQ-UA-016 — 건별 무롤백 집계

- **AC-UA-016-1**
  - Given 일괄 처리 중 일부가 409 로 실패할 때
  - When 처리가 끝나면
  - Then 성공 건은 커밋되고 실패 건만 `failures` 에 집계된다(전체 롤백 없음).
- **AC-UA-016-2**
  - Given 모든 건이 성공할 때
  - When 처리가 끝나면
  - Then `failureCount=0` 이다.

---

## 3.6 이메일 알림

### REQ-UA-017 — 승인 확인 이메일

- **AC-UA-017-1**
  - Given 승인이 확정되면
  - When 트랜잭션이 커밋되면
  - Then `USER_APPROVAL_CONFIRMED` 템플릿이 `EmailTemplateResolver.resolveAndRender` 로 렌더링되어 발송된다.
- **AC-UA-017-2**
  - Given 템플릿이 미존재할 때
  - When 발송하면
  - Then 하드코딩 fallback 본문으로 발송된다.

### REQ-UA-018 — 거절 이메일(사유 포함)

- **AC-UA-018-1**
  - Given 거절이 확정되면
  - When 이메일을 발송하면
  - Then `USER_APPROVAL_REJECTED` 템플릿에 `rejectionReason` 변수가 주입되어 발송된다.
- **AC-UA-018-2**
  - Given 거절 사유가 본문에 포함되면
  - When 사용자가 이메일을 확인하면
  - Then 거절 사유 텍스트가 표시된다.

### REQ-UA-019 — 발송 실패 graceful fallback

- **AC-UA-019-1**
  - Given 이메일 발송 또는 템플릿 렌더링이 실패할 때
  - When 승인/거절을 처리하면
  - Then 상태 전환은 커밋된 채 발송 실패는 로그만 남기고 예외를 전파하지 않는다.
- **AC-UA-019-2**
  - Given 발송이 트랜잭션 커밋 후(afterCommit)에 수행되면
  - When 트랜잭션이 롤백되면
  - Then 이메일은 발송되지 않는다.

---

## 3.7 권한/감사

### REQ-UA-020 — RBAC 인가

- **AC-UA-020-1**
  - Given `SUPER_ADMIN` 또는 `DEPT_ADMIN` 역할 사용자가
  - When 승인 API 를 호출하면
  - Then 200 으로 정상 처리된다.
- **AC-UA-020-2**
  - Given 권한이 없는 일반 사용자(USER)가
  - When 승인 API 를 호출하면
  - Then 403 Forbidden 을 반환한다.

### REQ-UA-021 — 감사 추적 영속화

- **AC-UA-021-1**
  - Given 승인/거절이 처리되면
  - When `users` 행을 조회하면
  - Then `approval_changed_by`/`approval_status_changed_at` 가 기록되어 있다.
- **AC-UA-021-2**
  - Given 처리자 ID 가 기록되면
  - When 감사 추적을 수행하면
  - Then 누가 언제 승인/거절했는지 추적 가능하다.
