package kr.co.ircp.cms.domain.ai.service;

import kr.co.ircp.cms.domain.ai.dto.RiskScoreQueryDto;
import kr.co.ircp.cms.domain.ai.dto.RiskScoreResultDto;

/**
 * 사업 위험도 점수 서비스.
 *
 * <p>SPEC-CMS-AI-001 — ML 추론 + 임계 기반 등급 재계산 + 설명(topFactors).
 */
public interface RiskScoreService {

    /** 위험도를 산출한다. riskGrade는 서버 임계 설정으로 재계산한다. */
    RiskScoreResultDto score(RiskScoreQueryDto query);

    /** 예측 로그에서 topFactors 설명을 조회한다. 미존재 시 AiPredictionNotFoundException. */
    RiskScoreResultDto explain(Long predictionId);
}
