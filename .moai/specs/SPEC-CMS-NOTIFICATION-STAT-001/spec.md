---
id: SPEC-CMS-NOTIFICATION-STAT-001
title: 관리자 알림 발송 통계 대시보드
status: Implemented
version: 1.0.0
created_at: 2026-06-11
updated_at: 2026-06-11
author: manager-spec (MoAI)
priority: P2
depends_on:
  - SPEC-CMS-NOTIFICATION-CENTER-001
  - SPEC-CMS-KPI-001
---

# SPEC-CMS-NOTIFICATION-STAT-001 — 관리자 알림 발송 통계 대시보드

## HISTORY

- 2026-06-11 (v1.0.1): 구현 완료 (Implemented). 커밋 009c5ac — backend 14개 파일(V46 마이그레이션, 5개 엔드포인트, NotificationStatMapper XML, 단위 테스트 9건 GREEN), frontend 4개 파일(NotificationStatPanel.vue, notificationStatStore.ts, notificationStat.ts, DashboardView.vue additive 통합), KpiValueMapper upsertNotificationKpi 추가.
- 2026-06-11 (v1.0.0): 최초 작성 (Draft). 시민용 인앱 알림 발송 현황·읽음율·카테고리·일별 추이·오류 목록·KPI 위젯 피드 통합. user_notification_inbox(V35) 재사용 + 단일 additive 컬럼(delivery_status) + 단일 마이그레이션(V46). manager-spec (MoAI).

---

## 1. 개요 (Overview)

플랫폼 운영자가 **시민에게 발송된 인앱(INAPP) 알림의 건전성**을 한눈에 파악하도록, 관리자 전용 **알림 발송 통계 대시보드** 패널을 추가한다. 발송 현황 요약, 카테고리(type)별 발송 통계, 최근 30일 일별 추이, 미발송/오류 알림 목록 및 재발송, 그리고 기존 KPI 대시보드(SPEC-CMS-KPI-001)가 표시할 수 있는 알림 건전성 KPI 피드를 제공한다.

본 SPEC은 **additive-only**이다. 기존 알림 센터(SPEC-CMS-NOTIFICATION-CENTER-001) 및 KPI 대시보드(SPEC-CMS-KPI-001)의 컬럼·엔드포인트·서비스 시그니처를 **수정하지 않는다**.

### 1.1 데이터 모델 정합성 (중요)

본 SPEC의 "발송 통계"는 **시민 수신 인앱 알림 테이블 `user_notification_inbox`(V35)** 를 단일 진실 원천으로 한다.

- `user_notification_inbox`(V35): `user_id`, `type`, `title`, `is_read`, `read_at`, `created_at` — 시민에게 발송된 알림 1행 = 발송 1건.
- `admin_notification`(V40): 관리자 운영 수신함(승인 요청·발송 실패 통지 등)으로, **발송 대상이 아닌 관리자 본인 수신함**이다. 발송 통계 집계 대상에서 **제외**한다.

> [HARD] 발송 현황·읽음율·카테고리·일별 추이는 모두 `user_notification_inbox` 기준이다. `admin_notification` 을 발송 모수로 혼용하지 않는다.

### 1.2 오류/미발송 추적 격차

V35에는 **발송 성공/실패를 구분하는 컬럼이 존재하지 않는다**(기존 행은 모두 "성공 발송"으로 INSERT됨). 오류 목록(REQ-NS-004)을 위해 V35에 nullable additive 컬럼 `delivery_status` 1개만 추가한다. 기존 행은 NULL이며, 집계 시 **NULL = SENT(정상 발송)** 로 간주하여 백필을 불요화한다.

---

## 2. 용어 (Glossary)

- **발송(Dispatch)**: 시민 사용자 수신함(`user_notification_inbox`)에 알림 1행이 INSERT된 사건.
- **읽음율(Read Rate)**: `is_read = TRUE` 비율 = 읽은 건수 / 발송 건수.
- **카테고리(Category)**: 알림 `type` 코드(예: QNA_ANSWERED, SYSTEM, BOARD, AI).
- **오류/미발송(Failed/Undelivered)**: `delivery_status IN ('FAILED','PENDING')` 인 알림.
- **KPI 피드(KPI Feed)**: SPEC-CMS-KPI-001 위젯이 소비 가능한 `kpi_value` 호환 집계값.

---

## 3. 범위 (Scope)

### 3.1 In Scope

1. 발송 현황 요약 조회 API (today / 7일 / 30일 구간: 발송 수·읽음율·미읽음 수·오류 수)
2. 카테고리(type)별 발송 통계 조회 API
3. 최근 30일 일별 발송 추이 데이터 조회 API (프런트가 차트 렌더)
4. 미발송/오류 알림 목록 조회 + 개별 재발송 API
5. 알림 건전성 KPI 피드(읽음율·오류율)를 `kpi_value` 호환 형태로 노출하여 KPI 대시보드 위젯이 소비
6. 관리자 전용 통계 패널 (Vue 3 + Pinia + TS), 기존 대시보드 뷰에 통합
7. V35 additive 컬럼(`delivery_status`) 단일 마이그레이션(V46)

### 3.2 Out of Scope — `## Exclusions (What NOT to Build)` 참조

---

## 4. 요구사항 (EARS Requirements)

### REQ-NS-001 — 발송 현황 요약 (Event-Driven)

**When** 관리자가 발송 현황 요약을 요청하면, **the system shall** today/7일/30일 각 구간에 대해 총 발송 건수, 읽음율(소수 2자리), 미읽음 건수, 오류 건수를 단일 응답으로 반환한다.

- 집계 모수: `user_notification_inbox`, `created_at` 기준 구간 필터.
- 오류 건수: `delivery_status IN ('FAILED','PENDING')`.

### REQ-NS-002 — 카테고리별 발송 통계 (Event-Driven)

**When** 관리자가 카테고리별 통계를 요청하면, **the system shall** `type` 별 발송 건수와 읽음 건수를 발송 건수 내림차순으로 반환한다.

- 구간 파라미터(`from`/`to`, 기본 최근 30일)를 지원한다.

### REQ-NS-003 — 일별 발송 추이 (Event-Driven)

**When** 관리자가 일별 추이를 요청하면, **the system shall** 최근 30일(요청 시 1~90일 범위) 각 일자별 발송 건수와 읽음 건수를 일자 오름차순 시계열로 반환한다.

- 발송이 0건인 일자도 0으로 채워(gap-fill) 연속 시계열을 보장한다.
- 시각화 렌더링은 프런트(vue-echarts LineChart) 책임이며 백엔드는 데이터만 반환한다.

### REQ-NS-004 — 미발송/오류 알림 목록 (Event-Driven)

**When** 관리자가 오류 목록을 요청하면, **the system shall** `delivery_status IN ('FAILED','PENDING')` 인 알림을 `created_at` 내림차순·페이지네이션하여 (id, user_id, type, title, delivery_status, created_at) 와 함께 반환한다.

### REQ-NS-005 — 개별 재발송 (Event-Driven)

**When** 관리자가 오류 목록의 한 알림에 대해 재발송을 요청하면, **the system shall** 해당 알림의 `delivery_status` 를 `SENT` 로 갱신하고 갱신 결과를 반환한다.

- 재발송은 기존 발송 인프라의 멱등 갱신으로 한정한다(실제 채널 재전송 트리거는 발송 인프라 책임, 본 SPEC은 상태 정정만 수행).

### REQ-NS-006 — KPI 피드 통합 (State-Driven)

**While** SPEC-CMS-KPI-001 KPI 대시보드가 활성화된 상태에서, **the system shall** 알림 읽음율 및 오류율을 `kpi_value` 호환(dimension JSONB: `{period, metric}`) 집계 피드로 노출하여 기존 위젯(`GET /widgets/{id}/data`)이 별도 신규 위젯 타입 없이 소비할 수 있게 한다.

- KPI 대시보드 미배포(PR 미머지) 환경에서는 피드 미소비가 정상이며 통계 패널 단독으로 동작해야 한다.

### REQ-NS-007 — 관리자 권한 격리 (Unwanted Behavior)

**If** 인증되지 않았거나 관리자 역할이 없는 주체가 통계/오류/재발송 엔드포인트에 접근하면, **then the system shall** 요청을 거부하고 401/403 으로 응답하며 어떤 통계 데이터도 노출하지 않는다.

- 모든 엔드포인트 `@PreAuthorize("hasAnyRole('SUPER_ADMIN','CONTENT_ADMIN','ADMIN')")` (NOTIFICATION-CENTER-001 권한 매트릭스 계승).
- 재발송(REQ-NS-005)은 `@AuditLog` 로 감사 기록한다.

### REQ-NS-008 — 집계 성능·구간 안전성 (Ubiquitous)

The system **shall** 모든 통계 조회에서 `created_at` 구간 필터를 강제하여 전체 테이블 스캔을 방지하고, 일별 추이 구간 상한을 90일로 캡한다.

---

## 5. Exclusions (What NOT to Build)

- **이메일/SMS/푸시 발송 추적**: 외부 채널(이메일·SMS·푸시) 전송 성공·실패·바운스 추적은 범위 밖이다. 본 SPEC은 인앱(`user_notification_inbox`) 전용이다.
- **실제 채널 재전송 트리거**: REQ-NS-005 의 재발송은 `delivery_status` 상태 정정만 수행하며, 외부 발송 게이트웨이로의 실제 재전송 호출은 발송 인프라(SPEC-CMS-007 계열) 책임으로 위임한다.
- **`admin_notification`(V40) 통계**: 관리자 운영 수신함은 발송 통계 모수가 아니므로 집계하지 않는다.
- **신규 KPI 위젯 타입/엔드포인트 신설**: KPI 대시보드의 위젯 CRUD·데이터 페치 API(`/widgets/{id}/data`)는 수정하지 않으며, `kpi_value` 호환 피드만 제공한다.
- **기존 알림 센터/대시보드 코드 수정**: NOTIFICATION-CENTER-001·KPI-001 의 컬럼·엔드포인트·서비스 시그니처를 변경하지 않는다(additive-only).
- **실시간 푸시/WebSocket 갱신**: 통계 패널은 요청 기반 조회이며 실시간 스트리밍은 범위 밖이다.
- **V35 기존 행 백필**: `delivery_status` 는 nullable 추가이며 NULL=SENT 로 간주, 과거 데이터 백필을 수행하지 않는다.
- **통합 테스트(IT)**: 통합 테스트는 별도 후속 SPEC(SPEC-CMS-NOTIFICATION-STAT-IT-001 등)으로 분리한다.

---

## 6. 격차 분석 (Gap Analysis)

| 항목 | 기존(재사용) | 신규(추가 필요) |
|------|-------------|----------------|
| 발송 데이터 원천 | `user_notification_inbox`(V35): type/is_read/read_at/created_at, `idx_user_notification_inbox_user` | 없음 (재사용) |
| 발송 상태 구분 | 없음 (모든 행 = 성공 INSERT) | **V46: `delivery_status VARCHAR(10) NULL CHECK(IN 'SENT','FAILED','PENDING')` + 부분 인덱스** |
| 집계 쿼리 | KPI 패턴(`GROUP BY` + 구간 필터, GovernanceStatsMapper.xml 참조) | `NotificationStatMapper`(요약/카테고리/일별/오류 4계열 + 재발송 UPDATE) |
| 권한·감사 | NOTIFICATION-CENTER-001 `@PreAuthorize` 매트릭스, `@AuditLog` | 통계 컨트롤러에 계승 적용 |
| KPI 피드 | `kpi_value` JSONB dimension 모델(V17), `KpiValueMapper`, `/widgets/{id}/data` | 읽음율·오류율 UPSERT 집계(`kpi_value`) — 신규 테이블 불요 |
| 프런트 인프라 | `dashboardStore.ts`, `api/dashboard.ts`, `views/dashboard/DashboardMainView.vue`, vue-echarts | `api/notificationStat.ts`, `stores/notificationStatStore.ts`, `views/dashboard/NotificationStatPanel.vue` |
| 알림 센터 API | `adminNotifications.ts`, `notificationCenter.ts` | 수정 없음 |

---

## 7. 기술 접근 (Technical Approach)

### 7.1 백엔드 (Spring Boot + MyBatis + PostgreSQL)

- 패키지: `kr.co.ircp.cms.domain.notification.stat` (기존 `notification.admin` 과 분리).
- 마이그레이션 **V46** (현재 tip V45 — 추가 직전 `find .../db/migration -name 'V*.sql' | sort -V | tail` 재확인 필수):
  - `ALTER TABLE user_notification_inbox ADD COLUMN delivery_status VARCHAR(10) NULL CHECK (delivery_status IN ('SENT','FAILED','PENDING'))`
  - 오류 목록 가속 부분 인덱스: `... WHERE delivery_status IN ('FAILED','PENDING')`
- 컨트롤러: `NotificationStatController` base path `/api/v1/admin/notifications/stats`
  - `GET /summary` (REQ-NS-001), `GET /by-category` (REQ-NS-002), `GET /daily-trend` (REQ-NS-003), `GET /errors` (REQ-NS-004, 페이지네이션), `PATCH /errors/{id}/resend` (REQ-NS-005, `@AuditLog`)
- DTO: Java record (`NotificationStatSummary`, `CategoryStat`, `DailyTrendPoint`, `FailedNotificationDto`).
- Service: `NotificationStatService` 인터페이스 + `NotificationStatServiceImpl`; 일별 추이 gap-fill 은 서비스단 Java 로직.
- Mapper: `@Mapper NotificationStatMapper` + `NotificationStatMapper.xml` (요약/카테고리/일별 `GROUP BY` 집계, 구간 필터 `created_at` 강제, 오류 목록·재발송 UPDATE).
- KPI 피드(REQ-NS-006): 읽음율·오류율을 `kpi_value` 에 dimension `{period, metric}` 으로 `ON CONFLICT DO UPDATE` UPSERT (KPI SPEC 패턴 계승). KPI 미배포 시 graceful no-op.

### 7.2 프런트엔드 (Vue 3 + Pinia + TypeScript)

- `api/notificationStat.ts`: 5개 엔드포인트 클라이언트.
- `stores/notificationStatStore.ts`: 요약/카테고리/추이/오류 상태 + 재발송 액션.
- `views/dashboard/NotificationStatPanel.vue`: 요약 카드 + 카테고리 테이블/바 + 일별 추이 LineChart(vue-echarts) + 오류 목록 + 재발송 버튼. `DashboardMainView.vue` 에 패널로 통합(라우트 additive).

### 7.3 권한·감사

- 전 엔드포인트 `@PreAuthorize("hasAnyRole('SUPER_ADMIN','CONTENT_ADMIN','ADMIN')")`.
- 재발송 `@AuditLog` 기록.

---

## 8. 의존성 (Dependencies)

- **SPEC-CMS-NOTIFICATION-CENTER-001** (Completed): `user_notification_inbox`(V35) 데이터, 관리자 권한 매트릭스, `admin_notification` 의미 분리.
- **SPEC-CMS-KPI-001** (Implemented, PR #22 미머지): `kpi_value`(V17) 모델, `/widgets/{id}/data` 위젯 페치 — REQ-NS-006 KPI 피드의 소비처. **미배포 시 graceful degradation** 으로 통계 패널 단독 동작.

---

## 9. 수용 기준 요약

상세 Given-When-Then 시나리오는 `acceptance.md`, 구현 계획은 `plan.md` 참조.
