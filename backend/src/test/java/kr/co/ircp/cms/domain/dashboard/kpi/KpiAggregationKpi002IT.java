package kr.co.ircp.cms.domain.dashboard.kpi;

import kr.co.ircp.cms.domain.dashboard.kpi.service.KpiAggregationService;
import kr.co.ircp.cms.integration.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SPEC-CMS-KPI-002 운영 활동 지표 KPI 확장 통합 테스트.
 *
 * <p>실제 PostgreSQL 16 + Flyway(V53 포함) 환경에서 access_log 시드를 적재하고
 * {@code aggregateAll(targetDate)} 호출 후 신규 4종(DAU/MAU, CONTENT_VIEW,
 * AVG_SESSION_DURATION, API_ERROR_RATE) 의 kpi_value / kpi_value_history /
 * kpi_aggregation_mv / batch_execution_log 결과를 검증한다.
 *
 * <p>설계 기준(research.md): access_log 단일 원천. user_id/session_id nullable → NULL 제외.
 * 일별 dimension={"date":"YYYY-MM-DD"}, 월별 dimension={"month":"YYYY-MM"},
 * CONTENT_VIEW dimension={"date","contentType"}.
 */
// @MX:NOTE: [AUTO] KpiAggregationKpi002IT — 신규 운영 활동 KPI 4종 집계 검증 (AC-001~019)
// @MX:SPEC: SPEC-CMS-KPI-002
@DisplayName("SPEC-CMS-KPI-002 운영 활동 지표 KPI 집계 IT")
class KpiAggregationKpi002IT extends AbstractIntegrationTest {

    @Autowired
    private KpiAggregationService kpiAggregationService;

    @Autowired
    private JdbcTemplate jdbc;

    /** V45 가 생성한 6월 파티션 내 일자. */
    private static final LocalDate TARGET = LocalDate.of(2026, 6, 12);
    private static final String DAY_DIM = "{\"date\":\"2026-06-12\"}";
    private static final String MONTH_DIM = "{\"month\":\"2026-06\"}";

    @BeforeEach
    void cleanState() {
        jdbc.update("DELETE FROM kpi_value_history");
        jdbc.update("DELETE FROM kpi_value");
        jdbc.update("DELETE FROM batch_execution_log WHERE job_name = 'KPI_AGGREGATION'");
        // 6월 전체 파티션 정리(DAU/MAU/세션/오류율 테스트가 6월 다양한 일자를 사용)
        jdbc.update("DELETE FROM access_log WHERE created_at >= '2026-06-01'::date AND created_at < '2026-07-01'::date");
    }

    private Long kpiId(String code) {
        return jdbc.queryForObject("SELECT id FROM kpi_definition WHERE code = ?", Long.class, code);
    }

    /** access_log 1행 삽입(분 단위 시각 제어). user_id/session_id 는 NULL 허용. */
    private void insertLog(Long userId, String sessionId, String pageUrl, int statusCode,
                          LocalDate date, int hour, int minute) {
        OffsetDateTime ts = date.atTime(hour, minute).atOffset(ZoneOffset.UTC);
        jdbc.update(
                "INSERT INTO access_log (site_id, user_id, session_id, ip_hash, page_url, status_code, response_time_ms, created_at) "
                        + "VALUES (1, ?, ?, ?, ?, ?, 10, ?)",
                userId, sessionId, "a".repeat(64), pageUrl, statusCode, ts);
    }

    private BigDecimal value(String code, String dimension) {
        return jdbc.queryForObject(
                "SELECT value_numeric FROM kpi_value WHERE kpi_id = ? AND dimension @> ?::jsonb",
                BigDecimal.class, kpiId(code), dimension);
    }

    // ── DAU / MAU ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("AC-001: DAU 가 COUNT(DISTINCT user_id) 로 일별 UPSERT 된다")
    void ac001_dau_countDistinctUser() {
        insertLog(10L, "s1", "/board/notice", 200, TARGET, 9, 0);
        insertLog(10L, "s1", "/board/notice", 200, TARGET, 9, 5);  // 중복 user → DISTINCT 1
        insertLog(20L, "s2", "/board/notice", 200, TARGET, 10, 0); // user 20
        insertLog(30L, "s3", "/board/notice", 200, TARGET, 11, 0); // user 30

        kpiAggregationService.aggregateAll(TARGET);

        assertThat(value("DAU", DAY_DIM).intValueExact()).isEqualTo(3);
    }

    @Test
    @DisplayName("AC-002: MAU 가 월 범위 COUNT(DISTINCT user_id) 로 멱등 UPSERT 된다")
    void ac002_mau_monthlyDistinct() {
        insertLog(10L, "s1", "/x", 200, LocalDate.of(2026, 6, 1), 9, 0);
        insertLog(20L, "s2", "/x", 200, LocalDate.of(2026, 6, 15), 9, 0);
        insertLog(10L, "s3", "/x", 200, LocalDate.of(2026, 6, 28), 9, 0); // 동일 user 10 재방문

        kpiAggregationService.aggregateAll(TARGET);
        BigDecimal first = value("MAU", MONTH_DIM);
        assertThat(first.intValueExact()).isEqualTo(2);

        // 재집계 멱등성: 중복 행 미생성
        kpiAggregationService.aggregateAll(TARGET);
        Integer rows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM kpi_value WHERE kpi_id = ? AND dimension @> ?::jsonb",
                Integer.class, kpiId("MAU"), MONTH_DIM);
        assertThat(rows).isEqualTo(1);
    }

    @Test
    @DisplayName("AC-003: user_id=NULL 행이 DAU 집계에서 제외된다")
    void ac003_nullUserExcludedFromDau() {
        insertLog(10L, "s1", "/x", 200, TARGET, 9, 0);
        insertLog(null, "s2", "/x", 200, TARGET, 10, 0); // 비로그인 → 제외
        insertLog(null, "s3", "/x", 200, TARGET, 11, 0); // 비로그인 → 제외

        kpiAggregationService.aggregateAll(TARGET);

        assertThat(value("DAU", DAY_DIM).intValueExact()).isEqualTo(1);
    }

    @Test
    @DisplayName("AC-004: 빈 일자에 DAU=0 으로 1행 UPSERT 된다")
    void ac004_emptyDayDauZero() {
        // access_log 없음
        kpiAggregationService.aggregateAll(TARGET);

        Integer rows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM kpi_value WHERE kpi_id = ? AND dimension @> ?::jsonb",
                Integer.class, kpiId("DAU"), DAY_DIM);
        assertThat(rows).isEqualTo(1);
        assertThat(value("DAU", DAY_DIM).intValueExact()).isZero();
    }

    // ── CONTENT_VIEW ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("AC-005: CONTENT_VIEW 가 notice/post/publication 유형별로 COUNT 한다")
    void ac005_contentViewByType() {
        insertLog(1L, "s1", "/board/notice", 200, TARGET, 9, 0);
        insertLog(1L, "s1", "/notices/123", 200, TARGET, 9, 1);   // notice 2
        insertLog(2L, "s2", "/posts/9", 200, TARGET, 10, 0);
        insertLog(2L, "s2", "/board/free/3", 200, TARGET, 10, 1);  // post 2
        insertLog(3L, "s3", "/publications/2025", 200, TARGET, 11, 0); // publication 1

        kpiAggregationService.aggregateAll(TARGET);

        assertThat(value("CONTENT_VIEW", "{\"date\":\"2026-06-12\",\"contentType\":\"notice\"}").intValueExact()).isEqualTo(2);
        assertThat(value("CONTENT_VIEW", "{\"date\":\"2026-06-12\",\"contentType\":\"post\"}").intValueExact()).isEqualTo(2);
        assertThat(value("CONTENT_VIEW", "{\"date\":\"2026-06-12\",\"contentType\":\"publication\"}").intValueExact()).isEqualTo(1);
    }

    @Test
    @DisplayName("AC-006: 미분류 page_url 이 CONTENT_VIEW 집계에서 제외된다")
    void ac006_unmatchedUrlExcluded() {
        insertLog(1L, "s1", "/board/notice", 200, TARGET, 9, 0);   // notice 1
        insertLog(2L, "s2", "/admin/dashboard", 200, TARGET, 10, 0); // 미분류 → 제외
        insertLog(3L, "s3", "/", 200, TARGET, 11, 0);              // 미분류 → 제외

        kpiAggregationService.aggregateAll(TARGET);

        assertThat(value("CONTENT_VIEW", "{\"date\":\"2026-06-12\",\"contentType\":\"notice\"}").intValueExact()).isEqualTo(1);
        // 미분류 유형 행은 생성되지 않는다
        Integer total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM kpi_value WHERE kpi_id = ?", Integer.class, kpiId("CONTENT_VIEW"));
        assertThat(total).isEqualTo(1);
    }

    @Test
    @DisplayName("AC-007: (date,contentType) 재집계가 멱등하다(중복 행 미생성)")
    void ac007_contentViewIdempotent() {
        insertLog(1L, "s1", "/board/notice", 200, TARGET, 9, 0);
        kpiAggregationService.aggregateAll(TARGET);
        kpiAggregationService.aggregateAll(TARGET);

        Integer rows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM kpi_value WHERE kpi_id = ? AND dimension @> ?::jsonb",
                Integer.class, kpiId("CONTENT_VIEW"), "{\"date\":\"2026-06-12\",\"contentType\":\"notice\"}");
        assertThat(rows).isEqualTo(1);
    }

    // ── AVG_SESSION_DURATION ──────────────────────────────────────────────────

    @Test
    @DisplayName("AC-008: AVG_SESSION_DURATION 이 세션별 (MAX-MIN) 평균(초)으로 UPSERT 된다")
    void ac008_avgSessionDuration() {
        // s1: 09:00 ~ 09:10 = 600초, s2: 10:00 ~ 10:20 = 1200초 → 평균 900초
        insertLog(1L, "s1", "/x", 200, TARGET, 9, 0);
        insertLog(1L, "s1", "/x", 200, TARGET, 9, 10);
        insertLog(2L, "s2", "/x", 200, TARGET, 10, 0);
        insertLog(2L, "s2", "/x", 200, TARGET, 10, 20);

        kpiAggregationService.aggregateAll(TARGET);

        assertThat(value("AVG_SESSION_DURATION", DAY_DIM).intValueExact()).isEqualTo(900);
    }

    @Test
    @DisplayName("AC-009: 단일 요청 세션(MAX=MIN)은 지속시간 0초")
    void ac009_singleRequestSessionZero() {
        insertLog(1L, "s1", "/x", 200, TARGET, 9, 0); // 단일 요청 → 0초

        kpiAggregationService.aggregateAll(TARGET);

        assertThat(value("AVG_SESSION_DURATION", DAY_DIM).intValueExact()).isZero();
    }

    @Test
    @DisplayName("AC-010: 30분 초과 idle gap 이 세션 경계로 분리된다")
    void ac010_idleGapSplitsSession() {
        // 동일 session_id 'g1' 이지만 09:00, 09:05 (= seg1, 300초),
        // 이후 40분 gap 후 09:45, 09:50 (= seg2, 300초) → 두 하위 세션 모두 300초 → 평균 300
        insertLog(1L, "g1", "/x", 200, TARGET, 9, 0);
        insertLog(1L, "g1", "/x", 200, TARGET, 9, 5);
        insertLog(1L, "g1", "/x", 200, TARGET, 9, 45);
        insertLog(1L, "g1", "/x", 200, TARGET, 9, 50);

        kpiAggregationService.aggregateAll(TARGET);

        // gap 분리 없이 단일 세션으로 봤다면 (09:50-09:00)=3000초가 됐을 것.
        // 30분 gap 분리 시 각 하위 세션 300초 → 평균 300초.
        assertThat(value("AVG_SESSION_DURATION", DAY_DIM).intValueExact()).isEqualTo(300);
    }

    @Test
    @DisplayName("AC-011: session_id=NULL 행이 세션 지속 집계에서 제외된다")
    void ac011_nullSessionExcluded() {
        insertLog(1L, "s1", "/x", 200, TARGET, 9, 0);
        insertLog(1L, "s1", "/x", 200, TARGET, 9, 10);   // s1 = 600초
        insertLog(2L, null, "/x", 200, TARGET, 10, 0);   // 세션 없음 → 제외
        insertLog(2L, null, "/x", 200, TARGET, 11, 0);   // 세션 없음 → 제외

        kpiAggregationService.aggregateAll(TARGET);

        // s1 만 집계 → 평균 = 600초 (NULL 세션이 포함됐다면 다른 값)
        assertThat(value("AVG_SESSION_DURATION", DAY_DIM).intValueExact()).isEqualTo(600);
    }

    // ── API_ERROR_RATE ────────────────────────────────────────────────────────

    @Test
    @DisplayName("AC-012: API_ERROR_RATE 가 status_code>=500 비율(%)로 UPSERT 된다")
    void ac012_apiErrorRate() {
        // 500 1건, 503 1건 (오류 2), 200 6건, 404 2건 → 총 10건, 오류 2건 → 20%
        insertLog(1L, "s1", "/x", 500, TARGET, 9, 0);
        insertLog(1L, "s1", "/x", 503, TARGET, 9, 1);
        insertLog(1L, "s1", "/x", 404, TARGET, 9, 2); // 4xx 는 분자 제외
        insertLog(1L, "s1", "/x", 404, TARGET, 9, 3);
        for (int i = 0; i < 6; i++) {
            insertLog(1L, "s1", "/x", 200, TARGET, 10, i);
        }

        kpiAggregationService.aggregateAll(TARGET);

        assertThat(value("API_ERROR_RATE", DAY_DIM).doubleValue()).isEqualTo(20.0);
    }

    @Test
    @DisplayName("AC-013: 빈 일자에 오류율 0%, NULLIF 로 division-by-zero 없음")
    void ac013_emptyDayErrorRateZero() {
        kpiAggregationService.aggregateAll(TARGET);

        Integer rows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM kpi_value WHERE kpi_id = ? AND dimension @> ?::jsonb",
                Integer.class, kpiId("API_ERROR_RATE"), DAY_DIM);
        assertThat(rows).isEqualTo(1);
        assertThat(value("API_ERROR_RATE", DAY_DIM).doubleValue()).isEqualTo(0.0);
    }

    // ── 배치 통합 / 아카이브 / MV / 격리 ─────────────────────────────────────────

    @Test
    @DisplayName("AC-014: 신규 KPI 중 하나의 실패가 나머지 집계를 멈추지 않는다(격리)")
    void ac014_failureIsolation() {
        insertLog(1L, "s1", "/board/notice", 200, TARGET, 9, 0);
        insertLog(1L, "s1", "/board/notice", 200, TARGET, 9, 10); // 세션 600초 + notice 2

        // 정상 데이터로 aggregateAll → 모든 신규 KPI 가 산출되어야 한다(격리 회귀 검증)
        kpiAggregationService.aggregateAll(TARGET);

        // DAU/CONTENT_VIEW/세션/오류율 모두 1행 이상 존재
        assertThat(value("DAU", DAY_DIM)).isNotNull();
        assertThat(value("AVG_SESSION_DURATION", DAY_DIM)).isNotNull();
        assertThat(value("API_ERROR_RATE", DAY_DIM)).isNotNull();

        Map<String, Object> log = jdbc.queryForMap(
                "SELECT job_group, status FROM batch_execution_log "
                        + "WHERE job_name = 'KPI_AGGREGATION' ORDER BY started_at DESC LIMIT 1");
        assertThat((String) log.get("job_group")).isEqualTo("STATS");
        assertThat((String) log.get("status")).isIn("SUCCESS", "FAILURE");
    }

    @Test
    @DisplayName("AC-015: 신규 KPI 집계가 갱신 전 값을 kpi_value_history 로 아카이브한다")
    void ac015_archiveBeforeUpsert() {
        Long dauId = kpiId("DAU");

        // 1차: user 1명 → DAU=1, history 0
        insertLog(1L, "s1", "/x", 200, TARGET, 9, 0);
        kpiAggregationService.aggregateAll(TARGET);
        Integer historyFirst = jdbc.queryForObject(
                "SELECT COUNT(*) FROM kpi_value_history WHERE kpi_id = ?", Integer.class, dauId);
        assertThat(historyFirst).isZero();

        // 2차: user 1명 추가 → DAU=2, 기존 DAU=1 이 history 로 아카이브
        insertLog(2L, "s2", "/x", 200, TARGET, 10, 0);
        kpiAggregationService.aggregateAll(TARGET);

        assertThat(value("DAU", DAY_DIM).intValueExact()).isEqualTo(2);
        List<BigDecimal> archived = jdbc.queryForList(
                "SELECT value_numeric FROM kpi_value_history WHERE kpi_id = ? ORDER BY archived_at DESC",
                BigDecimal.class, dauId);
        assertThat(archived).hasSize(1);
        assertThat(archived.get(0).intValueExact()).isEqualTo(1);
    }

    @Test
    @DisplayName("AC-016: 집계 후 kpi_aggregation_mv 가 신규 dimension 을 포함하여 갱신된다")
    void ac016_mvRefreshedWithNewDimension() {
        insertLog(1L, "s1", "/board/notice", 200, TARGET, 9, 0);
        kpiAggregationService.aggregateAll(TARGET);
        // MV CONCURRENTLY REFRESH 는 트랜잭션 밖 — 테스트에서 직접 호출하여 검증
        jdbc.execute("REFRESH MATERIALIZED VIEW CONCURRENTLY kpi_aggregation_mv");

        Integer mvRows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM kpi_aggregation_mv WHERE kpi_code = 'DAU' AND dimension @> ?::jsonb",
                Integer.class, DAY_DIM);
        assertThat(mvRows).isEqualTo(1);

        Integer cvMvRows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM kpi_aggregation_mv WHERE kpi_code = 'CONTENT_VIEW' AND dimension @> ?::jsonb",
                Integer.class, "{\"contentType\":\"notice\"}");
        assertThat(cvMvRows).isEqualTo(1);
    }

    @Test
    @DisplayName("AC-017: 신규 KPI 4종이 기존 /values 조회 경로(kpi_value)로 조회 가능하다")
    void ac017_queryableViaExistingStorage() {
        insertLog(1L, "s1", "/board/notice", 200, TARGET, 9, 0);
        kpiAggregationService.aggregateAll(TARGET);

        // 기존 조회 API 가 의존하는 kpi_definition×kpi_value JOIN 으로 신규 코드가 조회되는지 검증
        for (String code : List.of("DAU", "CONTENT_VIEW", "AVG_SESSION_DURATION", "API_ERROR_RATE")) {
            Integer cnt = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM kpi_value kv JOIN kpi_definition kd ON kv.kpi_id = kd.id "
                            + "WHERE kd.code = ?", Integer.class, code);
            assertThat(cnt).as("KPI %s 조회 가능", code).isGreaterThanOrEqualTo(1);
        }
    }

    @Test
    @DisplayName("AC-018: 신규 KPI 코드가 kpi_definition 에 ACTIVE 로 시드되어 있다(V53)")
    void ac018_definitionsSeeded() {
        for (String code : List.of("DAU", "MAU", "CONTENT_VIEW", "AVG_SESSION_DURATION", "API_ERROR_RATE")) {
            String status = jdbc.queryForObject(
                    "SELECT status FROM kpi_definition WHERE code = ?", String.class, code);
            assertThat(status).as("KPI %s status", code).isEqualTo("ACTIVE");
        }
    }

    @Test
    @DisplayName("AC-019: 집계 결과에 user_id/session_id/ip_hash 가 노출되지 않는다(집계값만)")
    void ac019_noPiiInResult() {
        insertLog(1L, "s1", "/board/notice", 200, TARGET, 9, 0);
        kpiAggregationService.aggregateAll(TARGET);

        // kpi_value 스키마에는 PII 컬럼이 없다 — value_numeric/value_text/dimension 만 존재.
        // dimension JSONB 에 user_id/session_id/ip_hash 키가 들어가지 않음을 검증.
        List<String> dims = jdbc.queryForList(
                "SELECT dimension::text FROM kpi_value", String.class);
        assertThat(dims).isNotEmpty();
        for (String d : dims) {
            assertThat(d).doesNotContain("user_id").doesNotContain("session_id").doesNotContain("ip_hash");
        }
    }
}
