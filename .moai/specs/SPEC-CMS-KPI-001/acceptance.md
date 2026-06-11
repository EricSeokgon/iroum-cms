---
id: SPEC-CMS-KPI-001
type: acceptance
version: 0.1.0
created: 2026-06-11
updated: 2026-06-11
author: manager-spec
---

# SPEC-CMS-KPI-001 수용 기준 (Acceptance Criteria)

Given/When/Then 형식. 테스트 클래스 명명은 프로젝트 규약(`Admin{Domain}ControllerIT`, `{Service}ImplIT`)을 따르며 `kr.co.ircp.cms.integration.AbstractIntegrationTest` 를 상속한다.

## REQ-KPI-001 KPI 집계 로직

### AC-001 기능별 이용률 집계 (KpiAggregationServiceImplIT)
- **Given** access_log 파티션에 board/policy/search page_url 접근 로그가 시드되어 있고
- **When** KpiAggregationJob 이 전일 대상으로 집계를 수행하면
- **Then** kpi_value 에 `kpiCode=feature_usage_rate`, `dimension={period, feature}`, `valueNumeric=feature_views/total_views` 가 UPSERT 된다.

### AC-002 이력 아카이브 (KpiAggregationServiceImplIT)
- **Given** kpi_value 에 기존 KPI 값이 존재하고
- **When** 집계 배치가 동일 dimension 의 값을 갱신하면
- **Then** 갱신 전 값이 `calculated_at` 과 함께 kpi_value_history 에 1행 추가된다.

### AC-003 집계 실패 격리 (KpiAggregationServiceImplIT)
- **Given** 3개 KPI 중 1개의 집계 쿼리가 예외를 던지도록 설정되고
- **When** 집계 배치가 실행되면
- **Then** 나머지 2개 KPI 는 정상 UPSERT 되고, 실패 KPI 는 batch_execution_log 에 실패로 기록된다.

### AC-017 파일 다운로드 수 집계 (KpiAggregationServiceImplIT)
- **Given** audit_log 에 `action='DOWNLOAD'` 인 파일 다운로드 이벤트가 시드되어 있고
- **When** KpiAggregationJob 이 전일 대상으로 집계를 수행하면
- **Then** kpi_value 에 `kpiCode=file_download_count`, `dimension={period, feature}`, `valueNumeric=download_count` 가 UPSERT 된다.

## REQ-KPI-002 KPI 조건별 조회

### AC-004 멀티조건 필터 (AdminKpiControllerIT)
- **Given** kpi_value 에 `{period:2026-06, feature:board, industry:it}` 등 다양한 dimension 값이 존재하고
- **When** `GET /api/v1/admin/kpi/values?period=2026-06&feature=board&industry=it` 를 ADMIN 으로 요청하면
- **Then** JSONB containment(`@>`) 로 정확히 일치하는 KPI 만 200 으로 반환된다.

### AC-018 기간 그래뉼래리티 (AdminKpiControllerIT)
- **Given** kpi_value 에 week/month/quarter/year 단위 집계 데이터가 시드되어 있고
- **When** `GET /api/v1/admin/kpi/values?periodUnit=week` 로 조회하면
- **Then** week 단위로 집계된 KPI 시계열만 반환되고 month/quarter/year 데이터는 포함되지 않는다.

### AC-019 조회 결과 LIMIT 안전 상한 (AdminKpiControllerIT)
- **Given** 조건에 일치하는 kpi_value 가 1,000행을 초과하고
- **When** 페이지네이션 파라미터 없이 조회하면
- **Then** 최대 1,000행이 반환되고 응답에 `totalCount`, `hasMore=true` 페이지네이션 메타데이터가 포함된다.

### AC-005 빈 결과 처리 (AdminKpiControllerIT)
- **Given** 조건에 일치하는 kpi_value 가 없고
- **When** 존재하지 않는 조합으로 조회하면
- **Then** 404/500 이 아닌 200 + 빈 `items` 배열 + 적용된 `filters` 메타가 반환된다.

### AC-006 전환율 데이터 준비 상태 (AdminKpiControllerIT)
- **Given** policy_match_stats_monthly 에 해당 기간 데이터가 존재하지 않고(SPEC-CMS-007 미구현)
- **When** `GET /api/v1/admin/kpi/conversion-funnel` 를 요청하면
- **Then** 응답에 `dataState="PREPARING"` 과 안내 메시지가 포함되고 빈 값이 KPI 로 노출되지 않는다.

## REQ-KPI-003 엑셀 다운로드

### AC-007 동기 내보내기 (KpiExportServiceImplIT)
- **Given** 조건에 일치하는 KPI 행이 10,000행 미만이고
- **When** `POST /api/v1/admin/kpi/export` 를 요청하면
- **Then** SXSSFWorkbook 으로 생성된 .xlsx 가 `Content-Disposition: attachment` 스트리밍으로 즉시 응답되고, 1행에 헤더(kpi_code, period, feature, industry, region, value_numeric, value_text, aggregated_at)가 고정된다.

### AC-008 비동기 내보내기 (KpiExportServiceImplIT)
- **Given** 대상 행이 10,000행 이상이고
- **When** 내보내기를 요청하면
- **Then** export_history 에 PROCESSING 상태로 등록되고, 응답에 exportId 가 반환되며, 완료 후 `GET /export/{id}/download` 가 HMAC-SHA256 서명 URL(24h TTL)로 다운로드를 제공한다.

### AC-009 다중 시트 청크 (KpiExportServiceImplIT)
- **Given** 단일 시트 행 수가 1,048,576행을 초과하고
- **When** 내보내기가 실행되면
- **Then** SXSSFWorkbook windowed row 방식으로 여러 시트에 분할 기록되고 모든 행이 누락 없이 포함된다.

### AC-010 내보내기 상한 (KpiExportServiceImplIT)
- **Given** 대상 행이 max_export_rows(1,000,000)를 초과하고
- **When** 내보내기를 요청하면
- **Then** 내보내기가 거부되고 조건 세분화 안내 메시지가 400 으로 반환된다.

## REQ-KPI-004 성능 최적화

### AC-011 파티션 제거 (KpiPerformanceIT)
- **Given** access_log 가 월별 RANGE 파티션으로 구성되어 있고
- **When** 집계 쿼리에 `created_at >= ... AND created_at < ...` 범위가 포함되어 `EXPLAIN ANALYZE` 를 실행하면
- **Then** 쿼리 플랜에 해당 월 파티션만 스캔(partition pruning)되고 전체 파티션 스캔이 없다.

### AC-012 MV CONCURRENTLY REFRESH (KpiPerformanceIT)
- **Given** kpi_aggregation_mv 에 UNIQUE 인덱스가 존재하고
- **When** 집계 배치 완료 후 `REFRESH MATERIALIZED VIEW CONCURRENTLY kpi_aggregation_mv` 를 실행하면
- **Then** 조회 차단 없이 MV 가 최신 kpi_value 로 갱신된다.

### AC-013 캐시 히트 (AdminKpiControllerIT)
- **Given** 동일 dimension+role 조합의 KPI 위젯 조회가 5분 이내 발생하고
- **When** 두 번째 조회를 요청하면
- **Then** chart_dataset_cache 에서 응답되어 재집계가 수행되지 않는다 (집계 쿼리 미실행 검증).

## REQ-KPI-005 보안 및 접근 제어

### AC-014 ADMIN 권한 (AdminKpiControllerIT)
- **Given** VIEWER/EDITOR 등 비ADMIN 사용자로 인증되고
- **When** KPI 조회/내보내기 엔드포인트에 접근하면
- **Then** 403 Forbidden 으로 거부된다.

### AC-020 비동기 내보내기 다운로드 HMAC 검증 (KpiExportServiceImplIT)
- **Given** 비동기 내보내기가 완료되어 HMAC-SHA256 서명 URL 이 발급되고
- **When** 잘못된 서명 또는 다른 소유자(비 SUPER_ADMIN) 계정으로 다운로드를 시도하면
- **Then** HMAC 서명 불일치 시 400, 소유자 불일치 시 403 으로 다운로드가 거부된다.

### AC-021 SQL 인젝션 방지 (AdminKpiControllerIT)
- **Given** KPI 조회 필터에 DDL/DML 패턴(`DROP TABLE`, `; SELECT`, `UNION`) 이 포함되고
- **When** 해당 필터로 조회 요청을 보내면
- **Then** 정규식 검증으로 요청이 400 으로 거부되고 집계 쿼리가 실행되지 않는다.

### AC-022 PII 비노출 (AdminKpiControllerIT)
- **Given** kpi_value 집계 결과와 내보내기 파일이 생성되고
- **When** 응답 본문과 엑셀 파일 컬럼을 검사하면
- **Then** 원본 IP(`client_ip`), 사용자 ID(`user_id`) 등 개인식별정보가 포함되지 않고 집계값(`valueNumeric`, `valueText`)만 노출된다.

### AC-015 내보내기 감사 (AdminKpiControllerIT)
- **Given** ADMIN 사용자가 KPI 엑셀 내보내기를 수행하고
- **When** 내보내기가 완료되면
- **Then** audit_log 에 `action='EXPORT'`, actor, 적용 조건, 행 수가 기록된다.

## REQ-KPI-006 UI 대시보드 위젯

### AC-016 필터 패널 갱신 (프런트엔드 컴포넌트 테스트)
- **Given** KpiDashboardView 에 요약/트렌드/전환율 위젯이 렌더링되어 있고
- **When** KpiFilterPanel 에서 기간/기능/업종/지역을 변경하면
- **Then** kpiStore 가 새 조건으로 조회 API 를 재호출하고 모든 위젯이 갱신된다.

## Definition of Done

- [ ] AC-001 ~ AC-022 전 항목 통과 (IT 출력 증빙)
- [ ] 코드 커버리지 85%+ (TRUST 5 Tested)
- [ ] V45 마이그레이션 적용 후 MV/인덱스/시드 검증
- [ ] SXSSFWorkbook stub 제거 및 실구현 검증
- [ ] 전환율 KPI PREPARING graceful 처리 검증
- [ ] 모든 KPI 엔드포인트 ADMIN 권한 검증
- [ ] LSP zero error / zero lint (run 단계 게이트)
- [ ] SPEC-CMS-008 컬럼/API 무변경 확인 (additive only)

## 엣지 케이스 요약

| 케이스 | 기대 동작 | AC |
|---|---|---|
| 0 데이터 (집계 대상 로그 없음) | 빈 KPI, 오류 없음 | AC-005 |
| policy_matching 테이블 부재 | dataState=PREPARING | AC-006 |
| 대용량 내보내기 (>1,048,576행) | 다중 시트 / 상한 거부 | AC-009, AC-010 |
| 동시 조회 (캐시 경합) | 캐시 히트, 재집계 회피 | AC-013 |
| 비ADMIN 접근 | 403 | AC-014 |
| 집계 일부 실패 | 나머지 KPI 정상 | AC-003 |
| HMAC 서명 불일치 / 소유자 불일치 | 400 / 403 | AC-020 |
| DDL/DML 패턴 필터 주입 | 400 거부 | AC-021 |
| 응답에 PII 포함 시도 | 집계값만 노출 | AC-022 |
| 조회 결과 1,000행 초과 | LIMIT 캡 + hasMore=true | AC-019 |
