package kr.co.ircp.cms.domain.system.stats.service;

import kr.co.ircp.cms.domain.system.stats.dto.DashboardKpiResponse;

/**
 * 운영 대시보드 서비스 인터페이스.
 * REQ-SYSTEM-002-D: KPI 60초 TTL 캐시 + X-No-Cache 즉시 재계산
 */
public interface DashboardService {

    /**
     * 대시보드 KPI 조회.
     *
     * @param noCache true 시 캐시 우회 후 즉시 재계산
     */
    DashboardKpiResponse getKpi(boolean noCache);
}
