package kr.co.ircp.cms.domain.policy.tracking.service;

import kr.co.ircp.cms.domain.policy.tracking.dto.ConversionStats;
import kr.co.ircp.cms.domain.policy.tracking.dto.TrackEventRequest;

/**
 * 정책 신청·클릭 추적 서비스.
 * REQ-POLICY-005
 */
public interface PolicyTrackingService {

    /** VIEW / CLICK_APPLY / EXTERNAL_REDIRECT / SAVED 적재. */
    void trackEvent(Long userId, Long policyId, TrackEventRequest request);

    /** 정책별 전환 통계 (POLICY_APPLY_CVR). */
    ConversionStats getConversionStats(Long policyId);
}
