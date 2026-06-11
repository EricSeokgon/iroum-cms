---
id: SPEC-CMS-KPI-001
version: 0.1.1
status: Draft
created_at: 2026-06-11
updated: 2026-06-11
author: manager-spec
priority: high
labels: [kpi, dashboard, aggregation, excel-export, performance]
parent: SPEC-CMS-008 v0.5
related:
  - SPEC-CMS-008 (시각화 대시보드 + KPI 위젯 — 인프라 재사용 대상)
  - SPEC-CMS-007 (규칙 기반 정책 매칭 — 전환율 KPI 데이터 공급원, 옵셔널 의존)
  - SPEC-CMS-002 (RBAC — ADMIN 권한 검증)
issue_number: 21
---

# SPEC-CMS-KPI-001 플랫폼 KPI 통합 관리

## HISTORY

- v0.1 / 2026-06-11 / manager-spec / 신규 작성. SPEC-CMS-008 이 구축한 대시보드 인프라(`kpi_definition` / `kpi_value` / `kpi_value_history` / `chart_dataset_cache` / `export_history`) 위에 1) **KPI 전용 집계 로직**(기능별 이용률, 파일 다운로드 수, 정책 매칭 전환율), 2) **조건별(기간/기능/업종/지역) 멀티필터 조회**, 3) **통계 엑셀 다운로드**(SXSSFWorkbook 실구현 완성), 4) **대용량 로그/트랜잭션 집계 성능 최적화**, 5) **KPI 대시보드 위젯** 5개 격차를 정의. 신규 DB 스키마는 도입하지 않고 V17 의 KPI 테이블을 재사용하며, 사전집계용 단일 마이그레이션(V45)만 추가.

---

## 1. 개요

본 SPEC 은 SPEC-CMS-008 v0.5 가 이미 구축한 대시보드 인프라 위에 **플랫폼 핵심 성과지표(KPI) 통합 관리 격차** 를 채우는 자식 SPEC 이다.

핵심 가치:

- 운영자가 **기능별 이용률, 파일 다운로드 수, 정책 매칭 신청 전환율** 등 핵심 KPI 를 단일 화면에서 통합 조회
- **기간/기능/업종/지역 등 다양한 조건** 별로 성과를 교차 분석하고 결과를 엑셀로 내보내기
- **대용량 시스템 로그(access_log)·트랜잭션 집계** 시 파티션 제거·사전집계·캐싱으로 응답 성능 확보

본 SPEC 은 새로운 KPI 데이터 모델을 도입하지 않으며, 기존 `kpi_value` 테이블과 로그 소스를 활용하여 **집계·조회·내보내기·시각화 계층의 누락분만** 채운다.

---

## 2. 목표 및 범위

### 2.1 비즈니스 목표

- 플랫폼 운영 성과를 객관적 수치로 측정 가능하게 하여 정책·콘텐츠 의사결정 근거 제공
- 핵심 지표 3종(기능별 이용률, 파일 다운로드 수, 정책 매칭 전환율)의 자동 집계·갱신
- 조건별 성과 데이터를 엑셀로 추출하여 비개발자 보고 자료 생산성 향상

### 2.2 범위 경계

- **포함**: KPI 집계 배치, 멀티조건 조회 API, 엑셀 다운로드 실구현, 성능 최적화, KPI 대시보드 위젯
- **제외**: 신규 KPI 데이터 모델, 실시간 스트리밍 집계, ML 예측 분석, BI 도구 연동 (제10절 참조)

---

## 3. 배경 및 컨텍스트

### 3.1 SPEC-CMS-008 이 이미 제공하는 것 (재사용)

| 기능 | 위치 | 비고 |
|---|---|---|
| KPI 정의/값/이력 테이블 | `kpi_definition` / `kpi_value` / `kpi_value_history` (V17) | code, JSONB dimension, value_numeric |
| JSONB dimension GIN 인덱스 | `idx_kpi_value_dim_gin`, `idx_kpi_value_calc` | period/feature/industry/region/role |
| KPI 위젯 데이터소스 | `DashboardWidget.dataSource = KPI_VALUE` | METRIC_CARD/LINE_CHART/BAR_CHART |
| 차트 캐시 (5분 TTL) | `chart_dataset_cache` | dimension hash + role 키 |
| 비동기 엑셀 내보내기 골격 | `ExportServiceImpl` (10k행 임계값, 24h TTL, HMAC 서명 URL) | SXSSFWorkbook 은 stub |
| 사전집계 패턴 | `GovernanceStatsMapper.xml` (GROUP BY + ON CONFLICT UPSERT) | 파티션 제거 on `created_at` |
| 정책 전환율 필드 | `policy_match_stats_monthly.apply_conversion_rate` (V18) | apply_count / match_count |
| vue-echarts 차트 | `DashboardTrendChart.vue` (LineChart 모듈형) | grid-layout-plus 12-grid |

→ 본 SPEC 은 위 목록을 **변경하거나 대체하지 않는다.**

### 3.2 본 SPEC 이 채우는 격차

| 격차 | 현재 상태 | 본 SPEC 해결 방식 |
|---|---|---|
| KPI 전용 집계 로직 | `kpi_value` 테이블만 존재, 채우는 배치 부재 | `KpiAggregationJob` 일별 배치 + `KpiAggregationMapper` GROUP BY 집계 |
| 멀티조건 조회 | 위젯 단건 dimension 조회만 가능 | `AdminKpiController` 기간/기능/업종/지역 복합 필터 API |
| 엑셀 다운로드 | `ExportServiceImpl` SXSSFWorkbook stub (v0.4) | SXSSFWorkbook 실구현 + 다중 시트 청크 |
| 대용량 집계 성능 | 원본 로그 직접 스캔 시 느림 | 파티션 제거·사전집계 MV·복합 인덱스·캐시 |
| KPI 통합 대시보드 | 개별 위젯만 존재 | KPI 요약/트렌드/전환율 위젯 + 필터 패널 |

### 3.3 SPEC-CMS-007 옵셔널 의존

정책 매칭 전환율 KPI 는 `PolicyMatchStatsJob` 산출물에 의존한다. SPEC-CMS-007 미구현(`policy_matching` 테이블 부재) 시 해당 배치는 0 값을 반환하므로, 본 SPEC 은 전환율 KPI 를 **"데이터 준비 중"** 상태로 graceful 표시한다.

---

## 4. 요구사항 (EARS)

### REQ-KPI-001 KPI 집계 로직

- **REQ-KPI-001-1 (이용률 집계 — Ubiquitous)**
  시스템은 `access_log` 의 `page_url` 패턴을 기능 단위로 분류하여 기능별 이용률(`feature_views / total_views`)을 `kpi_value` 에 `dimension={period, feature}` 형태로 집계·저장해야 한다.
- **REQ-KPI-001-2 (다운로드 수 집계 — Ubiquitous)**
  시스템은 `access_log` 또는 `audit_log(action='EXPORT'/'READ')` 에서 파일 다운로드 이벤트를 식별하여 기간·기능·업종별 다운로드 수를 `kpi_value` 에 집계해야 한다.
- **REQ-KPI-001-3 (전환율 집계 — Event-driven)**
  WHEN `PolicyMatchStatsJob` 이 `policy_match_stats_monthly.apply_conversion_rate` 를 산출하면, THEN 시스템은 해당 전환율을 `kpi_value` 의 정책 매칭 전환율 KPI 로 복사·집계해야 한다.
- **REQ-KPI-001-4 (집계 배치 스케줄 — Event-driven)**
  WHEN 매일 04:00 KST 가 되면, THEN `KpiAggregationJob` 이 전일 로그를 대상으로 GROUP BY dimension 집계를 수행하고 `ON CONFLICT DO UPDATE` 멱등 UPSERT 로 `kpi_value` 를 갱신해야 한다.
- **REQ-KPI-001-5 (이력 아카이브 — Event-driven)**
  WHEN KPI 집계 배치가 `kpi_value` 를 갱신하면, THEN 시스템은 갱신 전 값을 `kpi_value_history` 에 `calculated_at` 과 함께 아카이브해야 한다. (access_log 3개월 TTL 삭제 전 영속 보존)
- **REQ-KPI-001-6 (집계 실패 격리 — Unwanted)**
  IF 특정 KPI 의 집계 쿼리가 실패하면, THEN 시스템은 해당 KPI 만 실패 처리하고 나머지 KPI 집계를 계속 진행해야 하며, 실패 KPI 는 `batch_execution_log` 에 기록해야 한다.

### REQ-KPI-002 KPI 조건별 조회

- **REQ-KPI-002-1 (멀티조건 필터 — Event-driven)**
  WHEN 운영자가 기간(period)·기능(feature)·업종(industry)·지역(region) 조건을 조합하여 KPI 조회를 요청하면, THEN 시스템은 `kpi_value.dimension` JSONB containment(`@>`) 와 동적 WHERE 절로 일치하는 KPI 값을 반환해야 한다.
- **REQ-KPI-002-2 (기간 그래뉼래리티 — Unwanted)**
  IF 조회 요청의 period 단위가 week/month/quarter/year 중 하나로 지정되면, THEN 시스템은 해당 단위로 집계된 KPI 시계열을 반환해야 한다.
- **REQ-KPI-002-3 (전환율 데이터 준비 상태 — Unwanted)**
  IF 정책 매칭 전환율 KPI 의 원본(`policy_match_stats_monthly`)에 데이터 부재(해당 기간 데이터 미존재, SPEC-CMS-007 미구현)가 감지되면, THEN 시스템은 빈 값 대신 `dataState="PREPARING"` 메타데이터를 응답에 포함하여 UI 가 "데이터 준비 중" 으로 표시하도록 해야 한다.
- **REQ-KPI-002-4 (조회 결과 페이지네이션 — Ubiquitous)**
  시스템은 KPI 조회 결과에 LIMIT 1000 safety cap 을 적용하고, 초과 시 페이지네이션 메타데이터를 제공해야 한다.
- **REQ-KPI-002-5 (빈 결과 처리 — Unwanted)**
  IF 조건에 일치하는 KPI 값이 없으면, THEN 시스템은 오류가 아닌 빈 배열 + 적용된 필터 메타데이터를 200 응답으로 반환해야 한다.

### REQ-KPI-003 엑셀 다운로드

- **REQ-KPI-003-1 (동기 내보내기 — Event-driven)**
  WHEN 운영자가 10,000행 미만의 KPI 통계 내보내기를 요청하면, THEN 시스템은 SXSSFWorkbook 으로 즉시 엑셀(.xlsx)을 생성하여 `HttpServletResponse.getOutputStream()` 스트리밍으로 응답해야 한다.
- **REQ-KPI-003-2 (비동기 내보내기 — Event-driven)**
  WHEN 내보내기 대상이 10,000행 이상이면, THEN 시스템은 `export_history` 에 PROCESSING 상태로 등록하고 비동기로 SXSSFWorkbook 파일을 생성한 뒤 HMAC-SHA256 서명 다운로드 URL(24시간 TTL)을 제공해야 한다.
- **REQ-KPI-003-3 (다중 시트 청크 — Unwanted)**
  IF 단일 시트 행 수가 1,048,576행을 초과하면, THEN 시스템은 SXSSFWorkbook 의 windowed row 방식으로 여러 시트에 분할 기록해야 한다 (XLSX 시트당 1,048,576행 한계 준수).
- **REQ-KPI-003-4 (컬럼 구성 — Ubiquitous)**
  시스템은 내보내기 엑셀에 `kpi_code, period, feature, industry, region, value_numeric, value_text, aggregated_at` 컬럼을 포함하고 1행에 헤더를 고정해야 한다.
- **REQ-KPI-003-5 (내보내기 상한 — Unwanted)**
  IF 내보내기 대상 행이 `max_export_rows`(기본 1,000,000)를 초과하면, THEN 시스템은 내보내기를 거부하고 조건 세분화 안내 메시지를 반환해야 한다.
- **REQ-KPI-003-6 (내보내기 감사 — Event-driven)**
  WHEN KPI 엑셀 내보내기가 완료되면, THEN 시스템은 `audit_log` 에 `action='EXPORT'` 로 actor·조건·행 수를 기록해야 한다.

### REQ-KPI-004 성능 최적화

- **REQ-KPI-004-1 (파티션 제거 — Ubiquitous)**
  시스템은 `access_log` 기반 집계 시 항상 `created_at` 범위 조건을 포함하여 월별 RANGE 파티션 제거(partition pruning)가 동작하도록 해야 한다.
- **REQ-KPI-004-2 (사전집계 활용 — Unwanted)**
  IF 조회 대상 KPI 가 사전집계 가능하면, THEN 시스템은 원본 로그 대신 사전집계 결과(KPI 집계 MV 또는 `*_stats_monthly`)를 조회해야 한다.
- **REQ-KPI-004-3 (Materialized View 갱신 — Event-driven)**
  WHEN KPI 집계 배치가 완료되면, THEN 시스템은 `REFRESH MATERIALIZED VIEW CONCURRENTLY kpi_aggregation_mv` 로 멀티필터 조회용 MV 를 비차단 갱신해야 한다.
- **REQ-KPI-004-4 (복합 인덱스 — Ubiquitous)**
  시스템은 멀티조건 조회를 위해 `(kpi_id, calculated_at DESC)` 및 JSONB dimension GIN 인덱스를 활용하여 인덱스 스캔으로 응답해야 한다.
- **REQ-KPI-004-5 (캐시 재활용 — Unwanted)**
  IF 동일 dimension+role 조합의 KPI 위젯 조회가 5분 이내 재요청되면, THEN 시스템은 `chart_dataset_cache` 에서 응답하여 재집계를 회피해야 한다.
- **REQ-KPI-004-6 (캐시 스레싱 방지 — Unwanted)**
  IF 고빈도 KPI 가 5분 TTL 캐시를 빈번히 무효화하면, THEN 시스템은 해당 KPI 조합에 대해 1시간 TTL 을 선택적으로 적용할 수 있어야 한다.

### REQ-KPI-005 보안 및 접근 제어

- **REQ-KPI-005-1 (ADMIN 권한 — Ubiquitous)**
  시스템은 모든 KPI 조회·집계·내보내기 엔드포인트에 대해 ADMIN 이상 역할을 요구해야 한다 (`@PreAuthorize` ADMIN 검증).
- **REQ-KPI-005-2 (내보내기 다운로드 검증 — Event-driven)**
  WHEN 사용자가 비동기 내보내기 결과를 다운로드하면, THEN 시스템은 HMAC 서명 검증 + 소유자 또는 SUPER_ADMIN 여부를 확인하고, 검증 실패 시 요청을 거부해야 한다.
- **REQ-KPI-005-3 (감사 추적 — Ubiquitous)**
  시스템은 KPI 조회·내보내기 요청을 `@AuditLog` 어노테이션으로 자동 감사 기록해야 한다.
- **REQ-KPI-005-4 (SQL 인젝션 방지 — Unwanted)**
  IF KPI 조회 필터에 DDL/DML 패턴이 포함되면, THEN 시스템은 정규식 검증으로 요청을 거부해야 한다 (DashboardWidgetServiceImpl 검증 패턴 계승).
- **REQ-KPI-005-5 (PII 비노출 — Ubiquitous)**
  시스템은 KPI 집계·내보내기 결과에 개인식별정보(원본 IP, user_id)를 포함하지 않고 집계값만 노출해야 한다 (`ip_hash` 는 집계 후 폐기).

### REQ-KPI-006 UI 대시보드 위젯

- **REQ-KPI-006-1 (KPI 요약 카드 — Ubiquitous)**
  프런트엔드는 핵심 KPI 3종을 기존 `METRIC_CARD` 위젯 타입으로 요약 표시하고 전기 대비 증감률을 함께 표기해야 한다.
- **REQ-KPI-006-2 (KPI 트렌드 차트 — Event-driven)**
  WHEN 운영자가 KPI 트렌드 위젯을 조회하면, THEN 프런트엔드는 vue-echarts `LINE_CHART` 로 기간별 시계열을 렌더링해야 한다.
- **REQ-KPI-006-3 (전환율 퍼널 — Ubiquitous)**
  프런트엔드는 정책 매칭 전환율을 `BAR_CHART` 기반 퍼널(노출→매칭→신청)로 표시해야 한다.
- **REQ-KPI-006-4 (필터 패널 — Event-driven)**
  WHEN 운영자가 KPI 필터 패널에서 기간/기능/업종/지역을 변경하면, THEN 프런트엔드는 선택 조건으로 KPI 조회 API 를 재호출하고 모든 위젯을 갱신해야 한다.
- **REQ-KPI-006-5 (데이터 준비 중 표시 — Unwanted)**
  IF KPI 응답에 `dataState="PREPARING"` 이 포함되면, THEN 프런트엔드는 0 값 차트 대신 "데이터 준비 중 (SPEC-CMS-007 의존)" 안내를 표시해야 한다.
- **REQ-KPI-006-6 (엑셀 다운로드 버튼 — Event-driven)**
  WHEN 운영자가 KPI 화면의 "엑셀 다운로드" 를 클릭하면, THEN 프런트엔드는 현재 적용된 필터 조건으로 내보내기 API 를 호출하고, 비동기 처리 시 진행률(`export_history.progress_pct`)을 폴링 표시해야 한다.

---

## 5. 데이터 모델

### 5.1 재사용 테이블 (V17, 변경 없음)

| 테이블 | 용도 | 본 SPEC 활용 |
|---|---|---|
| `kpi_definition` | KPI 메타(code, calculation_query, refresh_interval_min) | 신규 KPI 3종 정의 row 시드 |
| `kpi_value` | KPI 값(kpi_id, JSONB dimension, value_numeric/text) | 집계 결과 UPSERT 대상 |
| `kpi_value_history` | 시계열 아카이브(calculated_at) | 갱신 전 값 보존 |

### 5.2 KPI dimension 차원 정의

| 차원 | 값 예시 | 출처 |
|---|---|---|
| period | `2026-W23`, `2026-06`, `2026-Q2`, `2026` | 집계 기간 단위 |
| feature | `board`, `policy`, `search`, `download` | access_log page_url 패턴 분류 |
| industry | `manufacturing`, `service`, `it` | 사용자 프로필 업종 |
| region | `seoul`, `busan` | 사용자/사이트 지역 |
| role | `VIEWER`, `EDITOR` | 권한별 분리 (옵션) |

### 5.3 KPI 지표 정의

| KPI code | 지표 | 계산식 | 집계 주기 |
|---|---|---|---|
| `feature_usage_rate` | 기능별 이용률 | feature_views / total_views | 일별 |
| `file_download_count` | 파일 다운로드 수 | COUNT(download events) | 일별 |
| `policy_apply_conversion_rate` | 정책 매칭 신청 전환율 | apply_count / match_count | 월별 (SPEC-CMS-007 의존) |

### 5.4 신규 마이그레이션 — V45 (사전집계 MV)

신규 테이블은 도입하지 않는다. 멀티필터 조회 가속용 Materialized View 와 복합 인덱스만 단일 마이그레이션으로 추가한다.

- 파일: `V45__kpi_aggregation_mv.sql` (현재 tip V44 다음 번호; 추가 시 `find .../db/migration -name 'V*.sql' | sort -V | tail` 로 재확인)
- 내용:
  - `CREATE MATERIALIZED VIEW kpi_aggregation_mv` — `kpi_value` × `kpi_definition` JOIN, dimension 평탄화
  - `CREATE UNIQUE INDEX` on MV (CONCURRENTLY REFRESH 전제 조건)
  - `kpi_definition` 신규 KPI 3종 시드 INSERT (`ON CONFLICT DO NOTHING`)
  - 부분 인덱스: `audit_log WHERE action='EXPORT'` (내보내기 추적 가속)
- 백필: 불필요 (MV 는 첫 REFRESH 시 채워짐)

---

## 6. 시스템 설계

### 6.1 백엔드 아키텍처

```
[Controller]  AdminKpiController (조회), KpiExportController (내보내기)
                    │  @PreAuthorize(ADMIN), @AuditLog
[Service]     KpiQueryService(+Impl) ── KpiAggregationService(+Impl) ── KpiExportService(+Impl)
                    │                          │  (배치)                      │  SXSSFWorkbook
[Mapper]      KpiQueryMapper(+XML)    KpiAggregationMapper(+XML)    (kpi_value 조회)
                    │
[DB]          kpi_value / kpi_value_history / kpi_aggregation_mv / access_log(파티션) / policy_match_stats_monthly
[Batch]       KpiAggregationJob (@Scheduled "0 0 4 * * *" 일별 04:00 KST)
```

- 패키지 루트: `kr.co.ircp.cms.domain.dashboard.kpi` (대시보드 도메인 하위, 기존 `kpi_value` 인프라와 응집)
- DTO 는 Java record, Service 는 인터페이스 + `*Impl` 분리, `@Mapper` 인터페이스 + XML

### 6.2 프런트엔드 아키텍처

```
views/dashboard/KpiDashboardView.vue   (KPI 통합 화면)
 ├── components/dashboard/KpiSummaryCards.vue   (METRIC_CARD ×3)
 ├── components/dashboard/KpiTrendChart.vue     (vue-echarts LINE_CHART)
 ├── components/dashboard/KpiConversionFunnel.vue (BAR_CHART)
 └── components/dashboard/KpiFilterPanel.vue    (기간/기능/업종/지역)
stores/kpiStore.ts        (필터 상태 + KPI 응답 캐시)
api/kpi.ts                (조회/내보내기 API 클라이언트)
```

### 6.3 성능 전략

| 전략 | 적용 위치 | 효과 |
|---|---|---|
| 파티션 제거 | `access_log` 집계 시 `created_at` 범위 필수 | 스캔 대상 월 파티션만 읽음 |
| 사전집계 MV | `kpi_aggregation_mv` | 멀티필터 조회 시 원본 로그 미스캔 |
| CONCURRENTLY REFRESH | 배치 완료 직후 | 조회 무중단 MV 갱신 |
| 복합 + GIN 인덱스 | `(kpi_id, calculated_at)`, dimension GIN | 인덱스 스캔 |
| 5분/1시간 TTL 캐시 | `chart_dataset_cache` | 재집계 회피 |
| 윈도우 함수 | `ROW_NUMBER() OVER (PARTITION BY dimension ORDER BY calculated_at DESC)` | 최신 KPI 추출 |

---

## 7. API 명세

base path: `/api/v1/admin/kpi`

| Method | Path | 설명 | 권한 | REQ |
|---|---|---|---|---|
| GET | `/values` | 멀티조건 KPI 조회 (query: period, feature, industry, region, granularity) | ADMIN | REQ-KPI-002-1~5 |
| GET | `/summary` | 핵심 KPI 3종 요약 + 증감률 | ADMIN | REQ-KPI-006-1 |
| GET | `/trend` | KPI 시계열 트렌드 (query: kpiCode, from, to, granularity) | ADMIN | REQ-KPI-006-2 |
| GET | `/conversion-funnel` | 정책 매칭 전환율 퍼널 | ADMIN | REQ-KPI-006-3, REQ-KPI-002-3 |
| POST | `/export` | KPI 통계 엑셀 내보내기 (body: 필터 조건) | ADMIN | REQ-KPI-003-1~6 |
| GET | `/export/{exportId}/status` | 비동기 내보내기 진행률 폴링 | ADMIN(소유자) | REQ-KPI-006-6 |
| GET | `/export/{exportId}/download` | 서명 URL 다운로드 | ADMIN(소유자)/SUPER_ADMIN | REQ-KPI-005-2 |
| POST | `/aggregate` | KPI 집계 수동 트리거 (운영용) | SUPER_ADMIN | REQ-KPI-001-4 |

응답 스키마 예시 (`GET /values`):

```json
{
  "filters": { "period": "2026-06", "feature": "board", "granularity": "month" },
  "items": [
    {
      "kpiCode": "feature_usage_rate",
      "dimension": { "period": "2026-06", "feature": "board" },
      "valueNumeric": 0.342,
      "aggregatedAt": "2026-06-11T04:00:00Z",
      "dataState": "READY"
    }
  ],
  "page": { "limit": 1000, "total": 1, "hasMore": false }
}
```

전환율 데이터 준비 상태 예시 (`GET /conversion-funnel`):

```json
{
  "kpiCode": "policy_apply_conversion_rate",
  "dataState": "PREPARING",
  "message": "정책 매칭 데이터 준비 중 (SPEC-CMS-007 의존)",
  "stages": []
}
```

---

## 8. 수용 기준 (Acceptance Criteria)

상세 Given/When/Then 시나리오는 `acceptance.md` 참조. 요약:

- **AC-001**: KpiAggregationJob 이 access_log 를 기능별 이용률로 집계하여 kpi_value 에 UPSERT 한다.
- **AC-002**: 집계 배치가 갱신 전 값을 kpi_value_history 에 아카이브한다.
- **AC-003**: 특정 KPI 집계 실패 시 나머지 KPI 집계가 계속 진행된다.
- **AC-004**: 멀티조건(기간+기능+업종) 필터가 JSONB containment 로 정확히 일치 결과를 반환한다.
- **AC-005**: 일치 결과 없을 때 빈 배열 + 필터 메타를 200 으로 반환한다.
- **AC-006**: SPEC-CMS-007 미구현 시 전환율 KPI 가 dataState="PREPARING" 으로 응답된다.
- **AC-007**: 10,000행 미만 내보내기가 SXSSFWorkbook 으로 동기 스트리밍된다.
- **AC-008**: 10,000행 이상 내보내기가 비동기 처리되고 서명 URL 을 반환한다.
- **AC-009**: 100만 행 초과 시 다중 시트로 분할 기록된다.
- **AC-010**: max_export_rows 초과 시 내보내기가 거부된다.
- **AC-011**: access_log 집계 쿼리가 created_at 범위로 파티션 제거를 수행한다.
- **AC-012**: 배치 완료 후 kpi_aggregation_mv 가 CONCURRENTLY REFRESH 된다.
- **AC-013**: 동일 dimension+role 5분 내 재조회 시 캐시에서 응답한다.
- **AC-014**: 비ADMIN 역할의 KPI 엔드포인트 접근이 403 으로 거부된다.
- **AC-015**: KPI 내보내기가 audit_log 에 action='EXPORT' 로 기록된다.
- **AC-016**: 필터 패널 변경 시 모든 위젯이 갱신된다.

---

## 9. 의존성

| 의존 대상 | 유형 | 비고 |
|---|---|---|
| SPEC-CMS-008 (대시보드) | 강한 의존 (인프라 재사용) | kpi_value, chart_dataset_cache, ExportServiceImpl, vue-echarts |
| SPEC-CMS-007 (정책 매칭) | 옵셔널 의존 | policy_match_stats_monthly 미구현 시 전환율 = PREPARING |
| SPEC-CMS-002 (RBAC) | 강한 의존 | ADMIN/SUPER_ADMIN 권한 검증 |
| Apache POI (SXSSFWorkbook) | 라이브러리 | 기존 의존성 (ExportServiceImpl 에 포함) |

---

## 10. Exclusions (What NOT to Build)

본 SPEC 이 의도적으로 다루지 않는 항목:

- **신규 KPI 데이터 모델 도입**: V17 의 `kpi_definition`/`kpi_value`/`kpi_value_history` 재사용. 새 KPI 저장 테이블 신설 안 함.
- **실시간 스트리밍 집계**: 일별/월별 배치 집계만. WebSocket/Kafka 기반 실시간 KPI 는 비범위.
- **ML 기반 예측 분석**: KPI 추세 예측·이상 탐지는 SPEC-CMS-AI 트랙 별도 처리.
- **외부 BI 도구 연동**: Tableau/PowerBI/Superset 커넥터 미포함. 엑셀 내보내기로 대체.
- **로그 스키마 변경**: access_log/audit_log/search_log 는 읽기 전용 소스. 컬럼 변경·트리거 추가 안 함.
- **PDF 차트 내보내기**: 1차 출시 엑셀만. PDF 는 v0.2+ 검토.
- **임계값 알림(WebSocket)**: KPI 임계 초과 실시간 알림은 비범위. SPEC-CMS-NOTIFICATION-CENTER-001 연계 차후.
- **사용자별 KPI 대시보드 개인화**: 위젯 가시성/테마는 SPEC-CMS-DASHBOARD-PERSONALIZE-001 영역.
- **KPI 정의 동적 편집 UI**: kpi_definition 은 시드/마이그레이션으로 관리. 런타임 KPI 정의 CRUD 비범위.
- **access_log 보존 정책 변경**: 3개월 TTL 유지. KPI 는 집계 후 history 아카이브로 대응.

---

## 11. 위험 및 대응

| ID | 위험 | 영향 | 대응 |
|---|---|---|---|
| RISK-KPI-01 | PolicyMatchStatsJob stub (SPEC-CMS-007 미구현) → 전환율 = 0 | KPI 오해 소지 | `dataState="PREPARING"` UI 표시, 의존성 명시 (REQ-KPI-002-3) |
| RISK-KPI-02 | ExportServiceImpl SXSSFWorkbook stub (v0.4, 미구현) | 엑셀 다운로드 불가 | 본 SPEC 에서 SXSSFWorkbook 실구현 완성 포함 (REQ-KPI-003) |
| RISK-KPI-03 | JSONB indexOf 파싱 취약 (DashboardWidgetServiceImpl) | dimension 파싱 오류 | Jackson ObjectMapper 안전 파싱으로 전환 |
| RISK-KPI-04 | access_log 3개월 TTL 삭제 → 집계 누락 | 과거 KPI 손실 | 집계 배치 완료 후 kpi_value_history 아카이브 필수 (REQ-KPI-001-5) |
| RISK-KPI-05 | StreamingResponseBody 내보내기 상한 부재 | 메모리/디스크 고갈 | max_export_rows = 1,000,000 cap (REQ-KPI-003-5) |
| RISK-KPI-06 | 5분 캐시 TTL 고빈도 KPI 스레싱 | 캐시 효율 저하 | 선택적 1시간 TTL 적용 (REQ-KPI-004-6) |
| RISK-KPI-07 | MV CONCURRENTLY REFRESH 전제 UNIQUE 인덱스 누락 | REFRESH 실패 | V45 에 MV UNIQUE 인덱스 선행 생성 |

---

## 12. 검증 체크리스트

- [ ] V45 마이그레이션이 단일 파일이며 V44 다음 번호
- [ ] 신규 KPI 저장 테이블을 만들지 않고 kpi_value(V17) 재사용
- [ ] EARS 5 패턴 사용 (Ubiquitous/Event-driven/State-driven/Unwanted/Optional)
- [ ] 모든 KPI 엔드포인트가 ADMIN 이상 권한 검증
- [ ] access_log 집계가 created_at 파티션 제거 조건 포함
- [ ] SXSSFWorkbook 실구현 (stub 제거)
- [ ] 전환율 KPI 가 SPEC-CMS-007 미구현 시 PREPARING graceful 처리
- [ ] Exclusions 절에 최소 5개 항목 명시 (현재 10개)
- [ ] SPEC-CMS-008 의 어떤 컬럼/API 도 변경하지 않음 (additive only)
