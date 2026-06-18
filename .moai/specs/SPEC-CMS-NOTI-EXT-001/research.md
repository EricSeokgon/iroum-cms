# SPEC-CMS-NOTI-EXT-001 — 알림 기능 확장 Phase 0.5 Research

**작성일**: 2026-06-18
**작성자**: Explore subagent (Phase 0.5 Deep Research)
**대상 SPEC**: SPEC-CMS-NOTI-EXT-001

---

## 1. Architecture Overview

### 프로젝트 기술 스택

- **Backend**: Spring Boot 3.3, Java 21, MyBatis (JPA 미사용)
- **Frontend**: Vue 3 + Pinia + Element Plus + TypeScript
- **Database**: PostgreSQL (BIGSERIAL PK, JSONB 컬럼, 부분 인덱스)
- **Migration**: Flyway (현재 V59까지 적용)
- **Build**: Gradle Kotlin DSL
- **Security**: Spring Security + JWT, `@PreAuthorize` 권한 제어

### 알림 도메인 전체 구조

현재 알림 관련 도메인은 다음 4개 영역으로 분산되어 있다:

```
kr.co.ircp.cms.domain/
├── notification/
│   ├── admin/         → AdminNotification (관리자 인앱 받은편지함, V40)
│   └── stat/          → 알림 발송 통계 (user_notification_inbox 집계)
├── board/
│   └── entity/        → UserNotificationInbox (사용자 인앱 받은편지함, V35)
├── auth/service/      → EmailService/EmailServiceImpl (SMTP 이메일 발송)
├── email/template/    → EmailTemplate, EmailTemplateAdminController (V55, CRUD)
├── policy/dispatch/   → NotificationDispatchSchedule, NotificationDispatchTarget (V16)
└── audit/notification/→ CriticalAuditNotifier (CRITICAL 감사이벤트 인메모리 큐)
```

### 핵심 흐름 (현재)

1. **사용자 INAPP**: 이벤트 발생 → `UserNotificationInbox` 레코드 생성 (직접 INSERT)
2. **관리자 INAPP**: 이벤트 발생 → `AdminNotification` 레코드 생성 (직접 INSERT)
3. **이메일 발송**: `EmailServiceImpl.send*()` → `@Async("auditExecutor")` → `JavaMailSender` → `EmailTemplateResolver` → SMTP 전송
4. **정책 발송 예약**: `PolicyDispatchServiceImpl.create()` → `notification_dispatch_schedule` 레코드 생성 (실제 발송 미구현)

---

## 2. Existing Notification Domain Classes

### 2.1 관리자 인앱 알림 (`admin_notification`, V40)

**파일**: `domain/notification/admin/entity/AdminNotification.java`

| 필드 | 타입 | 설명 |
|------|------|------|
| id | BIGSERIAL | PK |
| adminUserId | BIGINT | 수신 관리자 |
| type | VARCHAR(80) | 알림 유형 (자유 문자열) |
| severity | ENUM | INFO / WARN / ERROR |
| title | VARCHAR(200) | 알림 제목 |
| body | TEXT | 알림 본문 (nullable) |
| refType | VARCHAR(80) | 딥링크 엔티티 유형 (nullable) |
| refId | BIGINT | 딥링크 엔티티 ID (nullable) |
| status | ENUM | UNREAD / READ / ARCHIVED |
| readAt | TIMESTAMPTZ | 읽음 처리 일시 |
| archivedAt | TIMESTAMPTZ | 보관 처리 일시 |
| createdAt | TIMESTAMPTZ | 생성 일시 |

**컨트롤러**: `AdminNotificationController`
- `GET /api/v1/admin/notifications` — 목록 (status, severity, type, 날짜 범위, 페이지)
- `PATCH /api/v1/admin/notifications/{id}/read`
- `PATCH /api/v1/admin/notifications/read-all`
- `PATCH /api/v1/admin/notifications/{id}/archive`
- `GET /api/v1/admin/notifications/unread-count`
- 권한: `SUPER_ADMIN`, `CONTENT_ADMIN`, `ADMIN` 역할 (JWT 사용자 격리)

### 2.2 사용자 INAPP 받은편지함 (`user_notification_inbox`, V35)

**파일**: `domain/board/entity/UserNotificationInbox.java`

| 필드 | 타입 | 설명 |
|------|------|------|
| id | BIGSERIAL | PK |
| userId | BIGINT | 수신 사용자 |
| type | VARCHAR | 알림 유형 |
| title | VARCHAR | 알림 제목 |
| body | TEXT | 알림 본문 |
| refId | BIGINT | 딥링크 ID (nullable) |
| refType | VARCHAR | 딥링크 유형 (nullable) |
| read | BOOLEAN | 읽음 여부 |
| readAt | TIMESTAMPTZ | 읽음 일시 |
| createdAt | TIMESTAMPTZ | 생성 일시 |

**V46 추가**: `delivery_status` 컬럼 (SENT/FAILED/PENDING, nullable). NULL = SENT (기존 행 backfill 없음)

**채널 없음**: 테이블에 channel 컬럼이 없어 INAPP 전용임이 묵시적으로 고정되어 있다.

### 2.3 이메일 서비스 (`EmailService` / `EmailServiceImpl`)

**파일**: `domain/auth/service/EmailServiceImpl.java`

```java
// 모든 메서드는 @Async("auditExecutor") — fire-and-forget, 실패 시 예외 미전파
void sendOtp(String encryptedEmail, String otp);
void sendPasswordResetNotice(String encryptedEmail);
void sendApprovalConfirmed(String encryptedEmail, String applicantName);
void sendApprovalRejected(String encryptedEmail, String applicantName, String reason);
```

**특이사항**:
- `EmailTemplateResolver`를 통해 `email_template` 테이블에서 템플릿 조회 (code+language로)
- 조회 실패 시 하드코딩 폴백 텍스트로 전송 (가용성 우선)
- PII: `EmailEncryptionService.decrypt(encryptedEmail)` 후 발송

### 2.4 이메일 템플릿 관리 (`email_template`, V55)

**파일**: `domain/email/template/admin/entity/EmailTemplate.java`

| 필드 | 타입 | 설명 |
|------|------|------|
| id | BIGSERIAL | PK |
| code | VARCHAR(80) | 템플릿 코드 |
| name | VARCHAR(200) | 템플릿 이름 |
| templateType | ENUM | OTP / QNA_ANSWER / PASSWORD_RESET / ADMIN_NOTIFICATION / CUSTOM |
| language | VARCHAR(10) | 언어 코드 (ko, en 등) |
| subject | VARCHAR(300) | 이메일 제목 |
| bodyHtml | TEXT | HTML 본문 |
| bodyText | TEXT | Plain text 본문 |
| variables | JSONB | 변수 정의 목록 (`List<Map>`) |
| isActive | BOOLEAN | 활성 여부 |
| createdBy | BIGINT FK | 작성자 |
| updatedBy | BIGINT FK | 수정자 |
| createdAt | TIMESTAMPTZ | |
| updatedAt | TIMESTAMPTZ | |

UNIQUE 제약: `(code, language)`

**컨트롤러**: `EmailTemplateAdminController` (`@MX:ANCHOR`)
- `GET /api/v1/admin/email-templates` — `EMAIL_TEMPLATE:READ`
- `GET /api/v1/admin/email-templates/{id}` — `EMAIL_TEMPLATE:READ`
- `POST /api/v1/admin/email-templates` — `EMAIL_TEMPLATE:WRITE`
- `PUT /api/v1/admin/email-templates/{id}` — `EMAIL_TEMPLATE:WRITE`
- `DELETE /api/v1/admin/email-templates/{id}` — `EMAIL_TEMPLATE:DELETE`
- `POST /api/v1/admin/email-templates/{id}/preview` — `EMAIL_TEMPLATE:WRITE`
- `POST /api/v1/admin/email-templates/{id}/test-send` — `EMAIL_TEMPLATE:WRITE` (본인 이메일 고정)
- `GET /api/v1/admin/email-templates/{id}/send-logs` — `EMAIL_TEMPLATE:READ`

### 2.5 정책 발송 스케줄 (`notification_dispatch_schedule`, V16)

**파일**: `domain/policy/dispatch/entity/NotificationDispatchSchedule.java`

| 필드 | 타입 | 설명 |
|------|------|------|
| id | BIGSERIAL | PK |
| scheduleUuid | UUID | 외부 UUID |
| policyId | BIGINT FK | 연결 정책 |
| dispatchType | ENUM | APPLICATION_OPEN / CLOSING_SOON / RESULT / REMINDER / ANNOUNCEMENT |
| targetFilter | JSONB | 수신 대상 필터 조건 |
| scheduledAt | TIMESTAMPTZ | 예약 발송 일시 (야간 차단 적용) |
| channels | JSONB(List) | KAKAO / EMAIL / SMS / INAPP |
| templateId | BIGINT FK | `notification_template.id` 참조 |
| priority | INT | 우선순위 |
| status | ENUM | PENDING / PROCESSING / COMPLETED / CANCELLED / FAILED |
| createdBy | BIGINT FK | 작성자 |
| createdAt | TIMESTAMPTZ | |
| startedAt | TIMESTAMPTZ | |
| completedAt | TIMESTAMPTZ | |

**컨트롤러**: `PolicyDispatchController`
- `GET /api/v1/policy/admin/dispatch/schedules`
- `POST /api/v1/policy/admin/dispatch/schedules`
- `POST /api/v1/policy/admin/dispatch/schedules/{id}/trigger`
- `POST /api/v1/policy/admin/dispatch/schedules/{id}/cancel`
- **보안 취약점**: `@PreAuthorize` 어노테이션 없음 — 권한 검증 누락 가능

### 2.6 발송 대상 (`notification_dispatch_target`, V16)

**파일**: `domain/policy/dispatch/entity/NotificationDispatchTarget.java`

| 필드 | 타입 | 설명 |
|------|------|------|
| id | BIGSERIAL | PK |
| scheduleId | BIGINT FK | 부모 스케줄 |
| userId | BIGINT FK | 수신 사용자 |
| channel | VARCHAR | KAKAO / EMAIL / SMS / INAPP |
| status | ENUM | PENDING / SENT / FAILED / SKIPPED_OPTOUT / CANCELLED |
| idempotencyKey | VARCHAR | SHA-256(scheduleId \|\| userId \|\| dispatchType) |
| sentAt | TIMESTAMPTZ | 발송 일시 |
| errorMessage | TEXT | 실패 오류 메시지 |

### 2.7 알림 구독 (`notification_subscription`, V16)

사용자별 채널+카테고리 수신 동의 테이블. 채널: KAKAO / EMAIL / SMS / INAPP.

### 2.8 QnA 알림 서비스 (`QnaNotificationServiceImpl`)

**파일**: `domain/board/service/QnaNotificationServiceImpl.java`

INAPP + EMAIL 이중 발송의 레퍼런스 구현:
- INAPP: 무조건 발송 (옵트아웃 없음)
- EMAIL: `qna_notification_optout` 조회 → 미설정 시 발송
- 이메일 코드 `"QNA_ANSWER"` → `EmailTemplateResolver` → `email_template` 조회
- 재시도 3회 → `DEAD_LETTER` 상태
- PII: `EmailEncryptionService.decrypt()` 사용

### 2.9 알림 통계 (`NotificationStatServiceImpl`)

`user_notification_inbox`를 단일 모수로 집계:
- 오늘/7일/30일 발송 건수, 읽기율, 미읽음 수, 오류 수
- 일별 추이 (최대 90일 캡)
- 실패 알림 재발송: `delivery_status = 'SENT'`로 직접 업데이트 (실제 재발송 로직 없음)
- KPI 피드 연동 (graceful degradation: kpi_value 미배포 시 warn 후 계속)

### 2.10 CRITICAL 감사 알림 (`CriticalAuditNotifier`)

- `@MX:ANCHOR`, `@MX:WARN` (무제한 큐 증가 위험)
- 인메모리 `ConcurrentLinkedQueue`에 CRITICAL 이벤트 push
- GET `/api/v1/system/audit-logs/critical`에서 드레인
- 멀티노드/SMTP 연동은 후속 SPEC으로 명시됨

---

## 3. Database Schema (existing tables)

### 알림 관련 테이블 전체 목록

| 테이블명 | 마이그레이션 | 상태 | 설명 |
|----------|-------------|------|------|
| `user_notification_inbox` | V35 | 완성 | 사용자 INAPP 받은편지함 |
| `notification_subscription` | V16 | 완성 | 사용자 채널별 수신 동의 |
| `notification_dispatch_schedule` | V16 | 완성 | 정책 발송 예약 |
| `notification_dispatch_target` | V16 | 완성 | 발송 대상 (채널별) |
| `notification_template` | V16 STUB | **불완전** | 스텁만 존재, SPEC-CMS-004 ALTER 미적용 |
| `admin_notification` | V40 | 완성 | 관리자 INAPP 받은편지함 |
| `qna_notification_optout` | V21 | 완성 | QnA 이메일/카카오/SMS 옵트아웃 |
| `qna_notification_log` | V21 | 완성 | QnA 알림 발송 로그 (재시도/DEAD_LETTER) |
| `email_template` | V55 | 완성 | 이메일 HTML/text 템플릿 |
| `smtp_config` | V55 | 완성 | SMTP 동적 설정 (암호화된 패스워드) |
| `email_template_send_log` | V56 | 완성 | 이메일 템플릿 테스트발송 로그 |

### `notification_template` V16 스텁 스키마

```sql
CREATE TABLE IF NOT EXISTS notification_template (
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(80)  NOT NULL UNIQUE,
    name        VARCHAR(200) NOT NULL,
    channel     VARCHAR(20)  NOT NULL,   -- KAKAO/EMAIL/SMS/INAPP
    body_template TEXT        NOT NULL,
    review_status VARCHAR(20) DEFAULT 'DRAFT'
);
-- COMMENT: "정식 컬럼은 SPEC-CMS-004 실 마이그에서 ALTER 적용"
```

`notification_dispatch_schedule.template_id`가 이 테이블을 FK 참조한다.
현재 채널별 본문만 있고 이메일 전용 subject, HTML body, variables 등이 없다.

### 인덱스 패턴

- `admin_notification`: `(admin_user_id, status, created_at DESC)`, 부분 인덱스 `WHERE status = 'UNREAD'`
- `qna_notification_log`: 멱등성 인덱스 `(qna_id, user_id, channel)` UNIQUE (retry 방지)
- `user_notification_inbox`: 기본 인덱스

---

## 4. Email/Push Infrastructure Gap Analysis

### 4.1 이메일 (EMAIL) — 현황과 갭

**현황**: `JavaMailSender` + `EmailTemplateResolver` 완성 구현 존재

**활용 가능한 기반**:
- `EmailService` 인터페이스 + `EmailServiceImpl` (auth 도메인)
- `email_template` / `smtp_config` 테이블
- `EmailEncryptionService` (PII 복호화)
- `@Async("auditExecutor")` 패턴 (비동기 발송)

**갭**:
1. `notification_dispatch_schedule`의 EMAIL 채널 발송 로직이 없음
2. `email_template` (V55) ↔ `notification_template` (V16 stub) 연결이 없음
3. 발송 이력 테이블이 `email_template_send_log`(테스트 발송 전용)와 `qna_notification_log`로 분산

### 4.2 PUSH 알림 — 현황과 갭

**현황**: 존재하지 않음

- `build.gradle.kts`에 FCM/Firebase 의존성 없음
- 애플리케이션 코드 전체에 FCM/push 관련 코드 없음
- `notification_subscription` 테이블에 push 토큰 저장 컬럼 없음
- `notification_dispatch_target.channel` CHECK 제약에 PUSH/FCM 없음

**PUSH 구현 시 필요한 요소**:
1. `firebase-admin` 또는 `google.firebase:firebase-admin` dependency 추가
2. FCM 서비스 계정 키 관리 (환경변수/Secret)
3. 사용자별 FCM device token 저장 테이블 (V61+)
4. `notification_subscription` 또는 별도 테이블 확장
5. `PushNotificationService` 인터페이스 + 구현체
6. `notification_dispatch_target.channel` CHECK 제약 확장 (ALTER TABLE)

**구현 난이도 평가**: PUSH는 이메일 대비 인프라 부재가 심각해 별도 SPEC으로 분리 권장.

### 4.3 KAKAO / SMS — 현황과 갭

- `notification_dispatch_target.channel`에 KAKAO, SMS 값이 정의되어 있으나 구현 없음
- 외부 API 연동 (카카오 비즈니스 API, AWS SNS/Twilio 등) 필요
- 이 SPEC 범위에서 제외 권장

---

## 5. Template Management Analysis

### 5.1 이메일 템플릿 (`email_template`) — 완성 구현

`EmailTemplateAdminController`는 SPEC-CMS-EMAIL-TEMPLATE-001 기반의 완성된 CRUD이다.

- **프론트엔드**: `EmailTemplateListView.vue` + `email-template.ts` API 래퍼 존재
- **라우터**: `system/email-templates` → `email-template-list` 이름으로 등록
- **권한**: `EMAIL_TEMPLATE:READ/WRITE/DELETE` (V57 시드)
- **SUPER_ADMIN**: 모든 이메일 템플릿 권한 자동 부여 (V57)

### 5.2 알림 템플릿 (`notification_template`) — 미완성 스텁

**V16 스텁 현황**:
- 컬럼 5개 (id, code, name, channel, body_template, review_status)
- CRUD API 없음 (Java 엔티티 클래스 없음)
- 프론트엔드 UI 없음
- `notification_dispatch_schedule.template_id` FK로만 참조됨

**설계 문제**: 현재 스텁은 `channel` 컬럼 하나에 하나의 채널만 매핑된다. 
멀티채널(EMAIL+INAPP 동시)을 지원하려면 템플릿이 채널별로 분리되거나 JSONB 구조로 변경이 필요하다.

### 5.3 email_template vs notification_template 관계

두 테이블은 목적이 다르다:

| | `email_template` | `notification_template` |
|--|---|---|
| 용도 | 이메일 전용 (HTML/plain) | 다채널 메시지 본문 |
| 완성도 | 완성 (CRUD+권한+UI) | 스텁 |
| 참조처 | EmailTemplateResolver | notification_dispatch_schedule.template_id |
| 변수 | JSONB variables | 없음 (스텁) |

**설계 선택지**:
1. **Option A (권장)**: `notification_template`에 `email_template_id` FK 컬럼 추가 → 이메일 채널은 기존 email_template을 재사용
2. **Option B**: `notification_template`를 email_template에 준하는 풀 구조로 ALTER 확장

Option A가 기존 email_template의 완성된 CRUD/권한 시스템을 재활용할 수 있어 구현 비용이 낮다.

---

## 6. Scheduled Sending Analysis

### 6.1 현재 스케줄 관리 구조

`PolicyDispatchServiceImpl`이 스케줄 CRUD를 담당:
- `create()`: 야간 차단 (KST 21:00~08:00 → 익일 09:00 자동 조정)
- `triggerNow()`: `status = PROCESSING` 으로 변경 (실제 발송 없음)
- `cancel()`: `status = CANCELLED` 로 변경
- 멱등성 키 생성: `SHA-256(scheduleId || userId || dispatchType)`

### 6.2 핵심 갭: 실제 발송 로직 없음

`triggerNow()` 이후 PROCESSING 상태에서 실제 알림을 발송하는 백그라운드 워커가 없다.
현재 발송 체계는 다음과 같이 실행되지 않는 코드이다:

```
PENDING → PROCESSING (triggerNow 호출)
               ↓
         [워커 없음 — 여기서 멈춤]
```

### 6.3 발송 워커 구현 전략

현재 프로젝트에는 Spring Batch, Quartz, `@Scheduled` 활용 코드가 있는지 확인이 필요하다.
단순 `@Scheduled` 폴링 방식이 추가 인프라 없이 최소 구현 가능하다:

```
@Scheduled(fixedDelay = 60_000)   // 60초마다
→ SELECT * FROM notification_dispatch_schedule
    WHERE status = 'PENDING' AND scheduled_at <= now()
    ORDER BY priority DESC, scheduled_at ASC
    LIMIT 10
→ 채널별 DispatchExecutor 위임
→ notification_dispatch_target INSERT (멱등성 키 충돌 시 SKIP)
→ status = COMPLETED/FAILED 업데이트
```

### 6.4 야간 차단 로직 (기존 구현)

```java
// KST 21:00 이후 → 익일 09:00 KST로 자동 조정
private ZonedDateTime adjustForNighttime(ZonedDateTime scheduledAt) {
    ZoneId kst = ZoneId.of("Asia/Seoul");
    ZonedDateTime kstTime = scheduledAt.withZoneSameInstant(kst);
    int hour = kstTime.getHour();
    if (hour >= 21 || hour < 8) {
        return kstTime.toLocalDate().plusDays(hour >= 21 ? 1 : 0)
                .atTime(9, 0).atZone(kst);
    }
    return scheduledAt;
}
```

### 6.5 프론트엔드 발송 예약 UI

`PolicyDispatchView.vue` — 발송 예약 관리 화면 존재:
- 필터: status 선택
- 테이블: 정책명, 유형, 채널, 예약시각, 대상수, 상태
- 액션: 즉시 발송, 취소
- 라우터: `admin/policy/dispatch` → `policy-dispatch`

**갭**: 새 예약 생성 다이얼로그에서 `notification_template` 선택 드롭다운이 있지만 API와 연동이 되어 있지 않을 가능성 높음 (스텁 테이블이라 템플릿 목록 API 없음).

---

## 7. Frontend Components

### 7.1 알림 관련 기존 Vue 컴포넌트

| 컴포넌트 경로 | 설명 | 상태 |
|-------------|------|------|
| `views/notifications/NotificationCenterView.vue` | 관리자 INAPP 알림 센터 | 완성 |
| `views/system/EmailTemplateListView.vue` | 이메일 템플릿 목록/CRUD | 완성 |
| `views/policy/PolicyDispatchView.vue` | 발송 예약 관리 | 부분 완성 |
| `api/adminNotifications.ts` | 관리자 알림 API 래퍼 | 완성 |
| `api/email-template.ts` | 이메일 템플릿 API 래퍼 | 완성 |
| `stores/notificationCenter.ts` | 알림 센터 Pinia 스토어 | 완성 |
| `router/notificationDeepLink.ts` | 알림 딥링크 라우팅 | 완성 |

### 7.2 NotificationCenterView 구조

- 필터: status (UNREAD/READ/ARCHIVED), severity (INFO/WARN/ERROR), 날짜 범위
- 테이블: severity tag, title+body, type, status, 생성일시, 액션(읽음/보관)
- 페이지네이션: El-pagination (0-base 서버 ↔ 1-base UI 변환)
- 딥링크: 행 클릭 → `resolveNotificationDeepLink(refType, refId)` → router push
- a11y: `aria-live="polite"` 영역 (KWCAG REQ-NC-013-2)

### 7.3 PolicyDispatchView 구조

- 필터: status 선택
- 테이블: 정책명, 발송유형, 채널(멀티태그), 예약시각, 대상수, 상태
- 액션: 즉시 발송 (PENDING → 버튼), 취소
- 새 예약 생성 다이얼로그 (코드 80줄 이후 미확인 — 일부 미구현 가능)

### 7.4 신규 개발 필요 컴포넌트

| 컴포넌트 | 목적 | 우선순위 |
|---------|------|---------|
| `views/notification/NotificationTemplateListView.vue` | 알림 템플릿 CRUD UI | Priority High |
| `api/notificationTemplate.ts` | 알림 템플릿 API 래퍼 | Priority High |
| `stores/notificationTemplate.ts` | 알림 템플릿 Pinia 스토어 | Priority High |
| PolicyDispatchView 수정 | 템플릿 선택 드롭다운 실제 연동 | Priority Medium |

---

## 8. Security/RBAC Analysis

### 8.1 현재 권한 체계

권한은 `permissions` 테이블에 `resource:action` 형식으로 등록:

| 권한 코드 | 대상 |
|----------|------|
| `EMAIL_TEMPLATE:READ` | 이메일 템플릿 조회 |
| `EMAIL_TEMPLATE:WRITE` | 이메일 템플릿 등록/수정 |
| `EMAIL_TEMPLATE:DELETE` | 이메일 템플릿 삭제 |

SUPER_ADMIN → 모든 EMAIL_TEMPLATE 권한 자동 부여 (V57)

### 8.2 알림 템플릿 권한 미정의

`notification_template`에 대한 권한 코드가 없다. 신규 정의 필요:
- `NOTIFICATION_TEMPLATE:READ`
- `NOTIFICATION_TEMPLATE:WRITE`
- `NOTIFICATION_TEMPLATE:DELETE`

### 8.3 PolicyDispatchController 보안 취약점

`PolicyDispatchController`에 `@PreAuthorize` 어노테이션이 없다.
현재 JWT 인증만 통과하면 모든 관리자가 발송 스케줄을 생성/트리거할 수 있다.
최소한 `SUPER_ADMIN` 또는 전용 `DISPATCH:WRITE` 권한 검증이 필요하다.

### 8.4 PII 처리 패턴

이메일 주소는 `EmailEncryptionService`로 암호화 저장:
- `User.email` → 암호화된 값 저장
- 발송 시 `emailEncryptionService.decrypt(encryptedEmail)` 수행
- 새로운 채널 구현 시 동일 패턴 준수 필수

---

## 9. API Endpoint Map (existing)

### 알림 관련 현재 API 전체

```
[관리자 INAPP]
GET    /api/v1/admin/notifications              → 목록
PATCH  /api/v1/admin/notifications/{id}/read   → 읽음
PATCH  /api/v1/admin/notifications/read-all    → 일괄 읽음
PATCH  /api/v1/admin/notifications/{id}/archive → 보관
GET    /api/v1/admin/notifications/unread-count → 미읽음 수

[이메일 템플릿]
GET    /api/v1/admin/email-templates            → 목록
GET    /api/v1/admin/email-templates/{id}       → 상세
POST   /api/v1/admin/email-templates            → 생성
PUT    /api/v1/admin/email-templates/{id}       → 수정
DELETE /api/v1/admin/email-templates/{id}       → 삭제
POST   /api/v1/admin/email-templates/{id}/preview → 미리보기
POST   /api/v1/admin/email-templates/{id}/test-send → 테스트 발송
GET    /api/v1/admin/email-templates/{id}/send-logs → 발송 로그

[발송 예약]
GET    /api/v1/policy/admin/dispatch/schedules           → 목록
POST   /api/v1/policy/admin/dispatch/schedules           → 생성
POST   /api/v1/policy/admin/dispatch/schedules/{id}/trigger → 즉시 발송
POST   /api/v1/policy/admin/dispatch/schedules/{id}/cancel  → 취소

[알림 통계]
GET    /api/v1/admin/notifications/stat/summary    → 요약 (오늘/7일/30일)
GET    /api/v1/admin/notifications/stat/category   → 카테고리별
GET    /api/v1/admin/notifications/stat/daily      → 일별 추이
GET    /api/v1/admin/notifications/stat/errors     → 실패 목록
POST   /api/v1/admin/notifications/stat/errors/{id}/resend → 재발송(플레이스홀더)
```

### 신규 필요 API

```
[알림 템플릿 CRUD — 신규]
GET    /api/v1/admin/notification-templates
GET    /api/v1/admin/notification-templates/{id}
POST   /api/v1/admin/notification-templates
PUT    /api/v1/admin/notification-templates/{id}
DELETE /api/v1/admin/notification-templates/{id}
POST   /api/v1/admin/notification-templates/{id}/preview
```

---

## 10. Reference Implementations

### 10.1 이메일 + INAPP 이중 발송 — QnA 알림 패턴

`QnaNotificationServiceImpl`은 신규 기능 구현의 레퍼런스이다.

```java
// 패턴 요약:
// 1. INAPP: 무조건 발송 (옵트아웃 없음)
//    → userNotificationInboxMapper.insert(...)
// 2. EMAIL: 옵트아웃 조회 후 조건부 발송
//    → if (!qnaNotificationOptoutMapper.exists(userId, "EMAIL")) {
//         emailService.sendQnaAnswer(encryptedEmail, qnaTitle);
//    }
// 3. 재시도: 3회 실패 → qna_notification_log.status = DEAD_LETTER
// 4. 비동기: @Async("auditExecutor")
```

### 10.2 이메일 템플릿 + 하드코딩 폴백 패턴 (`EmailServiceImpl`)

```java
// EmailTemplateResolver.resolve(code, language) 조회
// → 성공: 템플릿으로 렌더링
// → 실패(TemplateNotFoundException): 하드코딩 텍스트로 폴백
// → 모든 예외 catch → log.error() 후 무시 (가용성 우선)
```

### 10.3 멱등성 키 패턴 (`PolicyDispatchServiceImpl`)

```java
String idempotencyKey = DigestUtils.sha256Hex(
    scheduleId + "|" + userId + "|" + dispatchType
);
// notification_dispatch_target 에 UNIQUE 제약으로 중복 방지
```

### 10.4 PII 이메일 처리 패턴

```java
// 저장: user.getEmail() = AES-256 암호화된 문자열
// 발송 직전 복호화:
EncryptedEmail encrypted = EncryptedEmail.of(user.getEmail());
String plainEmail = emailEncryptionService.decrypt(encrypted);
```

### 10.5 야간 발송 차단 패턴 (`PolicyDispatchServiceImpl`)

KST 21:00~08:00 범위에 scheduledAt이 있으면 익일 09:00 KST로 자동 조정.
`ZoneId.of("Asia/Seoul")` 기준.

---

## 11. Risks and Constraints

### 11.1 Critical Risk — notification_template 스텁 스키마

- `notification_dispatch_schedule.template_id`가 이미 `notification_template.id`를 FK 참조 중
- ALTER TABLE로 컬럼 추가 시 기존 FK 제약과의 호환성 검토 필요
- V16 이후 마이그레이션에서 이 테이블에 데이터가 없으면 영향 없음

### 11.2 High Risk — 발송 워커 없음

발송 스케줄러 구현이 없으면 전체 dispatch 시스템이 동작하지 않는다.
첫 번째 구현으로 `@Scheduled` 폴링을 권장하되, 스케일 요건에 따라 Spring Batch 도입을 검토해야 한다.

### 11.3 High Risk — PolicyDispatchController 권한 누락

현재 `@PreAuthorize` 없이 JWT 인증된 모든 관리자가 발송을 트리거할 수 있다.
발송 워커 구현과 동시에 권한 제어 추가가 필수이다.

### 11.4 Medium Risk — email_template vs notification_template 이원화

발송 예약이 `notification_template`을 참조하고, 실제 이메일은 `email_template`을 사용하는 구조가 혼재한다.
명시적 연결 컬럼 또는 통합 전략이 없으면 유지보수 복잡도가 높아진다.

### 11.5 Medium Risk — PUSH 인프라 부재

FCM 의존성, device token 저장, push service 클래스가 모두 없다.
이 SPEC에서 PUSH를 스코프에 포함하면 구현 부하가 크게 증가한다.
별도 SPEC 분리를 권장한다.

### 11.6 Low Risk — NotificationStatService.resend() 플레이스홀더

`resend()` 메서드가 `delivery_status = 'SENT'`로만 업데이트하고 실제 재발송을 하지 않는다.
통계 패널 재발송 기능이 필요하면 실제 발송 로직 연결이 필요하다.

### 11.7 구현 제약사항

- **MyBatis 전용**: JPA Repository 없음. 모든 DB 접근은 Mapper XML + 인터페이스로 작성
- **비동기 스레드 풀**: `auditExecutor`가 현재 이메일 발송에 사용됨. 발송 워커는 별도 스레드 풀 권장
- **암호화**: 이메일 PII는 반드시 `EmailEncryptionService` 통해 처리
- **UNIQUE 제약**: `email_template (code, language)` — 알림 템플릿 설계 시 동일 패턴 적용 권장
- **Flyway 버전**: 다음 마이그레이션은 V61부터 시작

---

## 12. Recommended Implementation Approach

### 12.1 스코프 분리 권장

| 기능 | 이 SPEC (SPEC-CMS-NOTI-EXT-001) | 별도 SPEC |
|-----|-------------------------------|----------|
| 이메일 채널 발송 구현 | O | |
| notification_template CRUD | O | |
| 발송 스케줄러 워커 | O | |
| PolicyDispatchController 권한 수정 | O | |
| PUSH (FCM) 알림 | | SPEC-CMS-NOTI-PUSH-001 |
| KAKAO/SMS 연동 | | SPEC-CMS-NOTI-KAKAO-001 |

### 12.2 Priority High — notification_template 스키마 확장 (V61 마이그레이션)

```sql
-- V61: notification_template 정식 확장 (SPEC-CMS-NOTI-EXT-001)
ALTER TABLE notification_template
  ADD COLUMN subject          VARCHAR(300),           -- 이메일 제목 (EMAIL 채널용)
  ADD COLUMN body_html        TEXT,                   -- HTML 본문 (EMAIL 채널용)
  ADD COLUMN variables        JSONB DEFAULT '[]',     -- 변수 정의 목록
  ADD COLUMN language         VARCHAR(10) DEFAULT 'ko',
  ADD COLUMN is_active        BOOLEAN NOT NULL DEFAULT TRUE,
  ADD COLUMN review_status_v2 VARCHAR(20) DEFAULT 'DRAFT',  -- 기존 review_status 대체
  ADD COLUMN email_template_id BIGINT REFERENCES email_template(id) ON DELETE SET NULL,
  ADD COLUMN created_by       BIGINT REFERENCES users(id),
  ADD COLUMN updated_by       BIGINT REFERENCES users(id),
  ADD COLUMN created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  ADD COLUMN updated_at       TIMESTAMPTZ NOT NULL DEFAULT now();
-- email_template_id: NULL이면 body_template 직접 사용, 非NULL이면 email_template 위임
```

### 12.3 Priority High — 발송 워커 구현

```
NotificationDispatchWorker (@Scheduled + @Transactional)
  ↓ 60초마다 PENDING + scheduled_at <= now() 조회 (LIMIT 10)
  ↓ 채널별 DispatchChannelExecutor 위임
     ├── EmailDispatchExecutor → EmailService 재사용
     ├── InappDispatchExecutor → UserNotificationInboxMapper 직접 INSERT
     └── (향후) PushDispatchExecutor, KakaoDispatchExecutor
  ↓ notification_dispatch_target 멱등성 INSERT
  ↓ schedule status COMPLETED/FAILED 업데이트
```

### 12.4 Priority High — 권한 시드 추가 (V61 또는 V61)

```sql
INSERT INTO permissions (code, resource, action, description) VALUES
  ('NOTIFICATION_TEMPLATE:READ',   'NOTIFICATION_TEMPLATE', 'READ',   '알림 템플릿 조회'),
  ('NOTIFICATION_TEMPLATE:WRITE',  'NOTIFICATION_TEMPLATE', 'WRITE',  '알림 템플릿 등록/수정'),
  ('NOTIFICATION_TEMPLATE:DELETE', 'NOTIFICATION_TEMPLATE', 'DELETE', '알림 템플릿 삭제'),
  ('DISPATCH:WRITE', 'DISPATCH', 'WRITE', '발송 예약 생성/트리거')
ON CONFLICT (code) DO NOTHING;
```

### 12.5 Priority Medium — notification_template CRUD API

`email_template` 도메인 구조를 모델로 삼아 동일 패턴으로 구현:

```
domain/notification/template/admin/
├── entity/NotificationTemplate.java
├── dto/NotificationTemplateCreateRequest.java (record)
├── dto/NotificationTemplateUpdateRequest.java (record)
├── dto/NotificationTemplateResponse.java (record)
├── repository/NotificationTemplateMapper.java
├── repository/NotificationTemplateMapper.xml
├── service/NotificationTemplateService.java
├── service/NotificationTemplateServiceImpl.java
└── controller/NotificationTemplateAdminController.java
    → GET    /api/v1/admin/notification-templates
    → GET    /api/v1/admin/notification-templates/{id}
    → POST   /api/v1/admin/notification-templates
    → PUT    /api/v1/admin/notification-templates/{id}
    → DELETE /api/v1/admin/notification-templates/{id}
```

### 12.6 Priority Medium — 프론트엔드 알림 템플릿 UI

`EmailTemplateListView.vue` 구조를 복제해서 `NotificationTemplateListView.vue` 생성:
- CRUD 폼: code, name, channel 선택, body_template (body_html + body_text), variables
- 라우터: `system/notification-templates` 추가
- API 래퍼: `api/notificationTemplate.ts`
- Pinia 스토어: `stores/notificationTemplate.ts`

### 12.7 Priority Low — PolicyDispatchView 템플릿 연동

신규 notification-templates API 완성 후, PolicyDispatchView.vue의 템플릿 선택 드롭다운을 실제 API 연동으로 교체.

### 12.8 구현 순서 권장

```
Phase 1 (백엔드 기반)
  1. V61 마이그레이션 (notification_template 확장 + 권한 시드)
  2. NotificationTemplate 엔티티 + Mapper + CRUD 서비스
  3. NotificationTemplateAdminController (@PreAuthorize)
  4. PolicyDispatchController @PreAuthorize 추가

Phase 2 (발송 워커)
  5. DispatchChannelExecutor 인터페이스 + EmailDispatchExecutor 구현
  6. InappDispatchExecutor 구현
  7. NotificationDispatchWorker @Scheduled 폴링

Phase 3 (프론트엔드)
  8. notificationTemplate.ts API 래퍼
  9. notificationTemplate.ts Pinia 스토어
  10. NotificationTemplateListView.vue
  11. 라우터 등록 + 메뉴 카탈로그 추가 (V61)
  12. PolicyDispatchView 템플릿 드롭다운 연동
```

---

## Appendix A. 파일 경로 참조

| 설명 | 경로 |
|-----|------|
| EmailService 인터페이스 | `backend/src/main/java/kr/co/ircp/cms/domain/auth/service/EmailService.java` |
| EmailServiceImpl | `backend/src/main/java/kr/co/ircp/cms/domain/auth/service/EmailServiceImpl.java` |
| EmailTemplateAdminController | `backend/src/main/java/kr/co/ircp/cms/domain/email/template/admin/controller/EmailTemplateAdminController.java` |
| EmailTemplateServiceImpl | `backend/src/main/java/kr/co/ircp/cms/domain/email/template/admin/service/EmailTemplateServiceImpl.java` |
| QnaNotificationServiceImpl | `backend/src/main/java/kr/co/ircp/cms/domain/board/service/QnaNotificationServiceImpl.java` |
| PolicyDispatchController | `backend/src/main/java/kr/co/ircp/cms/domain/policy/dispatch/controller/PolicyDispatchController.java` |
| PolicyDispatchServiceImpl | `backend/src/main/java/kr/co/ircp/cms/domain/policy/dispatch/service/PolicyDispatchServiceImpl.java` |
| NotificationDispatchSchedule | `backend/src/main/java/kr/co/ircp/cms/domain/policy/dispatch/entity/NotificationDispatchSchedule.java` |
| NotificationDispatchTarget | `backend/src/main/java/kr/co/ircp/cms/domain/policy/dispatch/entity/NotificationDispatchTarget.java` |
| AdminNotificationController | `backend/src/main/java/kr/co/ircp/cms/domain/notification/admin/controller/AdminNotificationController.java` |
| NotificationStatServiceImpl | `backend/src/main/java/kr/co/ircp/cms/domain/notification/stat/service/NotificationStatServiceImpl.java` |
| CriticalAuditNotifier | `backend/src/main/java/kr/co/ircp/cms/domain/audit/notification/CriticalAuditNotifier.java` |
| V16 policy_schema | `backend/src/main/resources/db/migration/V16__policy_schema.sql` |
| V21 qna_notification_schema | `backend/src/main/resources/db/migration/V21__qna_notification_schema.sql` |
| V35 user_notification_inbox | `backend/src/main/resources/db/migration/V35__user_notification_inbox.sql` |
| V40 admin_notification | `backend/src/main/resources/db/migration/V40__admin_notification.sql` |
| V46 delivery_status | `backend/src/main/resources/db/migration/V46__notification_delivery_status.sql` |
| V55 email_template | `backend/src/main/resources/db/migration/V55__email_template.sql` |
| V57 email_template_seed | `backend/src/main/resources/db/migration/V57__email_template_seed.sql` |
| PolicyDispatchView.vue | `frontend/admin/src/views/policy/PolicyDispatchView.vue` |
| NotificationCenterView.vue | `frontend/admin/src/views/notifications/NotificationCenterView.vue` |
| EmailTemplateListView.vue | `frontend/admin/src/views/system/EmailTemplateListView.vue` |
| adminNotifications.ts | `frontend/admin/src/api/adminNotifications.ts` |
| email-template.ts | `frontend/admin/src/api/email-template.ts` |
