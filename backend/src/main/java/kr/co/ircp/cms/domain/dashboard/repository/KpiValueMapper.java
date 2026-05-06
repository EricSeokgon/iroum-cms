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
}
