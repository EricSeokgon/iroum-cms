package kr.co.ircp.cms.domain.policy.aimatch.service;

import kr.co.ircp.cms.common.util.IpHashUtil;
import kr.co.ircp.cms.domain.policy.aimatch.dto.PolicyFeedbackRequest;
import kr.co.ircp.cms.domain.policy.aimatch.exception.AiFeedbackInvalidException;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * 추천 피드백 기록 서비스.
 *
 * <p>SPEC-CMS-AI-002 REQ-PM-012/013/014 — 입력 검증(VIEWED·null policy_id 거부) →
 * session_ref 해시 → 비동기 적재. DB {@code chk_aprl_feedback} 제약과 일관된다.
 */
@Service
public class PolicyFeedbackService {

    private static final Set<String> VALID_TYPES = Set.of("CLICKED", "APPLIED", "DISMISSED");

    private final PolicyRecommendationLogService logService;

    public PolicyFeedbackService(PolicyRecommendationLogService logService) {
        this.logService = logService;
    }

    /**
     * 피드백 기록. interaction_type이 VIEWED이거나 policy_id 누락이면
     * {@link AiFeedbackInvalidException}(400 AI_FEEDBACK_INVALID), 무적재(REQ-PM-013).
     */
    public void recordFeedback(PolicyFeedbackRequest req) {
        if (req == null || req.interactionType() == null
                || !VALID_TYPES.contains(req.interactionType())) {
            throw new AiFeedbackInvalidException(
                    "피드백 상호작용 유형이 유효하지 않습니다 (CLICKED/APPLIED/DISMISSED만 허용)");
        }
        if (req.policyId() == null) {
            throw new AiFeedbackInvalidException("피드백에는 policy_id가 필수입니다");
        }
        String sessionRef = IpHashUtil.sha256Hex(req.sessionRef());
        logService.logFeedback(sessionRef, req.interactionType(), req.policyId());
    }
}
