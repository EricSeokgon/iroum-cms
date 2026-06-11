---
id: SPEC-CMS-KPI-001
type: spec-compact
version: 0.1.1
created: 2026-06-11
author: manager-spec
---

# SPEC-CMS-KPI-001 플랫폼 KPI 통합 관리 (압축)

상태: Draft / 우선순위: high / labels: [kpi, dashboard, aggregation, excel-export, performance] / 부모: SPEC-CMS-008

## 핵심
SPEC-CMS-008 대시보드 인프라 위 KPI 집계·조회·내보내기·시각화 격차. 신규 KPI 테이블 미도입(V17 `kpi_value` 재사용), 사전집계 MV(V45)만 추가.

## 재사용 (변경 금지)
- `kpi_definition`/`kpi_value`/`kpi_value_history` (V17), `idx_kpi_value_dim_gin`, `idx_kpi_value_calc`
- `chart_dataset_cache` (5분 TTL), `ExportServiceImpl` (10k행 임계, 24h TTL, HMAC 서명; SXSSFWorkbook stub → 본 SPEC 완성)
- `GovernanceStatsMapper.xml` (GROUP BY + ON CONFLICT UPSERT, created_at 파티션 제거)
- `policy_match_stats_monthly.apply_conversion_rate` (V18), vue-echarts + grid-layout-plus

## KPI 지표
| code | 계산 | 주기 |
|---|---|---|
| feature_usage_rate | feature_views/total_views | 일별 |
| file_download_count | COUNT(download events) | 일별 |
| policy_apply_conversion_rate | apply_count/match_count | 월별 (SPEC-CMS-007 의존) |

dimension: period(week/month/quarter/year), feature, industry, region, role

## 요구사항 (EARS)

REQ-KPI-001 집계: 001-1 이용률(Ubiq) / 001-2 다운로드수(Ubiq) / 001-3 전환율복사(Event) / 001-4 일별04:00배치 UPSERT(Event) / 001-5 history아카이브(Event) / 001-6 실패격리(Unwanted)
REQ-KPI-002 조회: 002-1 멀티조건 JSONB`@>`(Event) / 002-2 granularity(Unwanted) / 002-3 PREPARING 데이터부재(Unwanted) / 002-4 LIMIT1000(Ubiq) / 002-5 빈결과200(Unwanted)
REQ-KPI-003 내보내기: 003-1 동기<10k SXSSF스트리밍(Event) / 003-2 비동기≥10k 서명URL(Event) / 003-3 다중시트>1,048,576행(Unwanted) / 003-4 컬럼고정(Ubiq) / 003-5 max_export_rows=1M거부(Unwanted) / 003-6 audit EXPORT(Event)
REQ-KPI-004 성능: 004-1 파티션제거 created_at(Ubiq) / 004-2 사전집계(Unwanted) / 004-3 MV CONCURRENTLY REFRESH(Event) / 004-4 복합+GIN인덱스(Ubiq) / 004-5 5분캐시(Unwanted) / 004-6 1시간TTL스레싱방지(Unwanted)
REQ-KPI-005 보안: 005-1 ADMIN권한(Ubiq) / 005-2 HMAC+소유자검증(Event) / 005-3 @AuditLog(Ubiq) / 005-4 DDL/DML거부(Unwanted) / 005-5 PII비노출(Ubiq)
REQ-KPI-006 UI: 006-1 METRIC_CARD요약(Ubiq) / 006-2 LINE_CHART트렌드(Event) / 006-3 BAR_CHART퍼널(Ubiq) / 006-4 필터패널갱신(Event) / 006-5 PREPARING안내(Unwanted) / 006-6 엑셀버튼+진행률폴링(Event)

## API (base `/api/v1/admin/kpi`, ADMIN)
GET `/values` (멀티조건) / GET `/summary` / GET `/trend` / GET `/conversion-funnel` / POST `/export` / GET `/export/{id}/status` / GET `/export/{id}/download` (소유자/SUPER_ADMIN) / POST `/aggregate` (SUPER_ADMIN)

응답 메타: `dataState` = READY|PREPARING

## 아키텍처
- 패키지: `kr.co.ircp.cms.domain.dashboard.kpi` (controller/service+Impl/mapper+XML/dto record/job)
- Batch: `KpiAggregationJob` @Scheduled "0 0 4 * * *"
- 프런트: `views/dashboard/KpiDashboardView.vue` + KpiSummaryCards/KpiTrendChart/KpiConversionFunnel/KpiFilterPanel + `stores/kpiStore.ts` + `api/kpi.ts`

## 파일
신규 마이그레이션: `V45__kpi_aggregation_mv.sql` (MV + UNIQUE인덱스 + audit_log EXPORT 부분인덱스 + kpi_definition 시드 3종)
변경: `ExportServiceImpl.java` (SXSSFWorkbook stub→실구현)

## AC (acceptance.md)
AC-001 이용률집계 / AC-002 history아카이브 / AC-003 실패격리 / AC-004 멀티조건 / AC-005 빈결과200 / AC-006 PREPARING(데이터부재) / AC-007 동기내보내기 / AC-008 비동기서명URL / AC-009 다중시트 / AC-010 상한거부(400) / AC-011 파티션제거 / AC-012 MV REFRESH / AC-013 캐시히트 / AC-014 비ADMIN403 / AC-015 EXPORT감사 / AC-016 필터갱신 / AC-017 다운로드수집계 / AC-018 그래뉼래리티 / AC-019 LIMIT1000 / AC-020 HMAC검증(400/403) / AC-021 SQL인젝션거부(400) / AC-022 PII비노출
테스트: AdminKpiControllerIT, KpiAggregationServiceImplIT, KpiExportServiceImplIT, KpiPerformanceIT (AbstractIntegrationTest 상속)

## 의존성
SPEC-CMS-008(강, 인프라) / SPEC-CMS-007(옵션, 미구현 시 전환율 PREPARING) / SPEC-CMS-002(강, ADMIN)

## Exclusions
신규 KPI 테이블 / 실시간 스트리밍 집계 / ML 예측 / 외부 BI 연동 / 로그 스키마 변경 / PDF 내보내기(v0.2+) / WebSocket 알림 / 대시보드 개인화(별도 SPEC) / KPI 정의 런타임 CRUD / access_log TTL 변경

## 위험
RISK-01 SPEC-CMS-007 stub→PREPARING / RISK-02 SXSSFWorkbook stub→본SPEC실구현 / RISK-03 JSONB indexOf→Jackson / RISK-04 access_log 3개월TTL→history아카이브 / RISK-05 내보내기상한→1M cap / RISK-06 캐시스레싱→1시간TTL / RISK-07 MV REFRESH→UNIQUE인덱스선행
