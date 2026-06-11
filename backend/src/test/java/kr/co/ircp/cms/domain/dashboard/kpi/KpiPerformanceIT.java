package kr.co.ircp.cms.domain.dashboard.kpi;

import kr.co.ircp.cms.domain.dashboard.kpi.service.KpiAggregationService;
import kr.co.ircp.cms.integration.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SPEC-CMS-KPI-001 Phase 5: 성능 검증 통합 테스트.
 *
 * <p>실제 PostgreSQL 16 + Flyway(V14 access_log 파티션 + V45 6/7월 파티션·MV) 환경에서
 * 파티션 프루닝과 MV CONCURRENT REFRESH 무중단성을 검증한다.
 *
 * <p>실제 스키마 기준 설계:
 * <ul>
 *   <li>access_log 는 created_at 기준 월별 RANGE 파티션. 6월 파티션명은 {@code access_log_y2026m06} (V45 생성).</li>
 *   <li>access_log 컬럼: id, site_id, user_id, session_id, ip_hash(CHAR 64), user_agent, referrer,
 *       page_url, status_code, response_time_ms, created_at — 프롬프트 가정과 달리 user_id/menu_id/ip_address 없음.</li>
 *   <li>kpi_aggregation_mv 는 uk_kpi_aggregation_mv(kpi_id, dimension) UNIQUE 인덱스 보유 → CONCURRENTLY REFRESH 가능.</li>
 * </ul>
 *
 * <p>AC 매핑:
 * <ul>
 *   <li>AC-011 파티션 프루닝: 6월 범위 조회 시 6월 파티션만 스캔, 타 파티션(4/5/7월) 미스캔</li>
 *   <li>AC-012 MV CONCURRENT REFRESH 무중단: REFRESH 중 동시 SELECT 가 락 대기 없이 성공</li>
 * </ul>
 */
// @MX:NOTE: [AUTO] KpiPerformanceIT — KPI 성능 검증 통합 테스트 (AC-011 파티션 프루닝 / AC-012 MV 무중단 REFRESH)
// @MX:SPEC: SPEC-CMS-KPI-001 Phase 5
@DisplayName("SPEC-CMS-KPI-001 KPI 성능 검증 IT")
class KpiPerformanceIT extends AbstractIntegrationTest {

    @Autowired
    private KpiAggregationService kpiAggregationService;

    @Autowired
    private JdbcTemplate jdbc;

    /** CONCURRENTLY REFRESH 는 트랜잭션 밖에서만 실행 가능하므로 독립 커넥션 확보용. */
    @Autowired
    private DataSource dataSource;

    /** V45 가 생성한 6월 파티션(access_log_y2026m06) 내 임의 일자. */
    private static final LocalDate JUNE = LocalDate.of(2026, 6, 15);

    @BeforeEach
    void cleanState() {
        // access_log 는 APPEND-ONLY 트리거가 DELETE 를 차단하므로 격리 위해 일시 비활성화 후 정리한다.
        jdbc.execute("ALTER TABLE access_log DISABLE TRIGGER USER");
        try {
            jdbc.update("DELETE FROM access_log WHERE created_at >= '2026-06-01' AND created_at < '2026-07-01'");
        } finally {
            jdbc.execute("ALTER TABLE access_log ENABLE TRIGGER USER");
        }
        // MV 데이터 확보용 집계 산출물 초기화 (정의/시드는 유지)
        jdbc.update("DELETE FROM kpi_value_history");
        jdbc.update("DELETE FROM kpi_value");
        jdbc.update("DELETE FROM batch_execution_log WHERE job_name = 'KPI_AGGREGATION'");
    }

    private void insertAccessLog(LocalDate date) {
        OffsetDateTime ts = date.atTime(10, 0).atOffset(ZoneOffset.UTC);
        jdbc.update(
                "INSERT INTO access_log (site_id, ip_hash, page_url, status_code, response_time_ms, created_at) "
                        + "VALUES (1, ?, ?, 200, 10, ?)",
                "a".repeat(64), "/features/search", ts);
    }

    @Test
    @DisplayName("AC-011: created_at 6월 범위 조회 시 6월 파티션만 스캔되고 타 파티션은 프루닝된다")
    void testPartitionPruning() {
        // Arrange: 6월 파티션에 1건 적재
        insertAccessLog(JUNE);

        // Act: 6월 한 달 범위로 EXPLAIN ANALYZE.
        // FORMAT TEXT 는 계획 라인마다 1행을 반환하므로 queryForList 로 받아 한 문자열로 합친다.
        String explain = String.join(
                "\n",
                jdbc.queryForList(
                        "EXPLAIN (ANALYZE, FORMAT TEXT) "
                                + "SELECT * FROM access_log "
                                + "WHERE created_at >= '2026-06-01' AND created_at < '2026-07-01'",
                        String.class));

        assertThat(explain).isNotBlank();

        // Assert 1: 6월 파티션이 계획에 등장 (Seq Scan / Bitmap Heap Scan on access_log_y2026m06)
        assertThat(explain)
                .as("6월 파티션 access_log_y2026m06 이 실행 계획에 포함되어야 함")
                .containsIgnoringCase("access_log_y2026m06");

        // Assert 2: 타 파티션(4/5/7월)은 프루닝되어 계획에 등장하지 않음
        assertThat(explain)
                .as("타 월 파티션은 프루닝되어 스캔되지 않아야 함 (파티션 프루닝 활성)")
                .doesNotContain("access_log_y2026m04")
                .doesNotContain("access_log_y2026m05")
                .doesNotContain("access_log_y2026m07");
    }

    @Test
    @DisplayName("AC-012: REFRESH MATERIALIZED VIEW CONCURRENTLY 중 동시 SELECT 가 락 대기 없이 성공한다")
    void testMvConcurrentRefreshNonBlocking() throws Exception {
        // Arrange: MV 에 데이터가 존재하도록 집계 수행 후 초기 REFRESH
        insertAccessLog(JUNE);
        kpiAggregationService.aggregateAll(JUNE);
        refreshMvConcurrently();

        Integer mvRows = jdbc.queryForObject("SELECT COUNT(*) FROM kpi_aggregation_mv", Integer.class);
        assertThat(mvRows).as("AC-012 전제: MV 에 집계 데이터가 적어도 1건 존재").isGreaterThanOrEqualTo(1);

        // Act: 백그라운드에서 CONCURRENTLY REFRESH 를 반복 수행하는 동안
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CompletableFuture<Void> refreshTask = CompletableFuture.runAsync(() -> {
            try {
                // 동시 SELECT 와 시간이 겹치도록 여러 번 REFRESH
                for (int i = 0; i < 3; i++) {
                    refreshMvConcurrently();
                }
            } catch (Exception e) {
                throw new RuntimeException("CONCURRENTLY REFRESH 실패", e);
            }
        }, executor);

        // 메인 스레드: REFRESH 진행 중 5회 SELECT 가 모두 데이터를 반환해야 한다 (락 대기·예외 없음)
        try {
            for (int i = 0; i < 5; i++) {
                Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM kpi_aggregation_mv", Integer.class);
                assertThat(count)
                        .as("REFRESH 중 동시 SELECT #%d 는 null 아닌 결과를 반환해야 함 (무중단)", i + 1)
                        .isNotNull()
                        .isGreaterThanOrEqualTo(1);
            }

            // Assert: 백그라운드 REFRESH 가 예외 없이 완료
            refreshTask.get(30, TimeUnit.SECONDS);
        } finally {
            // 백그라운드 작업은 위에서 이미 완료를 보장했으므로 graceful shutdown 으로
            // 커넥션 획득 중 InterruptedException 노이즈를 피한다.
            executor.shutdown();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }

        // 최종 무결성 확인: REFRESH 후에도 MV 조회 정상
        Integer finalRows = jdbc.queryForObject("SELECT COUNT(*) FROM kpi_aggregation_mv", Integer.class);
        assertThat(finalRows).isGreaterThanOrEqualTo(1);
    }

    /**
     * CONCURRENTLY REFRESH 는 트랜잭션 블록 안에서 실행할 수 없다.
     * 독립 커넥션을 autocommit 으로 열어 단일 문(statement) 으로 수행한다.
     */
    private void refreshMvConcurrently() throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(true);
            try (Statement st = conn.createStatement()) {
                st.execute("REFRESH MATERIALIZED VIEW CONCURRENTLY kpi_aggregation_mv");
            }
        }
    }
}
