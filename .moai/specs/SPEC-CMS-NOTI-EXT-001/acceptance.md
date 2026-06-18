# Acceptance Criteria — SPEC-CMS-NOTI-EXT-001

알림 기능 확장: 이메일 채널 발송 / 알림 템플릿 관리 / 발송 스케줄러

---

## 데이터베이스 / 마이그레이션

- **AC-NE-001**: V61 마이그레이션 적용 후 `notification_template` 테이블에 `subject`, `body_html`, `variables`, `language`, `is_active`, `email_template_id`, `created_by`, `updated_by`, `created_at`, `updated_at` 컬럼이 존재하고, 기존 `id` 컬럼과 `notification_dispatch_schedule.template_id` FK 제약이 유지된다.
- **AC-NE-002**: `(code, language)` 동일 쌍으로 두 번째 템플릿을 INSERT하면 UNIQUE 제약 위반으로 실패한다.
- **AC-NE-017**: V61 적용 후 `permissions` 테이블에 `NOTIFICATION_TEMPLATE:READ/WRITE/DELETE`, `DISPATCH:WRITE` 4개 코드가 존재하고 `SUPER_ADMIN`에 모두 매핑된다. 재적용(idempotent) 시 `ON CONFLICT DO NOTHING`으로 중복 없이 통과한다.

## 알림 템플릿 CRUD API

- **AC-NE-003**: `NOTIFICATION_TEMPLATE:WRITE` 권한 보유자가 `POST /api/v1/admin/notification-templates`로 신규 템플릿을 생성하면 201 응답과 함께 `created_by`가 요청자 ID로 설정된 행이 저장된다. 권한 미보유자는 403을 받는다.
- **AC-NE-004**: `GET /api/v1/admin/notification-templates?channel=EMAIL&isActive=true&page=0&size=20` 호출 시 필터가 적용된 페이지네이션 결과를 반환한다.
- **AC-NE-005**: `PENDING` 상태 dispatch 스케줄이 참조하는 템플릿을 `DELETE` 하면 409 Conflict를 반환하고 행은 삭제되지 않는다.
- **AC-NE-006**: `email_template_id`가 설정된 템플릿으로 EMAIL 발송 시, 발송된 제목/본문이 참조된 `email_template` 행의 값과 일치한다. `email_template_id`가 NULL이면 템플릿 자체의 `subject`/`body_html`이 사용된다.
- **AC-NE-007**: `POST /api/v1/admin/notification-templates/{id}/preview`에 샘플 변수를 전달하면 렌더링된 subject/body를 반환하고, `user_notification_inbox`/이메일 발송이 발생하지 않는다.

## 발송 워커

- **AC-NE-008**: `PENDING` 상태이고 `scheduled_at <= now()`인 스케줄 11건이 있을 때 워커 1회 폴링은 정확히 10건만 `PROCESSING`으로 전환하며, `priority DESC, scheduled_at ASC` 순서로 선택한다.
- **AC-NE-009**: INAPP 채널 발송 후 해당 수신자 행이 `user_notification_inbox`에 INSERT되고, `admin_notification`에는 어떤 행도 INSERT되지 않는다.
- **AC-NE-010**: EMAIL 채널 발송 시 로그/캡처에 평문 이메일 주소가 저장되지 않으며, `EmailService` 호출 직전 `EmailEncryptionService.decrypt()`가 호출된다.
- **AC-NE-011**: 동일 `(scheduleId, userId, dispatchType)`로 워커가 두 번 실행되어도 `notification_dispatch_target`에는 멱등성 키당 1행만 존재하고, 두 번째 발송은 스킵된다.
- **AC-NE-012**: 한 스케줄의 5개 대상 중 1개 대상의 채널 실행기가 예외를 던지면, 그 대상만 `FAILED`(+`error_message`)로 기록되고 나머지 4개는 정상 처리되며 스케줄은 `COMPLETED`가 된다.
- **AC-NE-013**: 한 스케줄의 모든 대상이 실패하면 스케줄 상태가 `FAILED`로 전환된다.
- **AC-NE-014**: 발송 워커가 사용하는 `TaskExecutor`/`ThreadPoolTaskScheduler` 빈이 `auditExecutor`와 다른 이름의 별도 빈으로 구성되어 있다.
- **AC-NE-015**: `notification_subscription`에서 EMAIL 옵트아웃한 사용자는 EMAIL 대상 행이 `SKIPPED_OPTOUT`이 되고 이메일이 전송되지 않는다.
- **AC-NE-020**: dispatch 시점에 템플릿 해석이 실패해도 EMAIL은 하드코딩 폴백 본문으로 전송되며, 예외가 전체 스케줄을 중단시키지 않는다.

## 권한 보강

- **AC-NE-016**: `DISPATCH:WRITE` 권한(또는 `SUPER_ADMIN`) 없이 `POST /api/v1/policy/admin/dispatch/schedules` 및 `.../{id}/trigger`, `.../{id}/cancel` 호출 시 403을 반환한다.

## 프론트엔드

- **AC-NE-018**: 권한별로 `NotificationTemplateListView.vue`의 생성/수정/삭제/미리보기 버튼이 활성/비활성 처리된다.
- **AC-NE-019**: `PolicyDispatchView.vue` 신규 예약 다이얼로그의 템플릿 드롭다운이 실제 `/api/v1/admin/notification-templates` 응답으로 채워지며, 하드코딩/빈 목록이 아니다.
