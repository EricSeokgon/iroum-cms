package kr.co.ircp.cms.domain.dashboard.kpi.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;

/**
 * SPEC-CMS-KPI-001 Phase 1: KPI 집계 MyBatis 매퍼.
 *
 * <p>모든 access_log / audit_log 조회는 일자 범위(created_at / event_time)를 포함하여
 * 파티션 프루닝 및 부분 인덱스를 활용한다.
 */
// @MX:ANCHOR: [AUTO] KpiAggregationMapper — KPI 집계 UPSERT/아카이브/REFRESH 계약
// @MX:REASON: KpiAggregationServiceImpl 의 모든 KPI 집계가 본 매퍼에 의존 (fan_in 집중 지점)
@Mapper
public interface KpiAggregationMapper {

    /** kpi_definition.code → id 조회. 미존재 시 null. */
    Long findKpiIdByCode(@Param("code") String code);

    /**
     * UPSERT 전 기존 kpi_value 행을 kpi_value_history 로 전체 스냅샷 아카이브.
     * 기존 행이 없으면 0건(아카이브 없음).
     *
     * @return 아카이브된 행 수
     */
    int archiveExisting(@Param("kpiId") Long kpiId, @Param("dimensionJson") String dimensionJson);

    /**
     * feature_usage_rate 일별 집계 후 kpi_value UPSERT.
     * value_numeric = feature_views / NULLIF(total_views, 0).
     *
     * @return 영향 행 수
     */
    int upsertFeatureUsageRate(@Param("kpiId") Long kpiId,
                               @Param("targetDate") LocalDate targetDate,
                               @Param("dimensionJson") String dimensionJson);

    /**
     * file_download_count 일별 집계 후 kpi_value UPSERT.
     * value_numeric = COUNT(audit_log WHERE action='EXPORT' AND event_time IN [day]).
     *
     * @return 영향 행 수
     */
    int upsertFileDownloadCount(@Param("kpiId") Long kpiId,
                                @Param("targetDate") LocalDate targetDate,
                                @Param("dimensionJson") String dimensionJson);

    /** policy_match_stats_monthly 등 의존 테이블 존재 여부 확인 (현재 스키마 기준 행 수). */
    int countTable(@Param("tableName") String tableName);

    /** kpi_aggregation_mv CONCURRENTLY 리프레시. UNIQUE 인덱스(uk_kpi_aggregation_mv) 전제. */
    void refreshAggregationMv();

    /** batch_execution_log 시작 행 삽입 후 생성된 id 반환. */
    Long insertBatchStart(@Param("startedAt") java.time.OffsetDateTime startedAt);

    /** batch_execution_log 종료 갱신 (status / 처리·실패 건수 / 종료시각 / 요약). */
    int updateBatchEnd(@Param("id") Long id,
                       @Param("status") String status,
                       @Param("recordsProcessed") int recordsProcessed,
                       @Param("recordsFailed") int recordsFailed,
                       @Param("errorSummary") String errorSummary);
}
