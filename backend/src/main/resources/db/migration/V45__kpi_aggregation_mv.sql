-- SPEC-CMS-KPI-001 Phase 0: KPI 집계 Materialized View + 인덱스 + 시드
-- 의존: V17(kpi_definition/kpi_value/kpi_value_history), V14(access_log 파티션), V3(audit_log)
--
-- 설계 메모(실제 스키마 기준):
--   kpi_value 에는 period 컬럼이 없으므로 일/월 구분은 dimension JSONB(예: {"date":"YYYY-MM-DD"})에 인코딩.
--   UNIQUE(kpi_id, dimension) 가 일자별 1행을 보장한다.
--   audit_log 의 시간 컬럼은 event_time(created_at 아님)이며 action CHECK 에 'EXPORT' 가 포함됨.

-- ─── 1. access_log 파티션 보강 (2026-06 ~ 2026-07) ──────────────────────────────
-- V14 는 2026-04/2026-05 파티션만 생성했다. 당월(2026-06) 이후 일자 집계를 위해 선행 파티션 추가.
CREATE TABLE IF NOT EXISTS access_log_y2026m06 PARTITION OF access_log
    FOR VALUES FROM ('2026-06-01') TO ('2026-07-01');

CREATE TABLE IF NOT EXISTS access_log_y2026m07 PARTITION OF access_log
    FOR VALUES FROM ('2026-07-01') TO ('2026-08-01');

-- ─── 2. audit_log EXPORT 부분 인덱스 (file_download_count 집계 가속) ─────────────
-- action='EXPORT' 행만 색인하여 일자 범위 스캔 시 인덱스 프루닝 효과.
CREATE INDEX IF NOT EXISTS idx_audit_log_export_time
    ON audit_log (event_time)
    WHERE action = 'EXPORT';

-- ─── 3. KPI 집계 Materialized View ──────────────────────────────────────────────
-- 최신 kpi_value 스냅샷을 kpi_definition 메타와 조인하여 빠른 조회 제공.
-- B2 결정: 내부 컬럼 calculated_at 을 aggregated_at 별칭으로 노출.
CREATE MATERIALIZED VIEW IF NOT EXISTS kpi_aggregation_mv AS
SELECT
    v.kpi_id                       AS kpi_id,
    d.code                         AS kpi_code,
    d.name                         AS kpi_name,
    v.dimension                    AS dimension,
    v.value_numeric                AS value_numeric,
    v.value_text                   AS value_text,
    v.calculated_at                AS aggregated_at
FROM kpi_value v
JOIN kpi_definition d ON d.id = v.kpi_id
WITH DATA;

-- ─── 4. CONCURRENT REFRESH 전제 UNIQUE 인덱스 (반드시 MV 생성 직후) ────────────────
-- REFRESH MATERIALIZED VIEW CONCURRENTLY 는 대상 MV 에 UNIQUE 인덱스가 1개 이상 필요.
CREATE UNIQUE INDEX IF NOT EXISTS uk_kpi_aggregation_mv
    ON kpi_aggregation_mv (kpi_id, dimension);

-- 조회 보조 인덱스
CREATE INDEX IF NOT EXISTS idx_kpi_aggregation_mv_code
    ON kpi_aggregation_mv (kpi_code);

COMMENT ON MATERIALIZED VIEW kpi_aggregation_mv IS
    'SPEC-CMS-KPI-001 KPI 집계 조회용 MV. CONCURRENTLY REFRESH 지원(uk_kpi_aggregation_mv).';

-- ─── 5. KPI 정의 시드 (3종) ──────────────────────────────────────────────────────
-- calculation_query 는 NOT NULL 이므로 산식 설명을 함께 적재. ON CONFLICT(code) DO NOTHING.
INSERT INTO kpi_definition (code, name, description, calculation_query, refresh_interval_min, status)
VALUES
    ('FEATURE_USAGE_RATE',
     '기능 사용률',
     '일자별 기능 페이지 조회수 / 전체 조회수 비율',
     'feature_views / NULLIF(total_views, 0) FROM access_log (daily)',
     1440, 'ACTIVE'),
    ('FILE_DOWNLOAD_COUNT',
     '파일 다운로드 수',
     '일자별 audit_log action=EXPORT 건수',
     'COUNT(audit_log WHERE action=''EXPORT'') (daily)',
     1440, 'ACTIVE'),
    ('POLICY_APPLY_CONVERSION_RATE',
     '정책 신청 전환율',
     '월별 신청수 / 매칭수 (SPEC-CMS-007 의존, 준비중)',
     'apply_count / NULLIF(match_count, 0) (monthly, PREPARING)',
     43200, 'ACTIVE')
ON CONFLICT (code) DO NOTHING;
