---
id: SPEC-CMS-USER-APPROVAL-001
version: 0.1.0
status: Draft
created: 2026-06-16
updated: 2026-06-16
author: ircp
priority: High
issue_number: null
---

# SPEC-CMS-USER-APPROVAL-001 — 사용자 가입 승인/거절 관리

## HISTORY

- 2026-06-16 (v0.1.0): 최초 작성 (Draft). 게이트형 가입 승인 워크플로 정의. 기존 인프라 재사용 원칙 — `system_setting`(V14) 설정 게이트, `users.status` enum 확장, 이메일 템플릿 시스템(SPEC-CMS-EMAIL-TEMPLATE-001), RBAC `permissions`/`role_permissions`(SPEC-CMS-RBAC-001), `@PreAuthorize("hasAnyRole('SUPER_ADMIN','DEPT_ADMIN')")` 인가 패턴 계승.

---

## 1. 개요 (Overview)

공개 사이트 회원가입(`POST /api/v1/auth/register`)은 현재 가입 즉시 `status=ACTIVE` + `MEMBER` 역할을 부여하고 JWT를 발급한다(AuthServiceImpl.registerPublicUser, 508행). 본 SPEC은 **설정으로 켜고 끌 수 있는 가입 승인 게이트**를 추가하여, 게이트가 켜진 경우 신규 가입자를 `PENDING_APPROVAL` 상태로 보류하고 관리자(SUPER_ADMIN/DEPT_ADMIN)가 승인/거절하도록 한다.

### 1.1 핵심 설계 결정 (Key Design Decisions)

본 SPEC은 신규 인프라 구축이 아니라 **기존 인프라 확장**을 원칙으로 한다.

1. **[HARD] 신규 상태 `PENDING_APPROVAL`은 기존 `UserStatus` enum에 추가** — 별도 테이블을 만들지 않는다. 단, `users` 테이블에는 `chk_users_status CHECK (status IN ('ACTIVE','INACTIVE','LOCKED','DELETED'))` 제약(V2)이 존재하므로, **마이그레이션에서 제약을 DROP 후 재생성하여 `PENDING_APPROVAL`을 추가**해야 한다.
2. **[HARD] 승인 메타데이터는 `users` 테이블에 additive 컬럼으로 저장** — `approval_status_changed_at TIMESTAMPTZ`, `approval_changed_by BIGINT`(users.id FK), `rejection_reason TEXT`(nullable). 모두 nullable이며 기존 행 백필 불요(NULL=승인 게이트 미적용 또는 즉시 활성 가입자).
3. **[HARD] 설정 게이트는 기존 `system_setting` 테이블(V14)을 재사용** — 신규 config 테이블 금지. 키 `REGISTRATION_APPROVAL_REQUIRED`, `value_type=BOOL`. 조회는 기존 `SystemSettingService.get(key)` 재사용. 기본값 시드 `'false'`(기존 즉시 활성 동작 유지 = 회귀 방지).
4. **[HARD] 신규 이메일 템플릿은 Flyway 마이그레이션 시드** — `USER_APPROVAL_CONFIRMED`, `USER_APPROVAL_REJECTED`. SPEC-CMS-EMAIL-TEMPLATE-001의 `email_template` 시드 패턴(V57, `ON CONFLICT (code, language) DO NOTHING`) 계승. 발송은 `EmailTemplateResolver.resolveAndRender()` 단일 진입점 사용, 실패 시 graceful fallback(예외 비전파).
5. **[HARD] 신규 권한 코드는 RBAC 시드** — `USER_APPROVAL:READ`, `USER_APPROVAL:APPROVE`, `USER_APPROVAL:REJECT`. `permissions` + `role_permissions`(SUPER_ADMIN 매핑) 시드 패턴(V57) 계승. 인가는 `@PreAuthorize("hasAnyRole('SUPER_ADMIN','DEPT_ADMIN')")` (UserController 기존 패턴 동일).

### 1.2 용어 (Glossary)

- **승인 게이트(Approval Gate)**: `REGISTRATION_APPROVAL_REQUIRED` 설정값. `true`면 신규 공개 가입자를 보류.
- **대기열(Approval Queue)**: `status=PENDING_APPROVAL`인 사용자 목록.
- **승인(Approve)**: 대기 사용자를 `ACTIVE`로 전환 + `MEMBER` 역할 보장 + 확인 이메일 발송.
- **거절(Reject)**: 대기 사용자를 `INACTIVE`로 전환 + 거절 사유 저장 + 거절 이메일 발송.

---

## 2. 의존성 (Dependencies)

| SPEC | 관계 | 사유 |
|------|------|------|
| SPEC-CMS-EMAIL-TEMPLATE-001 | 필수 (선행) | 승인/거절 이메일 발송에 `EmailTemplateResolver`, `email_template` 시드 패턴 사용 |
| SPEC-CMS-RBAC-001 | 필수 (선행) | `permissions`/`role_permissions` 시드, `@PreAuthorize` 인가 |
| SPEC-CMS-002 (auth) | 기반 | `UserStatus` enum, `users` 테이블, `registerPublicUser` 흐름 확장 |
| SPEC-CMS-NOTIFICATION-CENTER-001 | 선택 | 승인 요청 발생 시 `admin_notification`에 관리자 알림 적재 가능(graceful, 미배포 시 no-op) |

---

## 3. 기능 요구사항 (Functional Requirements — EARS)

### 3.1 가입 게이트 (Registration Gate)

**REQ-UA-001** (Event-Driven)
WHEN 공개 사용자가 `POST /api/v1/auth/register`로 가입을 요청하고 `REGISTRATION_APPROVAL_REQUIRED` 설정이 `true`이면, the system shall 사용자를 `status=PENDING_APPROVAL`로 생성하고 JWT를 발급하지 **않으며**, 승인 대기 안내를 응답한다.

**REQ-UA-002** (Event-Driven)
WHEN 공개 사용자가 가입을 요청하고 `REGISTRATION_APPROVAL_REQUIRED` 설정이 `false`이면, the system shall 기존 동작과 동일하게 사용자를 `status=ACTIVE`로 생성하고 `MEMBER` 역할 부여 후 JWT를 발급한다.

**REQ-UA-003** (Unwanted Behavior)
IF `system_setting`에 `REGISTRATION_APPROVAL_REQUIRED` 키가 없거나 값 파싱에 실패하면, THEN the system shall 게이트를 `false`(즉시 활성)로 간주하여 기존 가입 동작을 유지한다(회귀 방지).

**REQ-UA-004** (State-Driven)
WHILE 사용자가 `PENDING_APPROVAL` 상태이면, the system shall 해당 사용자의 로그인(`POST /api/v1/auth/login`) 시도를 거부하고 "승인 대기 중" 사유를 반환한다.

### 3.2 설정 관리 (Gate Configuration)

**REQ-UA-005** (Event-Driven)
WHEN 관리자가 `system_setting`의 `REGISTRATION_APPROVAL_REQUIRED` 값을 조회/변경하면, the system shall 기존 `SystemSettingController`(`/api/v1/system/settings`) 경로로 처리하며 별도 신규 설정 API를 만들지 않는다.

**REQ-UA-006** (Ubiquitous)
The system shall `REGISTRATION_APPROVAL_REQUIRED` 설정값을 `system_setting.value_type=BOOL`로 저장하고 `'true'`/`'false'` 문자열로 직렬화한다.

### 3.3 승인 대기열 조회 (Approval Queue)

**REQ-UA-007** (Event-Driven)
WHEN 관리자가 `GET /api/v1/users/approvals`를 요청하면, the system shall `status=PENDING_APPROVAL`인 사용자 목록을 페이지네이션하여 반환한다(가입일시 오름차순 기본 정렬).

**REQ-UA-008** (Optional)
WHERE 조회 요청에 검색 조건(이름/이메일/가입기간)이 포함되면, the system shall 해당 조건으로 대기열을 필터링한다.

**REQ-UA-009** (Event-Driven)
WHEN 관리자가 `GET /api/v1/users/approvals/{id}`를 요청하면, the system shall 해당 대기 사용자의 상세(가입일시, 이름, 이메일, 조직)를 반환한다.

### 3.4 단건 승인/거절 (Single Approve/Reject)

**REQ-UA-010** (Event-Driven)
WHEN 관리자가 `POST /api/v1/users/approvals/{id}/approve`를 요청하면, the system shall 대상 사용자를 `status=ACTIVE`로 전환하고, `MEMBER` 역할이 없으면 부여하며, `approval_status_changed_at`/`approval_changed_by`를 기록하고, `USER_APPROVAL_CONFIRMED` 이메일을 발송한다.

**REQ-UA-011** (Event-Driven)
WHEN 관리자가 `POST /api/v1/users/approvals/{id}/reject`를 요청하면, the system shall 대상 사용자를 `status=INACTIVE`로 전환하고, `rejection_reason`/`approval_status_changed_at`/`approval_changed_by`를 기록하며, `USER_APPROVAL_REJECTED` 이메일(거절 사유 포함)을 발송한다.

**REQ-UA-012** (Unwanted Behavior)
IF 거절 요청에 거절 사유(`reason`)가 비어 있으면, THEN the system shall 요청을 거부하고 400 검증 오류를 반환한다.

**REQ-UA-013** (Unwanted Behavior)
IF 승인/거절 대상 사용자가 `PENDING_APPROVAL` 상태가 아니면, THEN the system shall 요청을 거부하고 409 충돌 오류("승인 대기 상태가 아님")를 반환한다.

### 3.5 일괄 승인/거절 (Bulk Approve/Reject)

**REQ-UA-014** (Event-Driven)
WHEN 관리자가 `POST /api/v1/users/approvals/bulk-approve`로 다수 사용자 ID를 전달하면, the system shall `PENDING_APPROVAL` 상태인 대상만 일괄 승인하고, 건별 성공/실패(상태 불일치 등) 결과를 반환한다.

**REQ-UA-015** (Event-Driven)
WHEN 관리자가 `POST /api/v1/users/approvals/bulk-reject`로 다수 사용자 ID와 공통 거절 사유를 전달하면, the system shall `PENDING_APPROVAL` 상태인 대상만 일괄 거절하고, 건별 결과를 반환한다.

**REQ-UA-016** (State-Driven)
WHILE 일괄 처리가 진행되면, the system shall 개별 사용자 처리 실패가 전체 트랜잭션을 롤백하지 않도록 건별로 처리 결과를 집계한다.

### 3.6 이메일 알림 (Email Notification)

**REQ-UA-017** (Event-Driven)
WHEN 승인이 확정되면, the system shall `USER_APPROVAL_CONFIRMED` 템플릿을 `EmailTemplateResolver.resolveAndRender()`로 렌더링하여 사용자 이메일로 발송한다.

**REQ-UA-018** (Event-Driven)
WHEN 거절이 확정되면, the system shall `USER_APPROVAL_REJECTED` 템플릿에 거절 사유(`reason` 변수)를 주입하여 사용자 이메일로 발송한다.

**REQ-UA-019** (Unwanted Behavior)
IF 이메일 발송 또는 템플릿 렌더링이 실패하면, THEN the system shall 상태 전환(승인/거절)은 커밋한 채 발송 실패만 로그로 남기고 예외를 전파하지 않는다(SPEC-CMS-EMAIL-TEMPLATE-001 REQ-ET-033 graceful fallback 계승).

### 3.7 권한/감사 (Authorization & Audit)

**REQ-UA-020** (State-Driven)
WHILE 승인 대기열 조회/승인/거절 API가 호출되면, the system shall `SUPER_ADMIN` 또는 `DEPT_ADMIN` 역할만 접근을 허용하고(`@PreAuthorize("hasAnyRole('SUPER_ADMIN','DEPT_ADMIN')")`), 그 외는 403을 반환한다.

**REQ-UA-021** (Ubiquitous)
The system shall 승인/거절 처리 시 처리자(`approval_changed_by`)와 처리 시각(`approval_status_changed_at`)을 `users` 테이블에 영속화하여 감사 추적이 가능하도록 한다.

---

## 4. 비기능 요구사항 (Non-Functional Requirements)

### 4.1 성능 (Performance)

**NFR-UA-P1**: 승인 대기열 조회는 `idx_users_status`(V2, `WHERE deleted_at IS NULL`) 부분 인덱스를 활용하여 P95 300ms 이내로 응답한다.

**NFR-UA-P2**: 단건 승인/거절은 이메일 발송을 비동기(`@Async`) 또는 트랜잭션 커밋 후(after-commit)로 처리하여 API 응답 지연을 방지한다.

### 4.2 보안 (Security)

**NFR-UA-S1**: 승인/거절 API는 RBAC `@PreAuthorize`로 보호하며, JWT 미보유 시 401, 권한 부족 시 403을 반환한다.

**NFR-UA-S2**: 거절 사유는 사용자에게 이메일로 전달되므로 XSS 방지를 위해 템플릿 렌더러가 변수값을 이스케이프한다(EmailTemplateRenderer 기존 동작 활용).

**NFR-UA-S3**: `PENDING_APPROVAL` 사용자는 JWT를 발급받지 않으므로 인증된 보호 리소스에 접근할 수 없다.

### 4.3 호환성/회귀 방지 (Compatibility)

**NFR-UA-C1**: 게이트 기본값은 `false`이며, 본 SPEC 배포 후에도 기존 즉시 활성 가입 동작이 유지된다(설정을 켜기 전까지 동작 변경 없음).

**NFR-UA-C2**: 마이그레이션은 단일 Flyway 파일(V59)에 통합하여 적용 순서 충돌을 방지한다(상태 제약 변경 + additive 컬럼 + 권한/템플릿 시드).

---

## 5. 데이터 모델 변경 (Data Model)

### 5.1 `users` 테이블 (additive)

| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| `approval_status_changed_at` | TIMESTAMPTZ | nullable | 승인/거절 처리 시각 |
| `approval_changed_by` | BIGINT | nullable, FK users(id) | 처리한 관리자 ID |
| `rejection_reason` | TEXT | nullable | 거절 사유(거절 시에만 기록) |

### 5.2 `chk_users_status` 제약 재정의

```
ALTER TABLE users DROP CONSTRAINT chk_users_status;
ALTER TABLE users ADD CONSTRAINT chk_users_status
    CHECK (status IN ('ACTIVE','INACTIVE','LOCKED','DELETED','PENDING_APPROVAL'));
```

### 5.3 `system_setting` 시드 (idempotent)

```
INSERT INTO system_setting (key, value, value_type, description)
VALUES ('REGISTRATION_APPROVAL_REQUIRED', 'false', 'BOOL', '공개 가입 시 관리자 승인 필요 여부')
ON CONFLICT (key) DO NOTHING;
```

### 5.4 `permissions` / `role_permissions` 시드

`USER_APPROVAL:READ`, `USER_APPROVAL:APPROVE`, `USER_APPROVAL:REJECT` 등록 + SUPER_ADMIN 매핑(V57 패턴).

### 5.5 `email_template` 시드

`USER_APPROVAL_CONFIRMED`(변수 없음), `USER_APPROVAL_REJECTED`(변수 `reason`, `name`) 시드(V57 패턴, `ON CONFLICT (code, language) DO NOTHING`).

---

## 6. API 설계 (API Surface)

신규 컨트롤러 `UserApprovalController`(`/api/v1/users/approvals`)를 추가한다. 기존 `UserController`와 패키지 동일(`kr.co.ircp.cms.domain.auth.controller`).

| 메서드 | 경로 | 인가 | 설명 |
|--------|------|------|------|
| GET | `/api/v1/users/approvals` | SUPER_ADMIN, DEPT_ADMIN | 대기열 목록(검색/페이지) |
| GET | `/api/v1/users/approvals/{id}` | SUPER_ADMIN, DEPT_ADMIN | 대기 사용자 상세 |
| POST | `/api/v1/users/approvals/{id}/approve` | SUPER_ADMIN, DEPT_ADMIN | 단건 승인 |
| POST | `/api/v1/users/approvals/{id}/reject` | SUPER_ADMIN, DEPT_ADMIN | 단건 거절(사유 필수) |
| POST | `/api/v1/users/approvals/bulk-approve` | SUPER_ADMIN, DEPT_ADMIN | 일괄 승인 |
| POST | `/api/v1/users/approvals/bulk-reject` | SUPER_ADMIN, DEPT_ADMIN | 일괄 거절(공통 사유) |

---

## 7. 수용 기준 요약 (Acceptance Criteria Summary)

각 요구사항의 상세 Given-When-Then 시나리오는 `acceptance.md`에 정의한다(미작성 시 본 SPEC의 plan.md T9에서 생성). 핵심 게이트 기준:

- 게이트 OFF: 가입 즉시 ACTIVE + JWT (기존 동작 동일)
- 게이트 ON: 가입 시 PENDING_APPROVAL + JWT 미발급
- 승인: PENDING_APPROVAL → ACTIVE + MEMBER 역할 + 확인 이메일
- 거절: PENDING_APPROVAL → INACTIVE + 사유 저장 + 거절 이메일(사유 포함)
- 비대기 상태 승인/거절 시도 → 409
- 권한 없는 호출 → 403
- 이메일 실패 → 상태는 커밋, 발송만 실패 로그

---

## 8. 구현 작업 (Implementation Tasks)

| ID | 작업 | 산출물 | 의존 |
|----|------|--------|------|
| **T0** | DB 마이그레이션 | `V59__user_registration_approval.sql` — status 제약 재정의 + additive 3컬럼 + system_setting 시드 + 권한 3종 시드 + 이메일 템플릿 2종 시드 | — |
| **T1** | 도메인 enum 확장 | `UserStatus.PENDING_APPROVAL` 추가 | T0 |
| **T2** | 가입 게이트 적용 | `AuthServiceImpl.registerPublicUser` 분기 — 게이트 ON 시 PENDING_APPROVAL + JWT 미발급, `SystemSettingService.get` 재사용 | T1 |
| **T3** | 로그인 차단 | `AuthServiceImpl.login` — PENDING_APPROVAL 상태 거부 | T1 |
| **T4** | 승인 서비스 | `UserApprovalService`/`*Impl` — 단건/일괄 승인·거절, 상태 검증(409), 메타데이터 기록, MEMBER 역할 보장 | T1 |
| **T5** | 이메일 연동 | 승인/거절 시 `EmailTemplateResolver.resolveAndRender` 호출, after-commit/@Async, graceful fallback | T4 |
| **T6** | 컨트롤러/DTO | `UserApprovalController` + 요청/응답 record, `@PreAuthorize` 인가, Mapper 메서드 | T4 |
| **T7** | 프론트 API 클라이언트 | `frontend/admin/src/api/userApprovals.ts` — 6개 엔드포인트 | T6 |
| **T8** | 프론트 대기열 화면 | `views/users/ApprovalQueueView.vue` — 목록/검색/승인·거절 다이얼로그(거절 사유 입력)/일괄선택, 라우트 `/users/approvals`, 설정 토글 UI | T7 |
| **T9** | 수용 기준 문서 | `acceptance.md` Given-When-Then(최소 2개/요구사항) | T6 |
| **T10** | 테스트 | 백엔드 단위/통합(게이트 ON/OFF, 승인/거절, 409/403, 이메일 fallback), 프론트 컴포넌트 테스트 | T2~T8 |

---

## 9. 제외 사항 (Exclusions — What NOT to Build)

- **신규 config 테이블 생성 금지**: `system_setting`(V14) 재사용. `system_config`/`app_config` 등 신설하지 않는다.
- **별도 approval_history 테이블 생성 금지**: 승인 메타데이터는 `users` additive 컬럼에 저장. 다단계 승인 이력/워크플로는 본 SPEC 범위 밖.
- **다단계 승인(multi-step approval) 미포함**: 단일 관리자 승인만. 결재선/위임 결재는 제외.
- **관리자 생성 사용자(`POST /api/v1/users`)는 게이트 미적용**: 관리자가 직접 만든 계정은 기존대로 즉시 ACTIVE. 게이트는 공개 가입(`/auth/register`)에만 적용.
- **이메일 외 채널(SMS/푸시) 알림 제외**: 승인/거절 통지는 이메일만.
- **거절 후 재가입/재신청 플로 제외**: 거절은 INACTIVE 전환까지만. 재신청 워크플로는 후속 SPEC.
- **승인 자동화(규칙 기반 자동 승인) 제외**: 모든 승인은 관리자 수동 처리.
- **함수/클래스 시그니처·Mapper XML 세부 구현은 본 SPEC 범위 밖** (Run 단계에서 결정).
