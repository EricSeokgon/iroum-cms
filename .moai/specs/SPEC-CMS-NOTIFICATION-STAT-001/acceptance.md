---
id: SPEC-CMS-NOTIFICATION-STAT-001
title: 관리자 알림 발송 통계 대시보드 — 수용 기준
version: 1.0.0
created_at: 2026-06-11
updated_at: 2026-06-11
---

# 수용 기준 (Acceptance Criteria) — SPEC-CMS-NOTIFICATION-STAT-001

각 시나리오는 Given-When-Then 형식이며 요구사항(REQ-NS-xxx)에 매핑된다.

## AC-001 — 발송 현황 요약 (REQ-NS-001)

- **Given** `user_notification_inbox` 에 today/7일/30일 구간 발송 데이터가 존재할 때
- **When** 관리자가 `GET /api/v1/admin/notifications/stats/summary` 를 호출하면
- **Then** today/7일/30일 각 구간의 총 발송 수, 읽음율(소수 2자리), 미읽음 수, 오류 수(`delivery_status IN ('FAILED','PENDING')`)가 단일 응답으로 반환된다.

## AC-002 — 카테고리별 통계 (REQ-NS-002)

- **Given** 서로 다른 `type` 의 알림이 발송되어 있을 때
- **When** 관리자가 `GET /stats/by-category?from=...&to=...` 를 호출하면
- **Then** type 별 발송 건수·읽음 건수가 발송 건수 내림차순으로 반환되며, 구간 미지정 시 최근 30일이 기본 적용된다.

## AC-003 — 일별 추이 gap-fill (REQ-NS-003)

- **Given** 최근 30일 중 일부 일자에만 발송이 있는 상태에서
- **When** 관리자가 `GET /stats/daily-trend` 를 호출하면
- **Then** 발송 0건 일자도 0으로 채워진 연속 시계열(일자 오름차순, 발송·읽음 건수)이 반환된다.

## AC-004 — 일별 추이 구간 상한 (REQ-NS-003, REQ-NS-008)

- **Given** 관리자가 `days=120` 처럼 90을 초과하는 구간을 요청할 때
- **When** `GET /stats/daily-trend?days=120` 를 호출하면
- **Then** 구간이 90일로 캡되어 처리되며 전체 테이블 스캔이 발생하지 않는다.

## AC-005 — 오류 목록 (REQ-NS-004)

- **Given** `delivery_status` 가 `FAILED` 또는 `PENDING` 인 알림이 존재할 때
- **When** 관리자가 `GET /stats/errors?page=0&size=20` 를 호출하면
- **Then** 해당 알림이 `created_at` 내림차순·페이지네이션되어 (id, user_id, type, title, delivery_status, created_at) 와 함께 반환된다.

## AC-006 — 개별 재발송 (REQ-NS-005)

- **Given** `delivery_status='FAILED'` 인 알림 1건이 있을 때
- **When** 관리자가 `PATCH /stats/errors/{id}/resend` 를 호출하면
- **Then** 해당 알림의 `delivery_status` 가 `SENT` 로 갱신되고 갱신 결과가 반환되며, `@AuditLog` 감사 기록이 남는다.

## AC-007 — KPI 피드 (정상) (REQ-NS-006)

- **Given** SPEC-CMS-KPI-001 KPI 대시보드가 배포되어 활성일 때
- **When** 알림 통계 집계가 수행되면
- **Then** 읽음율·오류율이 `kpi_value` 에 dimension `{period, metric}` 으로 UPSERT되어 기존 위젯(`GET /widgets/{id}/data`)이 신규 위젯 타입 없이 소비할 수 있다.

## AC-008 — KPI 미배포 graceful (REQ-NS-006)

- **Given** KPI 대시보드(PR #22)가 미머지/미배포 상태일 때
- **When** 관리자가 통계 패널에 진입하면
- **Then** KPI 피드 미소비는 정상이며, 요약·카테고리·추이·오류 패널은 오류 없이 단독 동작한다.

## AC-009 — 권한 격리 (REQ-NS-007)

- **Given** 인증되지 않았거나 관리자 역할이 없는 주체가
- **When** 임의의 `/stats/**` 엔드포인트에 접근하면
- **Then** 401(미인증)/403(권한부족)으로 거부되고 어떤 통계 데이터도 노출되지 않는다.

## AC-010 — additive-only 보존 (REQ-NS-008, 전역)

- **Given** 본 SPEC 구현 후
- **When** 기존 알림 센터/KPI/대시보드 동작을 재확인하면
- **Then** NOTIFICATION-CENTER-001·KPI-001 의 컬럼·엔드포인트·서비스 시그니처가 변경되지 않았고, `user_notification_inbox` 기존 행은 `delivery_status=NULL`(=SENT)로 보존되며 백필이 발생하지 않는다.

---

## Definition of Done

- [ ] V46 단일 마이그레이션 적용(추가 직전 tip 재확인), NULL=SENT COMMENT 포함
- [ ] 5개 엔드포인트(summary/by-category/daily-trend/errors/resend) 구현 및 권한 매트릭스 계승
- [ ] 일별 추이 gap-fill + 90일 캡 + `created_at` 구간 필터 강제
- [ ] KPI `kpi_value` UPSERT 피드 + 미배포 graceful no-op
- [ ] 프런트 통계 패널(요약·카테고리·LineChart·오류·재발송) `DashboardMainView` additive 통합
- [ ] 재발송 `@AuditLog` 기록
- [ ] 기존 알림 센터/KPI/대시보드 코드 무수정(additive-only) 확인
- [ ] AC-001 ~ AC-010 전부 충족
- [ ] 통합 테스트는 후속 SPEC으로 분리(본 SPEC 비포함)

## Quality Gate

- 모든 통계 쿼리 `created_at` 구간 필터 존재(전체 스캔 방지)
- `admin_notification` 발송 모수 혼용 없음(`user_notification_inbox` 단일 원천)
- 이메일/SMS/푸시 추적·실채널 재전송 미포함(Exclusions 준수)
