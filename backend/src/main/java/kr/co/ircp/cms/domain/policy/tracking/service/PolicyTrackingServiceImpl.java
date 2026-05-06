package kr.co.ircp.cms.domain.policy.tracking.service;

import kr.co.ircp.cms.domain.policy.tracking.dto.ConversionStats;
import kr.co.ircp.cms.domain.policy.tracking.dto.TrackEventRequest;
import kr.co.ircp.cms.domain.policy.tracking.entity.PolicyApplicationLog;
import kr.co.ircp.cms.domain.policy.tracking.repository.PolicyApplicationLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 정책 신청·클릭 추적 서비스 구현.
 * REQ-POLICY-005
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PolicyTrackingServiceImpl implements PolicyTrackingService {

    private final PolicyApplicationLogMapper logMapper;

    @Override
    @Transactional
    public void trackEvent(Long userId, Long policyId, TrackEventRequest request) {
        PolicyApplicationLog log = PolicyApplicationLog.builder()
                .userId(userId)
                .policyId(policyId)
                .source(request.source())
                .action(request.action())
                .notificationSendId(request.notificationSendId())
                .userAgent(request.userAgent())
                .ipAddress(request.ipAddress())
                .build();
        logMapper.insert(log);
    }

    @Override
    public ConversionStats getConversionStats(Long policyId) {
        long views = logMapper.countByPolicyAndAction(policyId, "VIEW");
        long clicks = logMapper.countByPolicyAndAction(policyId, "CLICK_APPLY");
        long redirects = logMapper.countByPolicyAndAction(policyId, "EXTERNAL_REDIRECT");
        long saves = logMapper.countByPolicyAndAction(policyId, "SAVED");
        return ConversionStats.compute(policyId, views, clicks, redirects, saves);
    }
}
