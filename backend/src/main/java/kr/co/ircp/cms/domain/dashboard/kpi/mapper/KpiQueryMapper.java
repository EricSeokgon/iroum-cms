package kr.co.ircp.cms.domain.dashboard.kpi.mapper;

import kr.co.ircp.cms.domain.dashboard.kpi.dto.KpiQueryRequest;
import kr.co.ircp.cms.domain.dashboard.kpi.dto.KpiValueResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * SPEC-CMS-KPI-001 Phase 2: KPI 조회 MyBatis 매퍼.
 *
 * <p>모든 조회는 kpi_value JOIN kpi_definition 으로 code/name 을 함께 반환하며,
 * dimension JSONB containment(@>) 와 jsonb_exists(granularity) 동적 필터를 적용한다.
 * AC-019 상한을 위해 search 는 LIMIT/OFFSET 을, count 는 전체 행 수를 반환한다.
 */
// @MX:ANCHOR: [AUTO] KpiQueryMapper — KPI 조회 동적 필터/카운트/전환율 계약
// @MX:REASON: KpiQueryServiceImpl 의 조회/카운트/전환율 경로가 모두 본 매퍼에 의존 (fan_in 집중)
@Mapper
public interface KpiQueryMapper {

    /**
     * 동적 필터(kpiCode/calculated_at 범위/dimension containment/granularity 키)로 KPI 행 조회.
     * dimensionJson 은 호출 전 정규식 검증을 통과한 안전한 JSON 문자열이어야 한다.
     */
    List<KpiValueResponse> search(@Param("req") KpiQueryRequest req);

    /** 동일 필터의 전체 행 수 (페이지네이션 메타 totalCount/hasMore 산출용). */
    long count(@Param("req") KpiQueryRequest req);

    /**
     * SPEC-CMS-KPI-001 Phase 3: export 용 전체 행 조회.
     *
     * <p>조회 API 의 1000행 LIMIT 와 달리, export 는 max_export_rows(기본 1,000,000) 까지
     * 모든 일치 행을 반환해야 한다. 안전을 위해 maxRows 로 상한을 강제하여 OOM 방어와
     * SXSSFWorkbook 윈도우 쓰기를 전제로 한다.
     *
     * @param req     필터 조건 (granularityKey/dimension containment 동일 적용)
     * @param maxRows 전체 상한 (max_export_rows)
     */
    List<KpiValueResponse> searchForExport(
            @Param("req") KpiQueryRequest req,
            @Param("maxRows") long maxRows);

    /** policy_match_stats_monthly 에서 해당 월 정책 매칭 통계 행 수 (PREPARING 판정용). */
    int countConversionStats(@Param("statMonth") String statMonth);
}
