package kr.co.ircp.cms.domain.policy.aimatch.service;

import kr.co.ircp.cms.domain.policy.aimatch.dto.PolicyMatchMetricsRequest;
import kr.co.ircp.cms.domain.policy.aimatch.dto.PolicyMatchMetricsResponse;
import kr.co.ircp.cms.domain.policy.aimatch.repository.PolicyRecommendationLogMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 추천 품질 모니터링 서비스.
 *
 * <p>SPEC-CMS-AI-002 REQ-PM-015/016 — CTR·전환율은 매퍼 집계, 커버리지는
 * (추천 등장 고유 정책 수 / SPEC-CMS-007 활성 정책 총수)를 서비스에서 보정한다.
 */
@Service
public class PolicyMatchAdminService {

    private final PolicyRecommendationLogMapper mapper;

    public PolicyMatchAdminService(PolicyRecommendationLogMapper mapper) {
        this.mapper = mapper;
    }

    public PolicyMatchMetricsResponse getMetrics(PolicyMatchMetricsRequest req) {
        PolicyMatchMetricsResponse base = mapper.findMetrics(req);
        long distinctRecommended = mapper.countRecommendedDistinctPolicies(req);
        long activePolicies = mapper.countActivePolicies();
        double coverage = activePolicies == 0
                ? 0.0
                : BigDecimal.valueOf((double) distinctRecommended / activePolicies)
                        .setScale(4, RoundingMode.HALF_UP)
                        .doubleValue();
        return new PolicyMatchMetricsResponse(
                base.period(),
                base.ctr(),
                base.conversionRate(),
                coverage,
                base.totalViewed(),
                base.totalClicked(),
                base.totalApplied());
    }
}
