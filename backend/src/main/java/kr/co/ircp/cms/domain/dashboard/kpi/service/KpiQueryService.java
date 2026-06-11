package kr.co.ircp.cms.domain.dashboard.kpi.service;

import kr.co.ircp.cms.domain.dashboard.kpi.dto.KpiQueryRequest;
import kr.co.ircp.cms.domain.dashboard.kpi.dto.KpiQueryResult;
import kr.co.ircp.cms.domain.dashboard.kpi.dto.KpiValueResponse;

/**
 * SPEC-CMS-KPI-001 Phase 2: KPI 조회 서비스.
 *
 * <p>JSONB 동적 필터 + 5분 캐시 + LIMIT 1000 상한 + DDL/DML 인젝션 방어 + 전환율 PREPARING 처리.
 */
public interface KpiQueryService {

    /**
     * 조건에 맞는 KPI 집계값을 조회한다(REQ-KPI-002).
     *
     * <ul>
     *   <li>AC-013: 동일 필터 5분 내 재조회는 chart_dataset_cache 에서 응답(재집계 회피).</li>
     *   <li>AC-019: 최대 1000행 반환 + totalCount/hasMore 메타.</li>
     *   <li>AC-021: dimensionJson 에 DDL/DML 토큰 포함 시 IllegalArgumentException(→400).</li>
     * </ul>
     */
    KpiQueryResult query(KpiQueryRequest request);

    /**
     * 정책 매칭 전환율 퍼널을 조회한다(REQ-KPI-002 AC-006).
     * policy_match_stats_monthly 에 해당 월 데이터가 없으면 dataState=PREPARING 으로 반환한다.
     */
    KpiValueResponse conversionFunnel(String statMonth);
}
