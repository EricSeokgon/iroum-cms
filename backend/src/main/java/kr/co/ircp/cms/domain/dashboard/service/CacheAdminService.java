package kr.co.ircp.cms.domain.dashboard.service;

import kr.co.ircp.cms.domain.dashboard.dto.CacheInvalidateRequest;
import kr.co.ircp.cms.domain.dashboard.dto.CacheStatsResponse;

/**
 * 캐시 관리 서비스 (관리자).
 * REQ-VIZ-005-D-5
 */
public interface CacheAdminService {

    void invalidate(CacheInvalidateRequest req);

    CacheStatsResponse stats();
}
