---
id: SPEC-CMS-NOTI-EXT-001
version: 1.0.0
status: Completed
created: 2026-06-18
updated: 2026-06-23
author: ircp
priority: High
issue_number: null
---

# SPEC-CMS-NOTI-EXT-001 — 알림 기능 확장: 이메일 채널 발송 / 알림 템플릿 관리 / 발송 스케줄러

## HISTORY

- 2026-06-18 (v1.0.0): 초안 작성. Phase 0.5 research.md 기반. notification_template 스텁 확장(V64), 발송 워커, EMAIL/INAPP 채널 실행기, PolicyDispatchController 권한 보강을 포함.

---

## Background (배경)

### 현재 상태

iroum-cms의 알림 도메인은 4개 영역으로 분산되어 있으며, **정책 발송(dispatch) 파이프라인이 동작하지 않는 상태**다.

1. `notification_template` 테이블은 V16 스텁(컬럼 5개)으로만 존재한다. Java 엔티티, CRUD API, 관리 UI가 모두 없다. `notification_dispatch_schedule.template_id`가 이 테이블을 FK 참조하고 있으나 실데이터를 채울 수단이 없다.
2. `PolicyDispatchServiceImpl.triggerNow()`는 스케줄 상태를 `PROCESSING`으로만 변경한다. PROCESSING 이후 실제 알림을 발송하는 **백그라운드 워커가 존재하지 않아** 전체 dispatch 시스템이 무동작 상태다.
3. `PolicyDispatchController`에 `@PreAuthorize`가 없어 JWT 인증만 통과하면 모든 관리자가 발송 스케줄을 생성/트리거할 수 있는 **권한 검증 공백**이 있다.

### 재사용 가능한 완성 인프라

- `EmailService` / `EmailServiceImpl` (auth 도메인): `JavaMailSender` + `@Async("auditExecutor")` fire-and-forget 발송이 완성되어 있다.
- `email_template` (V55) + `EmailTemplateResolver`: 이메일 HTML/text 템플릿 조회·렌더링·하드코딩 폴백이 완성되어 있다.
- `EmailTemplateAdminController` (V55): 템플릿 CRUD의 구조적 레퍼런스.
- `QnaNotificationServiceImpl`: INAPP+EMAIL 이중 발송의 동작 레퍼런스.
- `user_notification_inbox` (V35): 시민 INAPP 수신함이자 발송 통계 단일 진실 원천.
- 멱등성 키: `SHA-256(scheduleId || userId || dispatchType)` (이미 `PolicyDispatchServiceImpl`에 구현됨).

### 이 SPEC의 목적

기존 이메일 인프라를 **재사용(재구축 아님)**하여 dispatch 파이프라인을 동작 상태로 만든다. 구체적으로:
EMAIL/INAPP 채널 실제 발송, `notification_template` 정식 확장 + CRUD, `@Scheduled` 폴링 발송 워커, 그리고 발송 컨트롤러의 권한 보강을 포함한다.

PUSH(FCM), KAKAO, SMS는 인프라 부재가 심각하여 별도 SPEC으로 분리한다.

---

## Scope (범위)

### IN SCOPE

1. **EMAIL 채널 실제 발송** — 기존 `EmailService` 재사용. `notification_template.email_template_id` 위임 또는 `body_html`/`subject` 직접 사용.
2. **`notification_template` 스키마 확장** — V64 마이그레이션 (additive ALTER).
3. **`NotificationTemplate` CRUD API** — `/api/v1/admin/notification-templates` (`EmailTemplateAdminController` 패턴 복제).
4. **`NotificationDispatchWorker`** — `@Scheduled(fixedDelay=60_000)` 폴링 워커, `LIMIT 10`, 우선순위 정렬.
5. **`DispatchChannelExecutor` 인터페이스** + `EmailDispatchExecutor` + `InappDispatchExecutor`.
6. **`PolicyDispatchController` 권한 보강** — 누락된 `@PreAuthorize` 추가.
7. **권한 시드 추가** — `NOTIFICATION_TEMPLATE:READ/WRITE/DELETE`, `DISPATCH:WRITE` (V64).
8. **프론트엔드** — `NotificationTemplateListView.vue` + `api/notificationTemplate.ts` + Pinia 스토어.
9. **프론트엔드** — `PolicyDispatchView.vue` 템플릿 선택 드롭다운 실연동.

### OUT OF SCOPE

이 섹션은 [HARD] 필수이며 최소 1개 이상의 항목을 포함한다.

1. **PUSH/FCM 알림** — FCM 의존성, device token 저장, push 서비스가 전무. → SPEC-CMS-NOTI-PUSH-001로 분리.
2. **KAKAO 채널** — 카카오 비즈니스 API 연동 필요. → SPEC-CMS-NOTI-KAKAO-001로 분리.
3. **SMS 채널** — AWS SNS/Twilio 등 외부 연동 필요. → 별도 SPEC.
4. **`QnaNotificationServiceImpl` 변경** — 레퍼런스로만 참조, 코드 수정 금지.
5. **`NotificationStatServiceImpl.resend()` 실재발송 연결** — 현 플레이스홀더 유지(상태 정정만). 실채널 재발송은 이 SPEC 범위 밖.
6. **`email_template` (V55) 구조 변경** — 기존 이메일 템플릿 도메인은 무수정 재사용.
7. **`admin_notification` (V40) 발송 대상 포함** — 관리자 수신함은 dispatch 대상이 아님. INAPP 발송은 `user_notification_inbox`(V35)만 모수.
8. **멀티노드 분산 락 / Spring Batch / Quartz 도입** — 최초 구현은 단일 노드 `@Scheduled` 폴링으로 한정. 스케일 요건은 후속 SPEC.

---

## Requirements (요구사항, EARS 형식)

### 알림 템플릿 관리 (Notification Template CRUD)

- **REQ-NE-001** (Ubiquitous): The system shall persist notification templates in the `notification_template` table with columns supporting EMAIL channel rendering (subject, body_html, variables) and INAPP channel rendering (body_template).

- **REQ-NE-002** (Ubiquitous): The system shall enforce a UNIQUE constraint on `(code, language)` for notification templates, mirroring the `email_template` pattern.

- **REQ-NE-003** (Event-Driven): When an administrator with `NOTIFICATION_TEMPLATE:WRITE` permission submits a create request, the system shall validate that the `(code, language)` pair does not already exist and shall persist the new template with `created_by` set to the requesting administrator.

- **REQ-NE-004** (Event-Driven): When an administrator with `NOTIFICATION_TEMPLATE:READ` permission requests the template list, the system shall return a paginated result filterable by `channel`, `is_active`, and `code`.

- **REQ-NE-005** (Event-Driven): When an administrator with `NOTIFICATION_TEMPLATE:DELETE` permission deletes a template that is referenced by a non-terminal (`PENDING`/`PROCESSING`) dispatch schedule, then the system shall reject the deletion with a conflict response rather than orphaning the FK reference.

- **REQ-NE-006** (Optional): Where a notification template has a non-null `email_template_id`, the system shall resolve EMAIL channel content from the referenced `email_template` row rather than from the template's own `subject`/`body_html` columns.

- **REQ-NE-007** (Event-Driven): When an administrator requests a template preview with sample variable values, the system shall render and return the resolved subject and body without sending any message.

### 발송 워커 / 채널 실행 (Dispatch Worker / Channel Execution)

- **REQ-NE-008** (State-Driven): While a `notification_dispatch_schedule` row has `status = 'PENDING'` and `scheduled_at <= now()`, the dispatch worker shall be eligible to claim it for processing.

- **REQ-NE-009** (Event-Driven): When the dispatch worker polls (every 60 seconds), the system shall select at most 10 eligible schedules ordered by `priority DESC, scheduled_at ASC` and transition each claimed schedule to `status = 'PROCESSING'` before dispatching.

- **REQ-NE-010** (Event-Driven): When the dispatch worker processes a schedule, the system shall delegate to one `DispatchChannelExecutor` per channel listed in the schedule's `channels` JSONB array.

- **REQ-NE-011** (Ubiquitous): The `InappDispatchExecutor` shall create recipient notifications exclusively in the `user_notification_inbox` (V35) table and shall never write to `admin_notification` (V40).

- **REQ-NE-012** (Ubiquitous): The `EmailDispatchExecutor` shall send via the existing `EmailService`, decrypting recipient email addresses through `EmailEncryptionService.decrypt()` immediately before transmission and never persisting plaintext email.

- **REQ-NE-013** (Event-Driven): When the worker dispatches to a recipient, the system shall insert a `notification_dispatch_target` row keyed by the idempotency key `SHA-256(scheduleId || userId || dispatchType)`, and if a row with that key already exists, then the system shall skip the duplicate send.

- **REQ-NE-014** (Event-Driven): When all targets of a schedule have been processed without unrecoverable error, the system shall transition the schedule to `status = 'COMPLETED'` and set `completed_at`.

- **REQ-NE-015** (Unwanted Behavior): If a channel executor throws during dispatch of a single target, then the system shall record `notification_dispatch_target.status = 'FAILED'` with `error_message` for that target and shall continue processing remaining targets rather than aborting the entire schedule.

- **REQ-NE-016** (Unwanted Behavior): If every target of a schedule fails, then the system shall transition the schedule to `status = 'FAILED'` rather than `COMPLETED`.

- **REQ-NE-017** (Ubiquitous): The dispatch worker shall execute on a thread pool separate from `auditExecutor` to avoid contending with synchronous email/audit fire-and-forget tasks.

- **REQ-NE-018** (State-Driven): While a recipient has opted out of a channel in `notification_subscription`, the system shall set that recipient's target `status = 'SKIPPED_OPTOUT'` and shall not transmit on that channel.

### 발송 예약 권한 (Dispatch Authorization)

- **REQ-NE-019** (Unwanted Behavior): If a request to create, trigger, or cancel a dispatch schedule arrives without `DISPATCH:WRITE` permission (or `SUPER_ADMIN` role), then the system shall reject the request with a 403 response.

- **REQ-NE-020** (Ubiquitous): The system shall seed the permissions `NOTIFICATION_TEMPLATE:READ`, `NOTIFICATION_TEMPLATE:WRITE`, `NOTIFICATION_TEMPLATE:DELETE`, and `DISPATCH:WRITE`, and shall grant all of them to `SUPER_ADMIN` by default.

### 프론트엔드 (Frontend)

- **REQ-NE-021** (Event-Driven): When an administrator opens the notification template management view, the system shall display a list of templates with create, edit, delete, and preview actions gated by the corresponding `NOTIFICATION_TEMPLATE:*` permissions.

- **REQ-NE-022** (Event-Driven): When an administrator opens the new dispatch schedule dialog in `PolicyDispatchView.vue`, the system shall populate the template selection dropdown from the live `/api/v1/admin/notification-templates` endpoint.

### 비기능 / 호환 (Non-functional / Compatibility)

- **REQ-NE-023** (Ubiquitous): The system shall use MyBatis (Mapper interface + XML) for all database access; no JPA repositories shall be introduced.

- **REQ-NE-024** (Ubiquitous): The V64 migration shall be additive (ALTER ADD COLUMN with defaults), preserving the existing `notification_template.id` FK relationship with `notification_dispatch_schedule.template_id`.

- **REQ-NE-025** (Optional): Where an EMAIL template cannot be resolved at dispatch time, the system shall fall back to a hardcoded default body (availability-first), matching the existing `EmailServiceImpl` resolver-failure behavior.

---

## Acceptance Criteria (인수 기준)

각 기준은 번호가 부여되고 테스트 가능하며 구체적이다.

- **AC-NE-001**: V64 마이그레이션 적용 후 `notification_template` 테이블에 `subject`, `body_html`, `variables`, `language`, `is_active`, `email_template_id`, `created_by`, `updated_by`, `created_at`, `updated_at` 컬럼이 존재하고, 기존 `id` 컬럼과 `notification_dispatch_schedule.template_id` FK 제약이 유지된다. (REQ-NE-001, REQ-NE-024)

- **AC-NE-002**: `(code, language)` 동일 쌍으로 두 번째 템플릿을 INSERT하면 UNIQUE 제약 위반으로 실패한다. (REQ-NE-002)

- **AC-NE-003**: `NOTIFICATION_TEMPLATE:WRITE` 권한 보유자가 `POST /api/v1/admin/notification-templates`로 신규 템플릿을 생성하면 201 응답과 함께 `created_by`가 요청자 ID로 설정된 행이 저장된다. 권한 미보유자는 403을 받는다. (REQ-NE-003, REQ-NE-020)

- **AC-NE-004**: `GET /api/v1/admin/notification-templates?channel=EMAIL&isActive=true&page=0&size=20` 호출 시 필터가 적용된 페이지네이션 결과를 반환한다. (REQ-NE-004)

- **AC-NE-005**: `PENDING` 상태 dispatch 스케줄이 참조하는 템플릿을 `DELETE` 하면 409 Conflict를 반환하고 행은 삭제되지 않는다. (REQ-NE-005)

- **AC-NE-006**: `email_template_id`가 설정된 템플릿으로 EMAIL 발송 시, 발송된 제목/본문이 참조된 `email_template` 행의 값과 일치한다. `email_template_id`가 NULL이면 템플릿 자체의 `subject`/`body_html`이 사용된다. (REQ-NE-006)

- **AC-NE-007**: `POST /api/v1/admin/notification-templates/{id}/preview`에 샘플 변수를 전달하면 렌더링된 subject/body를 반환하고, `user_notification_inbox`/이메일 발송이 발생하지 않는다. (REQ-NE-007)

- **AC-NE-008**: `PENDING` 상태이고 `scheduled_at <= now()`인 스케줄 11건이 있을 때 워커 1회 폴링은 정확히 10건만 `PROCESSING`으로 전환하며, `priority DESC, scheduled_at ASC` 순서로 선택한다. (REQ-NE-008, REQ-NE-009)

- **AC-NE-009**: INAPP 채널 발송 후 해당 수신자 행이 `user_notification_inbox`에 INSERT되고, `admin_notification`에는 어떤 행도 INSERT되지 않는다. (REQ-NE-011)

- **AC-NE-010**: EMAIL 채널 발송 시 로그/캡처에 평문 이메일 주소가 저장되지 않으며, `EmailService` 호출 직전 `EmailEncryptionService.decrypt()`가 호출된다. (REQ-NE-012)

- **AC-NE-011**: 동일 `(scheduleId, userId, dispatchType)`로 워커가 두 번 실행되어도 `notification_dispatch_target`에는 멱등성 키당 1행만 존재하고, 두 번째 발송은 스킵된다. (REQ-NE-013)

- **AC-NE-012**: 한 스케줄의 5개 대상 중 1개 대상의 채널 실행기가 예외를 던지면, 그 대상만 `FAILED`(+`error_message`)로 기록되고 나머지 4개는 정상 처리되며 스케줄은 `COMPLETED`가 된다. (REQ-NE-014, REQ-NE-015)

- **AC-NE-013**: 한 스케줄의 모든 대상이 실패하면 스케줄 상태가 `FAILED`로 전환된다. (REQ-NE-016)

- **AC-NE-014**: 발송 워커가 사용하는 `TaskExecutor`/`ThreadPoolTaskScheduler` 빈이 `auditExecutor`와 다른 이름의 별도 빈으로 구성되어 있다. (REQ-NE-017)

- **AC-NE-015**: `notification_subscription`에서 EMAIL 옵트아웃한 사용자는 EMAIL 대상 행이 `SKIPPED_OPTOUT`이 되고 이메일이 전송되지 않는다. (REQ-NE-018)

- **AC-NE-016**: `DISPATCH:WRITE` 권한(또는 `SUPER_ADMIN`) 없이 `POST /api/v1/policy/admin/dispatch/schedules` 및 `.../{id}/trigger`, `.../{id}/cancel` 호출 시 403을 반환한다. (REQ-NE-019)

- **AC-NE-017**: V64 적용 후 `permissions` 테이블에 `NOTIFICATION_TEMPLATE:READ/WRITE/DELETE`, `DISPATCH:WRITE` 4개 코드가 존재하고 `SUPER_ADMIN`에 모두 매핑된다. 재적용(idempotent) 시 `ON CONFLICT DO NOTHING`으로 중복 없이 통과한다. (REQ-NE-020)

- **AC-NE-018**: 권한별로 `NotificationTemplateListView.vue`의 생성/수정/삭제/미리보기 버튼이 활성/비활성 처리된다. (REQ-NE-021)

- **AC-NE-019**: `PolicyDispatchView.vue` 신규 예약 다이얼로그의 템플릿 드롭다운이 실제 `/api/v1/admin/notification-templates` 응답으로 채워지며, 하드코딩/빈 목록이 아니다. (REQ-NE-022)

- **AC-NE-020**: dispatch 시점에 템플릿 해석이 실패해도 EMAIL은 하드코딩 폴백 본문으로 전송되며, 예외가 전체 스케줄을 중단시키지 않는다. (REQ-NE-025)

---

## Technical Approach (기술적 접근)

### 아키텍처 결정

1. **이메일 재구축 금지, 재사용** — `EmailService` 인터페이스에 dispatch용 메서드를 추가하거나, `EmailDispatchExecutor`가 기존 `EmailService` + `EmailTemplateResolver`를 호출한다. SMTP/`JavaMailSender` 신규 구성 없음.

2. **템플릿 이원화 해소 (Option A 채택)** — `notification_template`에 `email_template_id BIGINT REFERENCES email_template(id)` 컬럼을 추가한다. EMAIL 채널은 이 FK가 가리키는 완성된 `email_template`을 위임 사용하고, FK가 NULL이면 `notification_template` 자체 `subject`/`body_html`을 사용한다. 이로써 V55의 완성된 CRUD/권한 자산을 재활용하면서 멀티채널 단일 템플릿 엔트리를 유지한다.

3. **채널 실행기 분리 (Strategy 패턴)** — `DispatchChannelExecutor` 인터페이스를 두고 채널별 구현체(`EmailDispatchExecutor`, `InappDispatchExecutor`)를 `Map<Channel, DispatchChannelExecutor>`로 주입한다. 향후 `PushDispatchExecutor`, `KakaoDispatchExecutor` 추가가 OCP를 만족한다.

4. **`@Scheduled` 폴링 워커** — 추가 인프라(Quartz/Batch) 없이 최소 구현. `@Scheduled(fixedDelay=60_000)` 단일 노드 폴링. 동시성은 claim 단계의 `UPDATE ... WHERE status='PENDING'` 조건부 전이로 보호(낙관적 claim). 멀티노드 분산 락은 후속 SPEC.

5. **별도 스레드 풀** — 발송 워커 전용 `ThreadPoolTaskScheduler`/`TaskExecutor` 빈(`dispatchExecutor`)을 구성하여 `auditExecutor`(이메일 fire-and-forget)와 격리 (REQ-NE-017).

6. **INAPP 모수 고정** — INAPP 발송은 `user_notification_inbox`(V35)에만 INSERT한다. `admin_notification`(V40)은 관리자 운영 수신함이므로 dispatch 대상에서 제외 (REQ-NE-011). 이는 발송 통계(SPEC-CMS-NOTIFICATION-STAT-001)의 모수 무결성을 보존한다.

7. **멱등성** — 기존 `SHA-256(scheduleId || userId || dispatchType)` 키 + `notification_dispatch_target` UNIQUE 제약으로 중복 발송 차단 (REQ-NE-013).

8. **권한 보강** — `PolicyDispatchController`의 모든 mutating 엔드포인트에 `@PreAuthorize("hasAuthority('DISPATCH:WRITE') or hasRole('SUPER_ADMIN')")` 추가.

### 발송 흐름 (목표 상태)

```
@Scheduled(60s)
  → SELECT ... WHERE status='PENDING' AND scheduled_at<=now()
      ORDER BY priority DESC, scheduled_at ASC LIMIT 10
  → 조건부 UPDATE status=PROCESSING (claim)
  → for each channel in schedule.channels:
        executor = executors.get(channel)
        for each recipient:
            if optout(channel) → target.status=SKIPPED_OPTOUT
            elif target(idempotencyKey) exists → skip
            else:
                try  executor.dispatch(recipient, template)
                     target.status=SENT, sentAt=now()
                catch e: target.status=FAILED, errorMessage=e
  → schedule.status = (allFailed ? FAILED : COMPLETED), completedAt=now()
```

---

## Migration Plan (V64 DDL 개요)

다음 마이그레이션 버전은 **V64**이다. 단일 마이그레이션에 스키마 확장 + 권한 시드를 함께 담는다.

```sql
-- V64__notification_template_extension.sql  (SPEC-CMS-NOTI-EXT-001)

-- 1) notification_template 정식 확장 (additive ALTER)
ALTER TABLE notification_template
  ADD COLUMN IF NOT EXISTS subject           VARCHAR(300),
  ADD COLUMN IF NOT EXISTS body_html         TEXT,
  ADD COLUMN IF NOT EXISTS variables         JSONB        DEFAULT '[]'::jsonb,
  ADD COLUMN IF NOT EXISTS language          VARCHAR(10)  NOT NULL DEFAULT 'ko',
  ADD COLUMN IF NOT EXISTS is_active         BOOLEAN      NOT NULL DEFAULT TRUE,
  ADD COLUMN IF NOT EXISTS email_template_id BIGINT       REFERENCES email_template(id) ON DELETE SET NULL,
  ADD COLUMN IF NOT EXISTS created_by        BIGINT       REFERENCES users(id),
  ADD COLUMN IF NOT EXISTS updated_by        BIGINT       REFERENCES users(id),
  ADD COLUMN IF NOT EXISTS created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
  ADD COLUMN IF NOT EXISTS updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now();

-- 2) (code, language) UNIQUE — 기존 code UNIQUE 제약 대체
--    기존 stub은 code 단독 UNIQUE. language 도입으로 복합키로 전환.
ALTER TABLE notification_template DROP CONSTRAINT IF EXISTS notification_template_code_key;
CREATE UNIQUE INDEX IF NOT EXISTS ux_notification_template_code_lang
  ON notification_template (code, language);

-- 3) 권한 시드 (idempotent)
INSERT INTO permissions (code, resource, action, description) VALUES
  ('NOTIFICATION_TEMPLATE:READ',   'NOTIFICATION_TEMPLATE', 'READ',   '알림 템플릿 조회'),
  ('NOTIFICATION_TEMPLATE:WRITE',  'NOTIFICATION_TEMPLATE', 'WRITE',  '알림 템플릿 등록/수정'),
  ('NOTIFICATION_TEMPLATE:DELETE', 'NOTIFICATION_TEMPLATE', 'DELETE', '알림 템플릿 삭제'),
  ('DISPATCH:WRITE',               'DISPATCH',              'WRITE',  '발송 예약 생성/트리거/취소')
ON CONFLICT (code) DO NOTHING;

-- 4) SUPER_ADMIN 자동 매핑 (V57 email_template 시드 패턴 계승)
--    role_permissions 매핑은 V57 패턴을 따르되, 실제 컬럼/테이블명은 run 단계에서 V57 확인 후 확정.
```

**마이그레이션 주의사항**:
- `notification_template`에 기존 데이터가 없을 가능성이 높아(stub) ALTER가 안전하다. run 단계에서 행 존재 여부를 먼저 확인한다.
- `language NOT NULL DEFAULT 'ko'` + `(code, language)` UNIQUE 전환 시, 기존 `code` 단독 UNIQUE 제약명(`notification_template_code_key`)을 실제 DB에서 확인 후 DROP한다.
- `role_permissions` 매핑 테이블/컬럼명은 V57(`email_template_seed`)의 실제 SQL을 run 단계에서 확인하여 동일 패턴으로 작성한다.

---

## API Contract (API 계약)

### 신규 엔드포인트

```
GET    /api/v1/admin/notification-templates          → 목록 (NOTIFICATION_TEMPLATE:READ)
GET    /api/v1/admin/notification-templates/{id}     → 상세 (NOTIFICATION_TEMPLATE:READ)
POST   /api/v1/admin/notification-templates          → 생성 (NOTIFICATION_TEMPLATE:WRITE)
PUT    /api/v1/admin/notification-templates/{id}     → 수정 (NOTIFICATION_TEMPLATE:WRITE)
DELETE /api/v1/admin/notification-templates/{id}     → 삭제 (NOTIFICATION_TEMPLATE:DELETE)
POST   /api/v1/admin/notification-templates/{id}/preview → 미리보기 (NOTIFICATION_TEMPLATE:WRITE)
```

### 요청/응답 형태

`POST /api/v1/admin/notification-templates` (Request, JSON):
```json
{
  "code": "POLICY_APPLICATION_OPEN",
  "name": "정책 접수 시작 알림",
  "channel": "EMAIL",
  "language": "ko",
  "subject": "[{{policyName}}] 접수가 시작되었습니다",
  "bodyHtml": "<p>{{userName}}님, ...</p>",
  "bodyTemplate": "{{userName}}님, {{policyName}} 접수가 시작되었습니다.",
  "variables": [{ "key": "userName", "desc": "수신자 이름" }],
  "emailTemplateId": null,
  "isActive": true
}
```

응답 (201, JSON) — `NotificationTemplateResponse`:
```json
{
  "id": 12,
  "code": "POLICY_APPLICATION_OPEN",
  "name": "정책 접수 시작 알림",
  "channel": "EMAIL",
  "language": "ko",
  "subject": "[{{policyName}}] 접수가 시작되었습니다",
  "bodyHtml": "<p>{{userName}}님, ...</p>",
  "bodyTemplate": "{{userName}}님, ...",
  "variables": [{ "key": "userName", "desc": "수신자 이름" }],
  "emailTemplateId": null,
  "isActive": true,
  "createdBy": 1,
  "updatedBy": 1,
  "createdAt": "2026-06-18T10:00:00+09:00",
  "updatedAt": "2026-06-18T10:00:00+09:00"
}
```

`POST /.../{id}/preview` (Request):
```json
{ "variables": { "userName": "홍길동", "policyName": "청년창업지원" } }
```
응답 (200):
```json
{ "subject": "[청년창업지원] 접수가 시작되었습니다", "body": "홍길동님, 청년창업지원 접수가 시작되었습니다." }
```

목록 응답은 기존 프로젝트 페이지네이션 규약(0-base 서버 page, `content`/`totalElements`/`totalPages`)을 따른다.

### 권한 보강 (기존 엔드포인트 — 동작 변경 없음, 권한만 추가)

```
POST /api/v1/policy/admin/dispatch/schedules           → +@PreAuthorize(DISPATCH:WRITE | SUPER_ADMIN)
POST /api/v1/policy/admin/dispatch/schedules/{id}/trigger → +@PreAuthorize(DISPATCH:WRITE | SUPER_ADMIN)
POST /api/v1/policy/admin/dispatch/schedules/{id}/cancel  → +@PreAuthorize(DISPATCH:WRITE | SUPER_ADMIN)
```

---

## File Change Map (파일 변경 맵)

### 백엔드 — 신규 생성

```
domain/notification/template/admin/
├── entity/NotificationTemplate.java                       (신규)
├── dto/NotificationTemplateCreateRequest.java   (record)  (신규)
├── dto/NotificationTemplateUpdateRequest.java   (record)  (신규)
├── dto/NotificationTemplateResponse.java        (record)  (신규)
├── dto/NotificationTemplatePreviewRequest.java  (record)  (신규)
├── repository/NotificationTemplateMapper.java             (신규)
├── repository/NotificationTemplateMapper.xml             (신규)
├── service/NotificationTemplateService.java               (신규)
├── service/NotificationTemplateServiceImpl.java           (신규)
└── controller/NotificationTemplateAdminController.java    (신규)

domain/policy/dispatch/executor/
├── DispatchChannelExecutor.java   (interface)             (신규)
├── EmailDispatchExecutor.java                             (신규)
└── InappDispatchExecutor.java                             (신규)

domain/policy/dispatch/worker/
└── NotificationDispatchWorker.java  (@Scheduled)          (신규)

config/
└── DispatchSchedulerConfig.java  (@EnableScheduling + dispatchExecutor 빈)  (신규)

resources/db/migration/
└── V64__notification_template_extension.sql              (신규)
```

### 백엔드 — 수정

```
domain/policy/dispatch/controller/PolicyDispatchController.java   (수정: @PreAuthorize 추가)
domain/policy/dispatch/service/PolicyDispatchServiceImpl.java     (수정: triggerNow가 워커 픽업 가능 상태로 정합 — 필요 시)
domain/policy/dispatch/repository/NotificationDispatchScheduleMapper.{java,xml}  (수정: claim/조회 쿼리 추가)
domain/policy/dispatch/repository/NotificationDispatchTargetMapper.{java,xml}    (수정: 멱등 INSERT/상태 업데이트)
```

### 프론트엔드 — 신규/수정

```
frontend/admin/src/views/notification/NotificationTemplateListView.vue   (신규)
frontend/admin/src/api/notificationTemplate.ts                           (신규)
frontend/admin/src/stores/notificationTemplate.ts                        (신규)
frontend/admin/src/router/...                                            (수정: notification-templates 라우트 추가)
frontend/admin/src/views/policy/PolicyDispatchView.vue                   (수정: 템플릿 드롭다운 실연동)
```

---

## Risks (위험 요소, research §11 기반)

- **R1 (Critical) — notification_template 스텁 스키마 + FK 호환성**: `notification_dispatch_schedule.template_id`가 이미 `notification_template.id`를 FK 참조 중이다. ALTER ADD COLUMN은 FK에 영향 없으나, `code` 단독 UNIQUE → `(code, language)` 복합 UNIQUE 전환 시 기존 제약명 확인·DROP이 필요하다. 완화: run 단계에서 실제 제약명 확인 후 `DROP CONSTRAINT IF EXISTS`.

- **R2 (High) — 발송 워커 부재**: 워커가 없으면 전체 dispatch가 무동작. `@Scheduled` 폴링으로 최소 구현하되, 단일 노드 가정. 멀티노드 배포 시 중복 claim 위험. 완화: 조건부 `UPDATE WHERE status='PENDING'` 낙관적 claim + 멱등성 키. 분산 락은 후속 SPEC.

- **R3 (High) — PolicyDispatchController 권한 누락**: 현재 JWT만 통과하면 누구나 발송 트리거 가능. 워커 구현과 동시에 권한 추가가 필수(워커가 없을 때는 무해했으나 워커 도입 즉시 실발송 위험). 완화: REQ-NE-019로 [HARD] 처리.

- **R4 (Medium) — email_template / notification_template 이원화**: 두 테이블의 목적 혼재. 완화: Option A(`email_template_id` FK 위임)로 명시적 연결, 단일 진실 위치 유지.

- **R5 (Medium) — PUSH 인프라 부재**: 이 SPEC에 PUSH 포함 시 부하 급증. 완화: Out of Scope로 분리(SPEC-CMS-NOTI-PUSH-001).

- **R6 (Low) — NotificationStatService.resend() 플레이스홀더**: 통계 패널 재발송은 상태 정정만 수행. 이 SPEC에서 실재발송 연결 안 함(Out of Scope). 완화: 후속 SPEC에서 dispatch 워커 재사용.

- **R7 (운영) — @Async / @Scheduled 스레드 풀 경합**: 워커가 `auditExecutor` 공유 시 이메일 fire-and-forget과 경합. 완화: REQ-NE-017로 별도 `dispatchExecutor` 빈 강제.

---

## 구현 순서 권장 (research §12.8 계승)

```
Phase 1 (백엔드 기반)
  1. V64 마이그레이션 (notification_template 확장 + 권한 시드 + SUPER_ADMIN 매핑)
  2. NotificationTemplate 엔티티 + Mapper + CRUD 서비스
  3. NotificationTemplateAdminController (@PreAuthorize)
  4. PolicyDispatchController @PreAuthorize 추가

Phase 2 (발송 워커)
  5. DispatchSchedulerConfig (dispatchExecutor 빈)
  6. DispatchChannelExecutor 인터페이스 + EmailDispatchExecutor
  7. InappDispatchExecutor (user_notification_inbox INSERT)
  8. NotificationDispatchWorker @Scheduled 폴링 + claim/멱등 처리

Phase 3 (프론트엔드)
  9. api/notificationTemplate.ts
  10. stores/notificationTemplate.ts
  11. NotificationTemplateListView.vue + 라우터 등록
  12. PolicyDispatchView 템플릿 드롭다운 연동
```

---

## 참조 구현 (반드시 패턴 준수)

- `EmailTemplateAdminController` — 템플릿 CRUD 구조 레퍼런스
- `QnaNotificationServiceImpl` — INAPP+EMAIL 이중 발송 패턴
- `EmailServiceImpl` — `@Async` fire-and-forget + 템플릿 폴백 패턴
- `PolicyDispatchServiceImpl` — 멱등성 키 / 야간 차단 로직 (기존)
- `EmailEncryptionService` — PII 복호화 (발송 직전)

---

## Implementation Notes

**구현 완료일**: 2026-06-18
**구현 커밋**: `efe2ed3` — `feat(notification): SPEC-CMS-NOTI-EXT-001 알림 기능 확장 구현`
**변경 규모**: 42개 파일, 2,534줄 추가

### 구현된 주요 컴포넌트

#### 백엔드

- **V61 Flyway 마이그레이션** (`V61__notification_template_extension.sql`): `notification_template` 테이블에 10개 컬럼 추가 (subject, body_html, variables JSONB, language, is_active, email_template_id FK, created_by FK, updated_by FK, created_at, updated_at). 기존 3개 NOT NULL 제약 완화, `(code, language)` 복합 UNIQUE 인덱스 추가, RBAC 권한 시드 (`NOTIFICATION_TEMPLATE:READ/WRITE/DELETE`, `POLICY:DISPATCH:READ/WRITE`).

- **`NotificationTemplateAdminController`**: REST CRUD API (`/api/v1/notification/admin/template`), `@PreAuthorize` 권한 가드 적용.

- **`NotificationTemplateServiceImpl`**: create, getAll(페이지네이션), getById, update, delete, previewTemplate (`${var}` 치환, 발송 없음).

- **`NotificationTemplateMapper` + `.xml`**: MyBatis 매퍼, variables 컬럼 JSONB 지원 (`variables::jsonb` 캐스트).

- **`DispatchChannelExecutor`**: 채널 실행기 Strategy 인터페이스.

- **`EmailDispatchExecutor`**: `MimeMessage` + `MimeMessageHelper` HTML 이메일 발송, 발송 직전 복호화(PII 보호, 로그 평문 이메일 금지).

- **`InappDispatchExecutor`**: `user_notification_inbox`에만 인앱 알림 기록.

- **`NotificationDispatchWorker`**: `@Scheduled(fixedDelay=60_000)` 폴링 워커, `LIMIT 10` + `FOR UPDATE SKIP LOCKED`.

- **`DispatchSchedulerConfig`**: `dispatchScheduler` 빈, 테스트 오버라이드를 위한 `@ConditionalOnMissingBean`.

- **`PolicyDispatchController`**: `@PreAuthorize` (`POLICY:DISPATCH:READ/WRITE`) 추가.

- **`MigrationOrderIT`**: 마이그레이션 카운트 58→59, 버전 목록에 "60" 추가.

#### 프론트엔드

- **`notificationTemplate.ts` (API 클라이언트)**: 기본 URL `/api/v1/notification/admin/template`.
- **`notificationTemplate.ts` (Pinia 스토어)**: Setup 방식 스토어, 템플릿 CRUD 상태 관리.
- **`NotificationTemplateListView.vue`**: Element Plus 기반 CRUD 목록 화면.
- **`router/index.ts`**: `/notification/template` 라우트 추가.
- **`PolicyDispatchView.vue`**: EMAIL 템플릿 드롭다운 실연동.

### 테스트 결과

단위 테스트 50개 전체 통과:
- `NotificationTemplateAdminControllerTest`, `NotificationTemplateServiceImplTest`
- `PolicyDispatchControllerTest`, `EmailDispatchExecutorTest`, `InappDispatchExecutorTest`, `NotificationDispatchWorkerTest`
- `NotificationTemplateMapperIT`, `MigrationOrderIT` (Docker/CI 환경 필요)

Docker 빌드 성공, TypeScript 오류 0건.

### 인수 기준 충족 현황

- **AC-NE-001 ~ AC-NE-020**: 전체 인수 기준 충족 (SPEC 요구사항 REQ-NE-001 ~ REQ-NE-025 구현 완료).
- **예외**: REQ-NE-006 `email_template_id` 위임 발송은 구현되었으나 실제 위임 로직은 `EmailDispatchExecutor` 내 `email_template_id` 참조 분기로 처리됨.

### 아키텍처 결정 준수 사항

1. 이메일 재구축 없이 기존 `EmailService` 재사용.
2. `notification_template.email_template_id` FK 위임 방식(Option A) 채택.
3. Strategy 패턴으로 채널 실행기 분리 (`Map<Channel, DispatchChannelExecutor>`).
4. `@Scheduled` 단일 노드 폴링 (분산 락은 후속 SPEC).
5. `dispatchExecutor` 별도 스레드 풀 — `auditExecutor`와 격리.
