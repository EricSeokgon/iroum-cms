---
id: SPEC-CMS-NOTIFICATION-CENTER-001
version: 0.1.0
status: Draft
created: 2026-05-29
updated: 2026-05-29
author: manager-spec
priority: P1
parent: SPEC-CMS-007 v0.4
related:
  - SPEC-CMS-007 (정책사업 매칭 + 적기 알림 발송 인프라 — `notification_dispatch_*`, `notification_send` 재사용)
  - SPEC-CMS-004 (`notification_template` 알림 템플릿 마스터 — 타입 분류 출처)
  - SPEC-CMS-005 (`integration_log` / `v_notification_history` — 발송 이력 뷰 참조)
  - SPEC-CMS-002 (RBAC — SUPER_ADMIN / CONTENT_ADMIN 권한 분리)
issue_number: TBD
---

# SPEC-CMS-NOTIFICATION-CENTER-001 관리자 알림 센터 (Admin Notification Center)

## HISTORY

- v0.1 / 2026-05-29 / manager-spec / 신규 작성. SPEC-CMS-007 이 구축한 발송 인프라(`notification_dispatch_schedule`/`notification_dispatch_target`/`notification_send`) 와 SPEC-CMS-004 의 `notification_template` 위에 **관리자 단일 화면 알림 허브** 를 정의. 관리자 헤더 배지·통합 목록·필터·읽음/보관·리소스 딥링크·30초 폴링 미읽음 갱신 8개 신규 REQ. 데이터 모델은 `user_notification_inbox` (V35) 와 동일한 패턴의 신규 `admin_notification` 테이블 (V40) 단일 마이그레이션. 발송 인프라(SPEC-CMS-007) 는 변경 없음.

---

## 1. 개요

### 1.1 목적

관리자가 시스템 알림(정책 매칭 발송 결과·승인 요청·오류·보안 이벤트 등) 을 **단일 화면에서 조회·필터·처리** 할 수 있는 통합 허브를 제공한다. 현재 관리자는 메뉴별로 흩어진 상태(승인 대기 목록, 발송 실패 로그, Q&A 답변 알림 등) 를 개별 확인해야 하며 통합 미읽음 인식 수단이 없다.

### 1.2 배경

- SPEC-CMS-007 v0.4 가 정책 매칭 알림 **발송** 인프라를 완성했으나, **발송 결과/오류/승인 요청** 을 관리자에게 능동적으로 보여주는 UI 가 없음
- SPEC-CMS-004 의 `notification_template` 은 발송 대상자 기준이며, **관리자 운영 알림** 은 별도 채널이 필요
- V35 의 `user_notification_inbox` 는 **시민 사용자용** Q&A 답변 알림에 한정 (관리자 알림 스키마와 의미가 다름)

### 1.3 범위 (Scope)

본 SPEC 은 SPEC-CMS-007 의 발송 인프라 **위에 UI/API 레이어를 추가** 하는 자식 SPEC 이다. 발송 측 도메인(`notification_dispatch_*`, `notification_send`) 의 스키마·로직은 **변경하지 않는다.**

핵심 가치:
- 관리자가 **헤더 배지** 로 미읽음 알림 수를 즉시 인지
- 한 화면에서 **타입/상태/기간 필터** 로 모든 시스템 알림 조회
- **개별/일괄 읽음 처리, 보관** 으로 받은편지함 정리
- 알림 클릭 시 **연관 리소스로 딥링크** (예: 게시글 승인 알림 → 해당 게시글 편집 화면)

---

## 2. SPEC-CMS-007 이 이미 제공하는 것 (재사용 인프라)

| 자산 | 위치 | 본 SPEC 의 사용 방식 |
|---|---|---|
| `notification_template` 마스터 | SPEC-CMS-004 §14.1 | **type/severity 분류 출처** (templateId 참조) |
| `notification_dispatch_schedule` | SPEC-CMS-007 §4.2 | **발송 예약 → 관리자 알림 생성 트리거** (예: 발송 실패 시) |
| `notification_dispatch_target` | SPEC-CMS-007 §4.2 | **시민 발송 대상** (관리자 알림과 분리, 변경 없음) |
| `notification_send` | SPEC-CMS-004 §14.2-1 | **발송 결과 참조** (관리자 알림의 ref_type='SEND', ref_id 로 링크) |
| `integration_log` | SPEC-CMS-005 §14.2 | **외부 연계 오류 발생 시 admin_notification 생성 소스** |
| `v_notification_history` 뷰 | SPEC-CMS-005 §13.3 | (참조 전용) 본 SPEC 에서 직접 사용 안 함 |
| SUPER_ADMIN / CONTENT_ADMIN RBAC | SPEC-CMS-002 §8 | 본 SPEC 의 모든 API 권한 매트릭스 출처 |

→ 본 SPEC 은 위 자산을 **변경하거나 대체하지 않는다.** 관리자 알림 데이터는 별도의 `admin_notification` 테이블에 저장하며, 발송 인프라는 _생성 트리거_ 로만 활용한다.

---

## 3. 본 SPEC 이 신규 도입하는 것

| 격차 | 현재 상태 | 본 SPEC 해결 방식 |
|---|---|---|
| 관리자용 통합 알림 저장소 | 부재 (시민용 `user_notification_inbox` 만 존재) | `admin_notification` 테이블 신규 (V40) |
| 미읽음 헤더 배지 | 부재 | `GET /unread-count` + 30초 폴링 |
| 통합 알림 목록 화면 | 부재 (메뉴별 산재) | `NotificationCenterView.vue` (`/admin/notifications`) |
| 타입/상태/기간 필터 | 부재 | severity (INFO/WARN/ERROR) + status (UNREAD/READ/ARCHIVED) + date range |
| 일괄 읽음 처리 | 부재 | `PATCH /read-all` (필터 기준 적용) |
| 보관(아카이브) | 부재 (삭제만 가능) | `status='ARCHIVED'` 소프트 보관, 기본 목록 제외 |
| 리소스 딥링크 | 부재 | `ref_type` + `ref_id` + 프론트 라우터 매핑 |

---

## 4. 범위 및 비범위

### 4.1 범위 (포함)

- 관리자별 알림 저장소 (`admin_notification` 테이블, V40 단일 마이그레이션)
- 통합 알림 목록 조회 API + Vue 화면 (`NotificationCenterView.vue`)
- 헤더 미읽음 배지 (`AppHeader.vue` 변경)
- 필터: severity (INFO/WARN/ERROR), status (UNREAD/READ/ARCHIVED), date range (from/to)
- 페이지네이션 (기존 `PageResponse` 패턴 재사용)
- 개별 읽음 처리 / 일괄 읽음 처리 / 보관 처리
- 알림 클릭 시 `ref_type` + `ref_id` 기반 프론트 라우터 딥링크
- 30초 폴링 기반 미읽음 수 갱신 (백오프: 화면 비활성 탭에서는 폴링 일시 중지)
- KWCAG 2.2 AA (스크린리더 라이브 영역 `aria-live="polite"` 로 신규 알림 통지)

### 4.2 비범위 (제외)

| 비범위 | 결정 | 향후 검토 |
|---|---|---|
| 실시간 WebSocket/SSE | 1차는 폴링으로 충분 (관리자 동시 접속자 ≤ 50) | v0.2+ |
| 알림 우선순위 자동 학습 | 운영자 수동 분류 우선 | 별도 SPEC |
| 알림 카테고리별 구독 설정 | 시민 알림(SPEC-CMS-007) 의 `notification_subscription` 과 분리 정책 | v0.2+ |
| 알림 푸시 (브라우저 Notification API) | OS 권한 요청 UX 부담 | v0.3+ |
| 알림 메일 디제스트 | 관리자 받은편지함이 이미 메일과 중복 | 제외 |
| 알림 삭제 (HARD DELETE) | 감사 추적 보존 위해 보관(ARCHIVED) 만 허용 | 제외 |
| 시민 사용자용 알림 센터 (`user_notification_inbox` UI) | 별도 SPEC 범위 | 후속 |
| `notification_template` 신규 채널 추가 | 관리자 알림은 INAPP 전용, 외부 발송 채널 무관 | 없음 |

---

## 5. 데이터 모델

### 5.1 신규 테이블 — `admin_notification` (V40)

관리자 1명당 N행. `user_notification_inbox` (V35) 와 유사한 구조이지만 **관리자 운영 알림 의미론** 에 맞춰 severity·status 컬럼을 명시적으로 분리한다.

```sql
-- V40__admin_notification.sql
CREATE TABLE admin_notification (
    id              BIGSERIAL    PRIMARY KEY,
    admin_user_id   BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,

    -- 5.1.1 분류
    type            VARCHAR(50)  NOT NULL,
        -- e.g. POST_APPROVAL_REQUEST, NOTIFICATION_SEND_FAILED,
        --      INTEGRATION_ERROR, SECURITY_EVENT, POLICY_SYNC_WARNING
    severity        VARCHAR(10)  NOT NULL DEFAULT 'INFO'
                    CHECK (severity IN ('INFO','WARN','ERROR')),

    -- 5.1.2 본문
    title           VARCHAR(200) NOT NULL,
    body            TEXT,

    -- 5.1.3 연관 리소스 (딥링크용)
    ref_type        VARCHAR(50),
        -- e.g. POST, COMMENT, NOTIFICATION_SEND, INTEGRATION_LOG, POLICY_PROGRAM
    ref_id          BIGINT,

    -- 5.1.4 상태 (UNREAD → READ → ARCHIVED, 단방향 전이만 권장)
    status          VARCHAR(10)  NOT NULL DEFAULT 'UNREAD'
                    CHECK (status IN ('UNREAD','READ','ARCHIVED')),
    read_at         TIMESTAMPTZ,
    archived_at     TIMESTAMPTZ,

    -- 5.1.5 메타
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    -- 90일 후 자동 보관 정책은 별도 배치(후속) — 본 SPEC 범위 아님

    CONSTRAINT chk_admin_notif_read     CHECK (status <> 'READ' OR read_at IS NOT NULL),
    CONSTRAINT chk_admin_notif_archived CHECK (status <> 'ARCHIVED' OR archived_at IS NOT NULL)
);

-- 목록 조회 최적화: admin_user_id + status + created_at DESC
CREATE INDEX idx_admin_notif_user_status
    ON admin_notification (admin_user_id, status, created_at DESC);

-- 미읽음 수 집계 부분 인덱스 (UNREAD 만)
CREATE INDEX idx_admin_notif_unread
    ON admin_notification (admin_user_id)
    WHERE status = 'UNREAD';

-- 타입 필터
CREATE INDEX idx_admin_notif_type
    ON admin_notification (admin_user_id, type, created_at DESC);

COMMENT ON TABLE admin_notification IS
  'SPEC-CMS-NOTIFICATION-CENTER-001: 관리자 운영 알림 받은편지함';
COMMENT ON COLUMN admin_notification.ref_type IS
  '딥링크용 리소스 타입. 프론트 라우터가 ref_type → URL 매핑 수행';
COMMENT ON COLUMN admin_notification.status IS
  'UNREAD → READ → ARCHIVED 단방향 전이. HARD DELETE 금지 (감사 추적)';
```

### 5.2 기존 테이블 변경

**없음.** `notification_dispatch_*`, `notification_send`, `notification_template`, `user_notification_inbox` 는 모두 변경하지 않는다.

### 5.3 마이그레이션

- 신규 파일: `V40__admin_notification.sql` (단일 마이그레이션)
- 백필 불필요 (신규 테이블, 기존 사용자에게는 0개 알림으로 시작)
- 알림 생성은 **이벤트 발생 시점** 부터 적용 (소급 생성 없음)

---

## 6. 신규 요구사항 (REQ-NC-*)

본 SPEC 의 모든 요구사항은 EARS (Event/Always/Result/State/Unwanted) 형식을 따른다.

### REQ-NC-001 — 알림 목록 조회 API (Ubiquitous)

**The system shall** 관리자(SUPER_ADMIN 또는 CONTENT_ADMIN) 의 요청 시 `GET /api/v1/admin/notifications` 엔드포인트로 본인의 알림 목록을 페이지네이션·필터링하여 반환한다.

- 쿼리 파라미터: `severity` (INFO/WARN/ERROR, 다중 선택), `status` (UNREAD/READ/ARCHIVED, 기본 UNREAD,READ — ARCHIVED 는 명시적 요청만), `from` (yyyy-MM-dd), `to` (yyyy-MM-dd), `page` (0-base), `size` (기본 20, 최대 100)
- 응답: `PageResponse<AdminNotificationDto>` (기존 `kr.co.ircp.cms.common.PageResponse` 재사용)
- 정렬: `created_at DESC` 고정
- 권한: SUPER_ADMIN, CONTENT_ADMIN (본인 알림만; 타 관리자 알림 조회 금지)

### REQ-NC-002 — 개별 읽음 처리 API (Event-driven)

**When** 관리자가 `PATCH /api/v1/admin/notifications/{id}/read` 를 호출 **then the system shall** 해당 알림의 `status` 를 'READ' 로, `read_at` 을 현재 시각으로 갱신한다.

- 대상 알림이 본인 소유가 아닐 경우 → 403 Forbidden
- 이미 READ 또는 ARCHIVED 인 경우 → 200 OK 멱등 응답 (재호출 안전)
- 존재하지 않는 id → 404 Not Found

### REQ-NC-003 — 일괄 읽음 처리 API (Event-driven)

**When** 관리자가 `PATCH /api/v1/admin/notifications/read-all` 을 호출 **then the system shall** 본인의 UNREAD 알림 전체를 READ 로 일괄 전환한다.

- 요청 본문 (선택): `{ "severity": ["WARN","ERROR"], "type": ["POST_APPROVAL_REQUEST"] }` — 지정 시 해당 조건의 UNREAD 만 대상
- 본문 미제공 시 → 전체 UNREAD 대상
- 응답: `{ "updatedCount": N }`
- 단일 트랜잭션 (`UPDATE ... WHERE admin_user_id=? AND status='UNREAD' [AND ...]`)

### REQ-NC-004 — 보관 처리 API (Event-driven)

**When** 관리자가 `PATCH /api/v1/admin/notifications/{id}/archive` 를 호출 **then the system shall** 해당 알림의 `status` 를 'ARCHIVED' 로, `archived_at` 을 현재 시각으로 갱신한다.

- UNREAD 알림 보관 시 → `read_at` 도 동시에 채움 (UNREAD→ARCHIVED 직접 전이 허용)
- ARCHIVED 알림 재호출 시 → 멱등 응답
- 권한: 본인 알림만

### REQ-NC-005 — 미읽음 수 배지 API (Ubiquitous)

**The system shall** `GET /api/v1/admin/notifications/unread-count` 호출 시 본인의 UNREAD 알림 총 수를 반환한다.

- 응답: `{ "unreadCount": N }`
- 캐싱: 없음 (실시간성 우선; `idx_admin_notif_unread` 부분 인덱스로 비용 ≤ 10ms)
- 최대 표시 값: 99+ (프론트 처리; API 는 실제 수 반환)

### REQ-NC-006 — 관리자 헤더 배지 표시 (State-driven)

**While** 관리자가 로그인 상태이며 미읽음 알림이 1개 이상 존재 **the system shall** 관리자 헤더 우측 종 아이콘 위에 미읽음 수 배지(빨간색 원) 를 표시한다.

- 0개 시 → 배지 미표시 (종 아이콘만)
- 1~99 → 정수 표시
- 100 이상 → '99+' 표시
- `aria-label` 에 "미읽음 알림 N개" 동적 갱신 (KWCAG 1.3.1)

### REQ-NC-007 — `NotificationCenterView.vue` 목록·필터·페이지네이션 (Ubiquitous)

**The system shall** `/admin/notifications` 경로에서 `NotificationCenterView.vue` 를 렌더링하며, 다음 UI 요소를 포함한다.

- 상단 필터 바: severity 다중 선택 (Element Plus `el-checkbox-group`), status 라디오 (UNREAD/READ/ARCHIVED/ALL), 날짜 범위 (`el-date-picker` range)
- 알림 목록: 카드형 (severity 아이콘 + 제목 + 본문 미리보기 + 상대 시간 + 액션 버튼 [읽음/보관/이동])
- 우상단: "모두 읽음" 버튼 (REQ-NC-003 호출, 현재 필터 적용)
- 페이지네이션: 기존 `BasePagination.vue` 재사용, size 20 기본
- 행 클릭 시 → REQ-NC-008 의 딥링크 + REQ-NC-002 자동 호출
- 빈 상태: "받은 알림이 없습니다" 메시지 + 일러스트

### REQ-NC-008 — 딥링크 라우팅 (Event-driven)

**When** 관리자가 알림 카드를 클릭하고 해당 알림에 `ref_type` 및 `ref_id` 가 모두 존재 **then the system shall** 프론트 라우터를 통해 매핑된 화면으로 이동하며, 동시에 REQ-NC-002 의 읽음 처리 API 를 호출한다.

- 매핑 (1차 출시):
  - `POST` → `/admin/board/posts/{ref_id}/edit`
  - `COMMENT` → `/admin/board/comments/{ref_id}`
  - `NOTIFICATION_SEND` → `/admin/notifications/send-history/{ref_id}` (SPEC-CMS-007 화면)
  - `INTEGRATION_LOG` → `/admin/integration/logs/{ref_id}` (SPEC-CMS-005 화면)
  - `POLICY_PROGRAM` → `/admin/policy/programs/{ref_id}` (SPEC-CMS-007 화면)
- `ref_type` 또는 `ref_id` 가 NULL → 카드 클릭 시 읽음 처리만 수행, 이동 없음 ("이동 가능한 리소스 없음" 토스트)
- 매핑되지 않은 `ref_type` → 콘솔 경고 + 읽음 처리만

### REQ-NC-009 — 30초 폴링 미읽음 수 갱신 (State-driven)

**While** 관리자 세션이 활성 상태이며 브라우저 탭이 활성(visibilityState='visible') **the system shall** 30초마다 REQ-NC-005 의 미읽음 수 API 를 호출하여 헤더 배지를 갱신한다.

- 탭이 백그라운드(visibilityState='hidden') → 폴링 일시 중지
- 탭이 다시 활성화 → 즉시 1회 호출 후 폴링 재개
- 폴링 실패 (네트워크/401/5xx) → 다음 폴링까지 대기 (사용자 토스트 없음), 401 은 인터셉터가 재인증 처리
- 로그아웃 시 → 폴링 즉시 중단

### REQ-NC-010 — 권한 격리 (Unwanted)

**The system shall not** 관리자가 타 관리자의 알림을 조회·수정·보관하도록 허용해서는 안 된다.

- 모든 API 는 JWT 의 `userId` 를 강제 사용 (요청 본문/경로의 `adminUserId` 무시)
- `id` 기반 단건 조회/수정 시 `WHERE id=? AND admin_user_id=?` 강제 — 미일치 시 403
- 시도 발생 시 `audit_log` 에 PERMISSION_DENIED 기록 (SPEC-CMS-005)

---

## 7. 수락 기준 (AC-NC-*)

### AC-NC-001 (REQ-NC-001 — 목록 조회)

- **AC-NC-001-1** SUPER_ADMIN A 가 본인의 UNREAD 알림 50개를 보유한 상태에서 `GET /api/v1/admin/notifications?status=UNREAD&size=20` 호출 시 → 200 OK, `content.length=20`, `totalElements=50`, `totalPages=3`
- **AC-NC-001-2** `severity=ERROR&from=2026-05-01&to=2026-05-29` 필터 적용 시 → 응답의 모든 항목이 severity='ERROR' 이며 created_at 이 범위 내
- **AC-NC-001-3** CONTENT_ADMIN B 가 호출 시 → B 본인의 알림만 반환되며 A 의 알림은 0건

### AC-NC-002 (REQ-NC-002 — 개별 읽음)

- **AC-NC-002-1** UNREAD 알림 id=100 에 대해 `PATCH .../100/read` 호출 → 200 OK, DB 상 `status='READ'`, `read_at` 채워짐
- **AC-NC-002-2** 동일 호출 재실행 → 200 OK, `read_at` 최초 값 유지 (멱등성)
- **AC-NC-002-3** 타 관리자 소유 알림 id 호출 → 403 Forbidden, DB 변경 없음

### AC-NC-003 (REQ-NC-003 — 일괄 읽음)

- **AC-NC-003-1** 본인 UNREAD 알림 30개 (WARN 10개 + ERROR 20개) 보유 상태에서 `PATCH .../read-all` 본문 미제공 → `{"updatedCount":30}`, DB 모두 READ
- **AC-NC-003-2** `PATCH .../read-all` 본문 `{"severity":["ERROR"]}` → `{"updatedCount":20}`, WARN 10개는 UNREAD 유지

### AC-NC-004 (REQ-NC-004 — 보관)

- **AC-NC-004-1** READ 알림 보관 → `status='ARCHIVED'`, `archived_at` 채워짐, `read_at` 유지
- **AC-NC-004-2** UNREAD 알림 직접 보관 → `status='ARCHIVED'`, `read_at` 과 `archived_at` 둘 다 채워짐
- **AC-NC-004-3** 보관된 알림이 기본 목록 조회(`status=UNREAD,READ`)에서 제외됨

### AC-NC-005 (REQ-NC-005 — 미읽음 수)

- **AC-NC-005-1** 본인 UNREAD 알림 7개 보유 → `{"unreadCount":7}`
- **AC-NC-005-2** 0개 보유 → `{"unreadCount":0}`
- **AC-NC-005-3** 응답 시간 ≤ 50ms (10,000건 알림 보유 상태, 부분 인덱스 활용)

### AC-NC-006 (REQ-NC-006 — 헤더 배지)

- **AC-NC-006-1** 로그인 후 미읽음 7개 → 헤더 종 아이콘 위 빨간 원에 '7' 표시
- **AC-NC-006-2** 미읽음 0개 → 빨간 원 미표시 (종 아이콘만)
- **AC-NC-006-3** 미읽음 150개 → '99+' 표시
- **AC-NC-006-4** Playwright E2E: `aria-label="미읽음 알림 7개"` 텍스트 매칭

### AC-NC-007 (REQ-NC-007 — Vue 화면)

- **AC-NC-007-1** `/admin/notifications` 진입 시 NotificationCenterView 렌더링, 기본 필터 status=UNREAD,READ
- **AC-NC-007-2** 사용자가 severity 'ERROR' 만 체크 → 목록 자동 갱신, ERROR 알림만 표시
- **AC-NC-007-3** "모두 읽음" 버튼 클릭 → 확인 다이얼로그 → REQ-NC-003 호출 → 토스트 "30개 알림을 읽음 처리했습니다"
- **AC-NC-007-4** 빈 결과 시 일러스트 + "받은 알림이 없습니다" 메시지

### AC-NC-008 (REQ-NC-008 — 딥링크)

- **AC-NC-008-1** ref_type='POST', ref_id=42 알림 카드 클릭 → `/admin/board/posts/42/edit` 라우팅 + DB read 처리
- **AC-NC-008-2** ref_type=NULL 알림 클릭 → 라우팅 없음, 읽음 처리만, "이동 가능한 리소스 없음" 토스트
- **AC-NC-008-3** 매핑 미정의 ref_type 클릭 → 콘솔 경고 + 읽음 처리

### AC-NC-009 (REQ-NC-009 — 폴링)

- **AC-NC-009-1** 30초 경과 시 unread-count API 자동 호출 (네트워크 탭으로 검증)
- **AC-NC-009-2** 탭 백그라운드 전환 → 다음 호출 스킵 (`document.hidden===true` 분기)
- **AC-NC-009-3** 탭 복귀 → 즉시 1회 호출 + 폴링 재개
- **AC-NC-009-4** 401 응답 → 토스트 없음, 다음 30초까지 대기, 인증 인터셉터에 위임

### AC-NC-010 (REQ-NC-010 — 권한 격리)

- **AC-NC-010-1** 관리자 A 의 JWT 로 알림 B(타 관리자 소유) id 호출 → 403, `audit_log` 에 PERMISSION_DENIED 1건 기록
- **AC-NC-010-2** 요청 본문에 임의의 `adminUserId` 주입 → 무시되고 JWT 의 userId 사용 (B 의 알림 노출 없음)
- **AC-NC-010-3** PUBLIC_USER (시민) 토큰으로 호출 → 403 (RBAC 차단, SPEC-CMS-002 권한 매트릭스)

### AC-NC-011 (마이그레이션)

- **AC-NC-011-1** Flyway `V40__admin_notification.sql` 적용 후 `admin_notification` 테이블·3개 인덱스 존재
- **AC-NC-011-2** 마이그레이션 롤백 시 종속 객체 없음 (CASCADE 안전)

### AC-NC-012 (성능)

- **AC-NC-012-1** 100,000건 알림 보유 관리자의 목록 조회 (page=0, size=20) ≤ 100ms (idx_admin_notif_user_status 활용)
- **AC-NC-012-2** unread-count 호출 (10,000건 UNREAD 보유) ≤ 50ms

### AC-NC-013 (접근성 KWCAG 2.2 AA)

- **AC-NC-013-1** 헤더 배지에 `aria-label="미읽음 알림 N개"` 동적 갱신, 스크린리더 읽힘 확인
- **AC-NC-013-2** 신규 알림 도착 시 `aria-live="polite"` 영역 갱신 (NotificationCenterView 상단)
- **AC-NC-013-3** 모든 필터·버튼 키보드 포커스 순서 정상, Element Plus 기본 ARIA 준수

### AC-NC-014 (회귀 방지)

- **AC-NC-014-1** SPEC-CMS-007 의 `notification_dispatch_*` 관련 기존 백엔드/E2E 테스트 전체 GREEN
- **AC-NC-014-2** `user_notification_inbox` (V35) 사용 화면(`NotificationSettingsView.vue`) 동작 변화 없음

### AC-NC-015 (다국어)

- **AC-NC-015-1** ko/en 양 언어로 UI 라벨 (제목·필터·버튼·빈 상태) 표시
- **AC-NC-015-2** severity/type/status 표시명은 i18n 키 기반 (예: `notifications.severity.ERROR`)

---

## 8. 기술 접근

### 8.1 백엔드 (Java 17 / Spring Boot 3.3 / MyBatis / PostgreSQL)

**패키지 위치:** `kr.co.ircp.cms.domain.notification.admin`

레이어 구성:
- `AdminNotificationController` — REST 엔드포인트 5개 (REQ-NC-001~005)
- `AdminNotificationService` — 권한 격리(REQ-NC-010) + 멱등 전이 로직
- `AdminNotificationMapper` (MyBatis XML) — `admin_notification` SELECT/UPDATE
- `AdminNotificationDto` / `AdminNotificationListQuery` — DTO
- `AdminNotificationEventListener` (별도, 후속) — 도메인 이벤트 수신 후 알림 INSERT (1차 출시는 수동 INSERT API 미제공, 발송 실패 시 SPEC-CMS-007 측 코드가 직접 INSERT 호출하는 접점만 마련)

권한:
- `@PreAuthorize("hasAnyRole('SUPER_ADMIN','CONTENT_ADMIN')")` (기존 RBAC 패턴 재사용)
- Service 레이어에서 `admin_user_id` 강제 주입 (Controller 에서 받은 path id + JWT userId 조합 검증)

응답 표준:
- 페이지네이션: `kr.co.ircp.cms.common.PageResponse<T>` 재사용 (기존 게시판/Q&A 화면과 동일)
- 에러: `kr.co.ircp.cms.common.exception.GlobalExceptionHandler` 의 표준 `ErrorResponse` (403/404/422)

### 8.2 프론트엔드 (Vue 3 / TypeScript / Element Plus / Pinia)

**파일 위치:** `frontend/admin/src/`
- `views/notifications/NotificationCenterView.vue` — REQ-NC-007 메인 화면
- `composables/useUnreadCountPolling.ts` — REQ-NC-009 폴링 훅 (visibilityState 처리)
- `api/adminNotifications.ts` — API 클라이언트 5개 메서드
- `stores/notificationCenter.ts` — Pinia 스토어 (목록·필터·미읽음 수 상태)
- `components/AppHeader.vue` (변경) — 종 아이콘 + 배지 컴포넌트 마운트
- `router/index.ts` (변경) — `/admin/notifications` 라우트 등록
- `i18n/locales/ko.json`, `en.json` (변경) — 알림 관련 키 추가

라우터 딥링크 매핑:
- `src/router/notificationDeepLink.ts` (신규) — `ref_type` → URL 변환 단일 책임 모듈 (REQ-NC-008 매핑 테이블 보유)

### 8.3 DB 스키마 결정 사유 — 신규 테이블 vs 기존 재사용

**결정: 신규 `admin_notification` 테이블 도입**

검토한 대안:
1. **SPEC-CMS-007 의 `notification_dispatch_target` 재사용** → 부적합. 해당 테이블은 _시민 사용자_ 발송 대상 추적용이며 admin_user_id 컬럼 없음, severity/status 의미가 다름 (PENDING/SENT/FAILED 발송 상태이지 받은편지함 상태 아님).
2. **V35 `user_notification_inbox` 확장 (admin scope 추가)** → 부적합. 해당 테이블은 시민 Q&A 답변 알림용 (REQ-BOARD-014-D-2) 으로 의미론이 좁다. 관리자 알림 도메인 이벤트와 섞이면 인덱스·조회 패턴 충돌 및 의미 혼선 발생.
3. **신규 `admin_notification` 테이블** → 채택. 의미론 분리 + 인덱스 최적화(`idx_admin_notif_unread` 부분 인덱스) + 권한 모델 단순화.

이 결정의 후속 영향:
- 시민용 V35 와 관리자용 V40 은 **의도적으로 분리** 유지
- 향후 통합이 필요해질 경우 별도 SPEC 에서 마이그레이션 (현 시점에 통합 비용 > 이득)

### 8.4 발송 인프라(SPEC-CMS-007) 와의 연계

본 SPEC 의 `admin_notification` INSERT 시점:
- 1차 출시: SPEC-CMS-007 의 발송 실패 핸들러가 직접 INSERT (Service 단순 호출, 이벤트 버스 미사용)
- v0.2+: Spring `ApplicationEventPublisher` 기반 이벤트 분리

연계 시 `ref_type='NOTIFICATION_SEND'` + `ref_id=notification_send.id` 로 발송 실패 상세 화면 딥링크.

### 8.5 폴링 vs WebSocket 결정

- 1차 출시: HTTP 폴링 30초 (REQ-NC-009)
- 사유: 관리자 동시 접속자 ≤ 50명 추정, WebSocket 인프라 미구축 (Spring WebFlux/STOMP 도입 비용 > 30초 지연 허용 가치)
- v0.2+ 전환 트리거: 동시 접속 100명 초과 또는 알림 평균 지연 30초 SLA 위반 시 재검토

### 8.6 보안

- 모든 API: HTTPS + JWT (SPEC-CMS-002 인증 인프라 재사용)
- SQL Injection: MyBatis `#{}` 파라미터 바인딩 (기존 패턴 준수)
- 권한 격리 위반 시도: `audit_log` 자동 기록 (SPEC-CMS-005 감사 인프라 재사용)
- XSS: 알림 `body` 는 `v-text` 렌더링 (HTML 허용 안 함)

---

## 9. 의존 SPEC

| SPEC ID | 버전 | 의존 사유 | 본 SPEC 의 영향 |
|---|---|---|---|
| SPEC-CMS-007 | v0.4 | 발송 인프라 (`notification_dispatch_*`, `notification_send`) — `ref_type='NOTIFICATION_SEND'` 딥링크 대상 | **읽기만 수행, 변경 없음** |
| SPEC-CMS-004 | v0.2.1 | `notification_template` — `type` 분류 명명 참조 | 변경 없음 |
| SPEC-CMS-005 | v0.2.1 | `integration_log` 딥링크 대상, `audit_log` 감사 기록 | 변경 없음 |
| SPEC-CMS-002 | v0.3 | RBAC (SUPER_ADMIN/CONTENT_ADMIN) | 변경 없음 |

상위 SPEC: **SPEC-CMS-007 v0.4** (본 SPEC 은 발송 인프라 위 UI 레이어)

---

## 10. 미해결 질문 / 사용자 확인 필요 사항

본 SPEC 작성 중 다음 의사결정은 합리적 가정으로 진행했으며, /moai plan 의 annotation cycle 에서 사용자 확인 필요:

1. **알림 보존 기간 (90일 자동 보관)** — 본 SPEC 은 자동 보관 배치를 범위에서 제외했으나, 1년 후 테이블 크기 추정 시 정책 필요. → 후속 SPEC 또는 본 SPEC v0.2 에서 결정.
2. **CONTENT_ADMIN 의 알림 타입 범위** — 현 가정: CONTENT_ADMIN 은 본인 담당 도메인(게시판/Q&A) 알림만 수신. SUPER_ADMIN 은 INTEGRATION_ERROR/SECURITY_EVENT 등 시스템 알림 수신. → 알림 INSERT 측 (발송 인프라 연계) 에서 admin_user_id 결정 로직 명세 필요.
3. **폴링 주기 30초** — 적정성. 사용자가 더 짧은 주기(10초) 또는 긴 주기(60초) 선호 시 조정.
4. **REQ-NC-008 매핑 테이블의 후속 확장 정책** — 신규 `ref_type` 추가 시 단순 코드 변경으로 충분한지, 별도 설정 테이블화 필요 여부.
5. **시민용 알림 센터 (`user_notification_inbox` 화면)** — 본 SPEC 은 관리자 전용. 시민용 별도 SPEC 필요 여부 확인.

---

## 11. 변경 이력 트래커

본 SPEC 구현 시 영향받는 파일 목록 (예상):

**백엔드 신규:**
- `backend/src/main/resources/db/migration/V40__admin_notification.sql`
- `backend/src/main/java/kr/co/ircp/cms/domain/notification/admin/AdminNotificationController.java`
- `backend/src/main/java/kr/co/ircp/cms/domain/notification/admin/AdminNotificationService.java`
- `backend/src/main/java/kr/co/ircp/cms/domain/notification/admin/AdminNotificationMapper.java`
- `backend/src/main/resources/mapper/notification/AdminNotificationMapper.xml`
- `backend/src/main/java/kr/co/ircp/cms/domain/notification/admin/dto/AdminNotificationDto.java`
- `backend/src/main/java/kr/co/ircp/cms/domain/notification/admin/dto/AdminNotificationListQuery.java`
- (테스트) `backend/src/test/java/kr/co/ircp/cms/domain/notification/admin/AdminNotificationControllerIT.java`

**프론트엔드 신규:**
- `frontend/admin/src/views/notifications/NotificationCenterView.vue`
- `frontend/admin/src/composables/useUnreadCountPolling.ts`
- `frontend/admin/src/api/adminNotifications.ts`
- `frontend/admin/src/stores/notificationCenter.ts`
- `frontend/admin/src/router/notificationDeepLink.ts`
- (테스트) `frontend/admin/src/__tests__/notifications/NotificationCenterView.spec.ts`
- (E2E) `tests/e2e/admin/notification-center.spec.ts`

**프론트엔드 변경:**
- `frontend/admin/src/components/AppHeader.vue` (배지 + 종 아이콘 추가)
- `frontend/admin/src/router/index.ts` (라우트 등록)
- `frontend/admin/src/i18n/locales/ko.json`, `en.json` (키 추가)

---

END OF SPEC
