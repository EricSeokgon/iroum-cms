---
id: SPEC-CMS-NOTIFICATION-STAT-001
title: 관리자 알림 발송 통계 대시보드 — 구현 계획
version: 1.0.0
created_at: 2026-06-11
updated_at: 2026-06-11
---

# 구현 계획 (Implementation Plan) — SPEC-CMS-NOTIFICATION-STAT-001

## 1. 기술 접근 요약

`user_notification_inbox`(V35)를 발송 통계 단일 원천으로 삼고, 발송 성공/실패 구분을 위해 **단일 additive 컬럼**(`delivery_status`)만 추가한다(V46). 집계는 KPI SPEC의 `GROUP BY` + 구간 필터 패턴을 계승하고, KPI 위젯 연동은 `kpi_value`(V17) UPSERT 피드로 신규 테이블 없이 구현한다. 프런트는 기존 대시보드 인프라(`dashboardStore`/vue-echarts)에 통계 패널을 additive 통합한다.

## 2. 마일스톤 (우선순위 기반, 시간 추정 없음)

### Milestone M1 — 데이터 모델 (Priority: High)

- V46 마이그레이션: `user_notification_inbox.delivery_status VARCHAR(10) NULL` + CHECK + 오류 부분 인덱스.
- 추가 직전 `find backend/src/main/resources/db/migration -name 'V*.sql' | sort -V | tail` 로 tip 재확인(현재 V45 → V46).
- NULL=SENT 시맨틱 문서화(컬럼 COMMENT).

### Milestone M2 — 집계 백엔드 (Priority: High)

- 패키지 `kr.co.ircp.cms.domain.notification.stat` 신설.
- `NotificationStatMapper` + XML: 요약/카테고리/일별 집계 + 오류 목록 + 재발송 UPDATE. `created_at` 구간 필터 강제.
- DTO record 4종, `NotificationStatService(+Impl)`: 일별 추이 gap-fill 서비스 로직.
- `NotificationStatController`: 5 엔드포인트, NOTIFICATION-CENTER-001 권한 매트릭스 계승, 재발송 `@AuditLog`.

### Milestone M3 — KPI 피드 통합 (Priority: Medium)

- 읽음율·오류율을 `kpi_value`(V17) dimension `{period, metric}` 으로 `ON CONFLICT DO UPDATE` UPSERT.
- KPI 대시보드 미배포 환경 graceful no-op 보장(피드 미소비 정상 동작).

### Milestone M4 — 프런트 패널 (Priority: Medium)

- `api/notificationStat.ts`, `stores/notificationStatStore.ts`.
- `views/dashboard/NotificationStatPanel.vue`: 요약 카드·카테고리 표/바·일별 추이 LineChart·오류 목록·재발송 버튼.
- `DashboardMainView.vue` 에 패널 additive 통합(라우트/탭 추가, 기존 위젯 무수정).

### Milestone M5 — 검증·문서 (Priority: Low)

- 수동 스모크(요약/카테고리/추이/오류/재발송 happy path), 권한 거부 확인.
- 통합 테스트는 별도 후속 SPEC으로 분리(본 SPEC 범위 밖).

## 3. 재사용 자산 (수정 금지)

- `user_notification_inbox`(V35), `idx_user_notification_inbox_user` — 컬럼 additive 만.
- `kpi_value`(V17), `KpiValueMapper`, `/api/v1/dashboard/widgets/{id}/data` — 무수정, 피드 공급만.
- NOTIFICATION-CENTER-001 권한 매트릭스(`hasAnyRole('SUPER_ADMIN','CONTENT_ADMIN','ADMIN')`), `@AuditLog`.
- 프런트 `dashboardStore.ts`, vue-echarts LineChart, `DashboardMainView.vue`.

## 4. 리스크 및 완화

| 리스크 | 영향 | 완화 |
|--------|------|------|
| KPI SPEC(PR #22) 미머지 상태 | KPI 피드 소비처 부재 | REQ-NS-006 graceful degradation, 통계 패널 단독 동작 |
| V35에 발송 실패 데이터가 실제로 없음 | 오류 목록이 항상 비어 보일 수 있음 | NULL=SENT 시맨틱 명시, 발송 인프라가 향후 FAILED/PENDING 기록 시 자동 노출 |
| 마이그레이션 번호 충돌 | Flyway 실패 | 추가 직전 tip 재확인 규약(M1) |
| 전체 테이블 스캔 | 통계 응답 지연 | `created_at` 구간 필터 강제(REQ-NS-008) + 오류 부분 인덱스 |
| `admin_notification` 혼용 | 발송 모수 오염 | spec.md §1.1 [HARD] 분리 명시 |

## 5. @MX 태그 대상

- `NotificationStatService.summarize/dailyTrend` — 구간 필터·gap-fill 불변식 → `@MX:ANCHOR`(REASON 필수).
- `NotificationStatMapper.resend` 재발송 UPDATE — 상태 정정 한정 → `@MX:NOTE`.
- KPI UPSERT 피드 — KPI 미배포 graceful 분기 → `@MX:NOTE`.
