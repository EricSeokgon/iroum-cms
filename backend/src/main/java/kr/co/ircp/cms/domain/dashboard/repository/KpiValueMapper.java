package kr.co.ircp.cms.domain.dashboard.repository;

import kr.co.ircp.cms.domain.dashboard.entity.KpiValueRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * kpi_value 조회 매퍼 (SPEC-CMS-005 의존, 위젯 데이터 페치 전용).
 * REQ-VIZ-005-D-1
 */
@Mapper
public interface KpiValueMapper {

    /**
     * (kpi_id, dimension subset) 으로 kpi_value 조회.
     * dimensionFilter 는 단순 LIKE 매칭(JSON 텍스트). 정합성은 호출자에서 보장.
     */
    List<KpiValueRow> findByKpiIdAndDimension(
            @Param("kpiId") Long kpiId,
            @Param("dimensionFilter") String dimensionFilter
    );

    /**
     * 알림 건전성 KPI UPSERT (dimension {period, metric} 기준).
     *
     * <p>SPEC-CMS-NOTIFICATION-STAT-001 REQ-NS-006 — 읽음율·오류율을 kpi_value 호환 피드로 적재.
     * uk_kpi_value(kpi_id, dimension) UNIQUE 제약 기반 ON CONFLICT DO UPDATE.
     */
    void upsertNotificationKpi(
            @Param("kpiId") Long kpiId,
            @Param("dimension") String dimension,
            @Param("valueNumeric") java.math.BigDecimal valueNumeric
    );
}
