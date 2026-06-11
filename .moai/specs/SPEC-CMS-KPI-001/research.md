# Research: SPEC-CMS-KPI-001 플랫폼 KPI 통합 관리

## 1. Existing Dashboard Architecture

### 핵심 컴포넌트

**DashboardWidget** (`backend/src/main/java/kr/co/ircp/cms/domain/dashboard/entity/DashboardWidget.java:19-42`):
- widgetType enum: METRIC_CARD, LINE_CHART, BAR_CHART, PIE_CHART, RADAR_CHART, MATRIX_HEATMAP, TABLE, PROGRESS_BAR, MAP_KOREA
- dataSource enum: KPI_VALUE, CUSTOM_QUERY, EXTERNAL
- `required_role_codes` 배열로 롤별 접근 제어

**DashboardWidgetServiceImpl** (`backend/src/main/java/kr/co/ircp/cms/domain/dashboard/service/DashboardWidgetServiceImpl.java`):
- REQ-VIZ-001, REQ-VIZ-005 구현
- Cache TTL: 5분 (line 47)
- DDL/DML regex 검증으로 SQL injection 방지 (lines 49-51)
- `fetchAndTransform()` via KPI_VALUE dimension 추출 (lines 256-268)
- `enforceRole()` 롤 검증 (lines 228-236)
- Cache key: dimension hash + role 조합 (lines 243-250)
- KPI_VALUE dimension 지원: period, feature, industry, region, role

**Dashboard Schema** (`backend/src/main/resources/db/migration/V17__dashboard_schema.sql`):
- `dashboard_widget`: JSONB config, 위젯 정의
- `dashboard_layout`: 12-컬럼 그리드, 사용자별 레이아웃
- `saved_view`: JSONB filter_state, 저장된 필터 뷰 (line 109)
- `chart_dataset_cache`: 5분 TTL (line 128)
- `export_history`: 24시간 TTL (line 152), 상태: PROCESSING/COMPLETED/FAILED/EXPIRED
- **kpi_definition**: code, name, calculation_query, refresh_interval_min, status (lines 8-20)
- **kpi_value**: kpi_id, JSONB dimension(period/feature/industry/region/role), value_numeric, value_text (lines 22-35)
- **kpi_value_history**: 타임시리즈 아카이브, calculated_at 추적 (lines 37-43)
- Index: `idx_kpi_value_calc` (kpi_id, calculated_at DESC), `idx_kpi_value_dim_gin` (GIN on JSONB dimension)

### 결론
- KPI 데이터 모델(kpi_definition, kpi_value, kpi_value_history)이 **이미 존재**함 (V17)
- 새 DB 스키마 없이 기존 kpi_value 테이블 활용 가능
- KpiValueMapper.xml 존재하며 JSONB containment 쿼리 패턴 구현됨

## 2. Data Sources for KPI (Log/Transaction Tables)

### Audit Log (`backend/src/main/resources/db/migration/V3__audit_log.sql`)
- 필드: id, event_time, actor_id, actor_role, action(CREATE/READ/UPDATE/DELETE/LOGIN/PERMISSION_CHANGE/EXPORT/BATCH), entity_type, entity_id, before_value(JSONB), after_value(JSONB), severity(INFO/WARN/CRITICAL), result(SUCCESS/FAILURE), duration_ms
- Index: idx_audit_log_event_time, idx_audit_log_actor, idx_audit_log_critical, idx_audit_log_action_time
- **APPEND-ONLY**: DB 트리거로 UPDATE/DELETE 차단 (lines 38-50)

### Access Log (`backend/src/main/resources/db/migration/V14__system_schema.sql`)
- 필드: id, site_id, user_id, session_id, ip_hash(SHA-256), user_agent, referrer, page_url, status_code, response_time_ms, created_at
- **월별 RANGE 파티션**: access_log_y2026m04, access_log_y2026m05 등
- Index: idx_access_log_site_created(site_id, created_at DESC), idx_access_log_page_url, idx_access_log_status
- 고빈도: 모든 HTTP 요청 기록

### Search Log (`backend/src/main/java/kr/co/ircp/cms/domain/search/entity/SearchLog.java`)
- 필드: userId, sessionId, query, normalizedQuery, resultCount, responseMs, clickedDocType, clickedDocId, clickedAt, clickedRank, locale, domainFilter, ipHash, createdAt
- `SearchLogAsyncService`: INSERT-ONLY 패턴 (lines 1-74)
- 보존 정책: 6개월 (V18 governance schema)

### Governance Stats Tables (`backend/src/main/resources/db/migration/V18__governance_schema.sql`)
- `board_stats_daily/monthly`: total_views, unique_visitors, post_count, avg_response_ms
- `content_view_stats_daily/monthly`: view_count, unique_viewers, avg_dwell_sec
- **`policy_match_stats_monthly`**: match_count, apply_count, **apply_conversion_rate**, success_count (lines 154-163)
- `safety_stats_monthly`: incident_count, casualty_count, severity_avg

### Policy Conversion Rate
- `PolicyMatchStatsMonthly` 엔티티 존재 (`backend/src/main/java/kr/co/ircp/cms/domain/governance/entity/PolicyMatchStatsMonthly.java`)
- `ConversionStats` DTO (`backend/src/main/java/kr/co/ircp/cms/domain/policy/tracking/dto/ConversionStats.java`)
  - 계산: `(clicks / views)`, 분모 보호 처리 (line 15-16)
  - 필드: policyId, viewCount, clickCount, redirectCount, savedCount, conversionRate
- `PolicyMatchStatsJob`: 월별 스케줄 (`0 30 2 1 * *`, 매월 1일 02:30 KST)
  - **주의**: SPEC-CMS-007 미구현 시 0값 반환 (stub 상태, line 41-42)

## 3. Excel Export Patterns

### ExportServiceImpl (`backend/src/main/java/kr/co/ircp/cms/domain/dashboard/service/ExportServiceImpl.java`)
- **비동기 임계값**: 10,000행 (line 44)
- **다운로드 TTL**: 24시간 (line 47)
- **서명 방식**: HMAC-SHA256 서명 URL (lines 150-160)
- **저장소**: `iroum.export.storage-dir` 설정 (line 56)
- **스트리밍**: `HttpServletResponse.getOutputStream()` 청크 응답
- **진행률 추적**: export_history.progress_pct (0-100)
- **상태**: PROCESSING → COMPLETED/FAILED/EXPIRED
- v0.4 주석: SXSSFWorkbook 패턴 예정, 현재 **stub 상태** (line 99)
  - → **SXSSFWorkbook 구현 필요**

### AuditLog Export 패턴
- `AuditLogController`: StreamingResponseBody 청크 export
- 필터: action, severity, actor_id, date range

## 4. Policy/Conversion Rate Data

- `policy_match_stats_monthly.apply_conversion_rate = apply_count / match_count`
- `ConversionStats.conversionRate = clicks / views` (0 나누기 보호)
- PolicyMatchStatsJob 스케줄 배치 존재
- SPEC-CMS-007 완료 전까지 stub (0값) — KPI 표시 시 "데이터 준비 중" 상태 처리 필요

## 5. Frontend Chart Patterns

**차트 라이브러리**: vue-echarts + echarts/core (모듈형)

**등록된 모듈** (`frontend/admin/src/components/system/DashboardTrendChart.vue:36-37`):
- LineChart, GridComponent, TooltipComponent, LegendComponent, CanvasRenderer

**ECharts 옵션 구조**:
```typescript
{
  tooltip: { trigger: 'axis' },
  legend: { data: [...] },
  grid: { left, right, bottom, containLabel: true },
  xAxis: { type: 'category', boundaryGap: false, data: [...dates] },
  yAxis: { type: 'value' },
  series: [{ name, type: 'line', smooth: true, data: [...values] }]
}
```

**Dimension 레이블 추출** (DashboardWidgetServiceImpl line 292):
- 순서: feature > industry > region > role > period
- v0.4+에서 Jackson 파싱으로 개선 예정 (현재 indexOf 문자열 파싱)

**레이아웃**: grid-layout-plus 드래그&드롭 (`frontend/admin/src/views/dashboard/DashboardGridLayout.vue`)
- 12컬럼 반응형, 768px 모바일 브레이크포인트

## 6. Complex Query Patterns (MyBatis XML)

### GovernanceStatsMapper.xml 집계 패턴
```sql
-- 일별 집계 (lines 77-104)
INSERT INTO board_stats_daily
SELECT stat_date, board_id, COUNT(*), COUNT(DISTINCT ip_hash), ...
FROM access_log
WHERE created_at >= #{targetDate}::date AND created_at < #{targetDate}::date + INTERVAL '1 day'
  AND page_url ~ '^/board/'
GROUP BY board_id
ON CONFLICT (stat_date, board_id) DO UPDATE SET ...
```

**핵심 기법**:
- RANGE 파티션 제거 on `created_at`
- `regexp_match()` URL 패턴 추출 (lines 84, 134)
- `COUNT(DISTINCT ip_hash)` 유니크 방문자
- `COALESCE(AVG(...))` null-safe 집계
- `ON CONFLICT DO UPDATE` 멱등성 UPSERT

**월별 롤업** (lines 106-126):
```sql
SELECT TO_CHAR(stat_date,'YYYY-MM'), board_id, SUM(total_views),...
FROM board_stats_daily
WHERE TO_CHAR(stat_date,'YYYY-MM') = #{targetMonth}
GROUP BY board_id
```

**페이지네이션**: LIMIT 1000 safety cap, `<if test="...">` 동적 WHERE

## 7. Performance Optimization Opportunities

### 기존 최적화
1. **시계열 파티셔닝**: access_log 월별 파티션 (V14)
2. **사전 집계 테이블**: board_stats_daily/monthly, content_view_stats_daily/monthly
3. **JSONB 인덱싱**: GIN index on kpi_value.dimension
4. **선택적 인덱스**: idx_audit_log_critical (WHERE severity='CRITICAL')
5. **Cache**: 5분 TTL chart_dataset_cache
6. **비동기 로깅**: SearchLogAsyncService, AuditLogServiceImpl @Async

### KPI 대시보드 추천 최적화

1. **Materialized View**: `kpi_aggregation_mv` 사전 계산
   - REFRESH MATERIALIZED VIEW CONCURRENTLY (배치 완료 후)
   - 멀티필터 조회 속도 향상

2. **인덱스 전략**:
   - 복합 인덱스: (kpi_id, dimension, calculated_at DESC)
   - 부분 인덱스: audit_log WHERE action='EXPORT' (EXPORT KPI 추적용)

3. **쿼리 최적화**:
   - CTE로 멀티조건 필터 사전 계산
   - 윈도우 함수: `ROW_NUMBER() OVER (PARTITION BY dimension ORDER BY calculated_at DESC)`
   - PL/pgSQL 저장 프로시저로 배치 KPI 계산

4. **캐싱 전략**:
   - 기존 chart_dataset_cache 재활용 (5분 TTL)
   - 자주 조회되는 KPI 조합은 1시간 TTL 고려

## 8. Security Patterns

- **Dashboard Endpoints**: VIEWER 이상 역할 필요
- **Stats/KPI Endpoints**: ADMIN 이상 (GovernanceStatsController, DataQualityController 패턴)
- **Export Endpoints**: 역할 검증 + SUPER_ADMIN override (ExportServiceImpl.verifyDownload() line 128)
- **Widget Access Control**: required_role_codes 배열 검증 (lines 85-117)
- **PII 보호**: ip_hash = SHA-256(IP + salt), audit_log APPEND-ONLY
- **Export URL 보안**: HMAC-SHA256 서명, 24시간 만료, 소유자/SUPER_ADMIN만 다운로드
- **@AuditLog 어노테이션**: 공개 엔드포인트 자동 감사 추적

## 9. Implementation Recommendations

### Phase 1: KPI 집계 서비스

**KpiAggregationService**:
- 집계 차원: period(week/month/quarter/year), feature(type), industry(type)
- 지표: usage_rate(feature_views/total_views), download_count, policy_conversion_rate
- 배치: KpiAggregationJob 일별 @04:00 KST
- XML mapper: KpiAggregationMapper GROUP BY dimension 쿼리
- 기존 kpi_definition/kpi_value 테이블 활용 (V17에 이미 존재)

**멀티조건 필터링**:
- 기존 KpiValueMapper JSONB containment(`@>`) 패턴 확장
- 동적 WHERE 절: period + feature + industry 복합 조건

**Excel Export 완성**:
- ExportServiceImpl SXSSFWorkbook stub → 실제 구현 완성
- KpiExportService: 50k행 이상 다중 시트 청크
- 컬럼: kpi_code, period, feature, industry, value_numeric, aggregated_at

**Dashboard Widgets**:
- KPI 요약 카드 (기존 METRIC_CARD 타입 활용)
- KPI 트렌드 차트 (기존 LINE_CHART 타입 활용)
- KPI 전환율 퍼널 (BAR_CHART 타입 활용)
- KPI 필터 패널 (Vue 컴포넌트)

### Phase 2: 고급 분석
- Materialized view 기반 실시간 조회
- 고급 export: PDF 차트 포함
- WebSocket 임계값 알림

## 10. Risk & Constraints

### 식별된 리스크

1. **PolicyMatchStatsJob Stub**: SPEC-CMS-007 미구현 시 전환율 KPI = 0
   - **완화**: "데이터 준비 중" 상태 UI 표시, SPEC-CMS-007 의존성 명시

2. **ExportServiceImpl SXSSFWorkbook Stub**: 현재 파일 실제 미작성
   - **완화**: 본 SPEC에서 SXSSFWorkbook 구현 완성 포함

3. **JSONB Parse Fragility**: indexOf 문자열 파싱 (DashboardWidgetServiceImpl line 290-303)
   - **완화**: Jackson ObjectMapper로 안전한 파싱으로 전환

4. **3개월 Access Log TTL**: V18 거버넌스 스키마에서 3개월 후 삭제
   - **완화**: 집계 배치 완료 후 kpi_value_history 아카이브 필수

5. **Export 크기 미제한**: StreamingResponseBody 상한 없음
   - **완화**: max_export_rows = 1,000,000 cap 추가

### 제약사항

1. **단방향 의존성**: KPI는 audit_log, access_log, search_log 소스에 의존 → 로그 스키마 변경 불가
2. **access_log 3개월 TTL**: 집계 전 아카이브 필수
3. **5분 Cache TTL**: 고빈도 KPI 갱신 시 캐시 스레싱 가능
4. **SPEC-CMS-007 의존성**: policy_matching 테이블 미존재 시 전환율 = 0 (stub)

## 핵심 파일 경로 요약

**Backend Core**:
- `backend/src/main/java/kr/co/ircp/cms/domain/dashboard/` (위젯, 레이아웃, 익스포트)
- `backend/src/main/java/kr/co/ircp/cms/domain/governance/` (통계 배치)
- `backend/src/main/java/kr/co/ircp/cms/domain/audit/` (감사 로그)
- `backend/src/main/java/kr/co/ircp/cms/domain/search/entity/SearchLog.java`

**DB Migrations**:
- `V3__audit_log.sql`, `V14__system_schema.sql`, `V17__dashboard_schema.sql`
- `V18__governance_schema.sql`, `V23__search_performance_indexes.sql`

**MyBatis Mappers**:
- `backend/src/main/resources/mapper/dashboard/KpiValueMapper.xml`
- `backend/src/main/resources/mybatis/mapper/governance/GovernanceStatsMapper.xml`

**Frontend**:
- `frontend/admin/src/views/dashboard/DashboardGridLayout.vue`
- `frontend/admin/src/components/system/DashboardTrendChart.vue`
- `frontend/admin/src/stores/dashboardStore.ts`
