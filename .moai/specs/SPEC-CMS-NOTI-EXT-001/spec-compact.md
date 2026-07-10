# SPEC-CMS-NOTI-EXT-001 Compact

알림 기능 확장 — 이메일 채널 발송 / 알림 템플릿 관리 / 발송 스케줄러 | TDD | V61

## 핵심 문제
- `notification_template`: V16 스텁(5컬럼), Java 엔티티/CRUD/UI 전무
- dispatch 워커 부재 → 전체 파이프라인 무동작 (`triggerNow()`가 PROCESSING 전환만)
- `PolicyDispatchController` `@PreAuthorize` 누락 → 보안 공백

## IN SCOPE
EMAIL 채널 발송 (EmailService 재사용), notification_template CRUD API, NotificationDispatchWorker(@Scheduled 60s LIMIT 10), DispatchChannelExecutor 전략패턴, PolicyDispatchController 권한 보강, V61 마이그레이션, 프론트엔드(NotificationTemplateListView + PolicyDispatchView 드롭다운)

## OUT OF SCOPE
PUSH/FCM, KAKAO, SMS, email_template 구조 변경, admin_notification 발송 대상 포함

## V61 마이그레이션
```sql
ALTER TABLE notification_template ADD COLUMN IF NOT EXISTS subject VARCHAR(300), body_html TEXT, variables JSONB DEFAULT '[]', language VARCHAR(10) NOT NULL DEFAULT 'ko', is_active BOOLEAN NOT NULL DEFAULT TRUE, email_template_id BIGINT REFERENCES email_template(id) ON DELETE SET NULL, created_by BIGINT, updated_by BIGINT, created_at TIMESTAMPTZ DEFAULT now(), updated_at TIMESTAMPTZ DEFAULT now();
DROP CONSTRAINT notification_template_code_key;
CREATE UNIQUE INDEX ux_notification_template_code_lang ON notification_template (code, language);
INSERT INTO permissions ... ('NOTIFICATION_TEMPLATE:READ', 'NOTIFICATION_TEMPLATE:WRITE', 'NOTIFICATION_TEMPLATE:DELETE', 'DISPATCH:WRITE') ON CONFLICT DO NOTHING;
```

## API (6 endpoints)
```
GET/POST/PUT/DELETE /api/v1/admin/notification-templates
POST /api/v1/admin/notification-templates/{id}/preview
+@PreAuthorize: POST/...trigger/...cancel /api/v1/policy/admin/dispatch/schedules
```

## 발송 흐름
`@Scheduled(60s)` → claim 10 PENDING schedules → per channel: EmailDispatchExecutor(EmailService+decrypt) | InappDispatchExecutor(user_notification_inbox ONLY) → idempotency(SHA-256) → target status: SENT/FAILED/SKIPPED_OPTOUT → schedule: COMPLETED/FAILED

## Key constraints
- MyBatis only (no JPA)
- INAPP → user_notification_inbox (V35), NEVER admin_notification (V40)
- EMAIL → EmailEncryptionService.decrypt() before send, no plaintext log
- dispatchExecutor 빈 != auditExecutor
- email_template_id FK → delegate to email_template; null → use own subject/body_html

## 파일 (24개)
백엔드신규: V61.sql, NotificationTemplate entity+mapper+service+controller, DispatchChannelExecutor+EmailDispatchExecutor+InappDispatchExecutor, NotificationDispatchWorker, DispatchSchedulerConfig
백엔드수정: PolicyDispatchController(@PreAuthorize), ScheduleMapper(claim), TargetMapper(idempotent INSERT)
FE신규: api/notificationTemplate.ts, stores/notificationTemplate.ts, NotificationTemplateListView.vue
FE수정: router, PolicyDispatchView(dropdown)

## 레퍼런스 구현
EmailTemplateAdminController(CRUD패턴), QnaNotificationServiceImpl(INAPP+EMAIL이중발송), EmailServiceImpl(@Async+폴백), PolicyDispatchServiceImpl(멱등키)

## AC 요약 (20개)
AC-NE-001~002(V61스키마), AC-NE-003~007(템플릿CRUD+권한+409+미리보기), AC-NE-008~015(워커: claim10, INAPP대상, PII, 멱등, 부분실패, 전체실패, 스레드풀, 옵트아웃), AC-NE-016(dispatch403), AC-NE-017(권한시드), AC-NE-018~019(FE권한게이트+드롭다운), AC-NE-020(발송폴백)
