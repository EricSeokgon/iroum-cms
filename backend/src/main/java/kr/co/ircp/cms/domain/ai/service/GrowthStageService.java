package kr.co.ircp.cms.domain.ai.service;

import kr.co.ircp.cms.domain.ai.dto.GrowthStageQueryDto;
import kr.co.ircp.cms.domain.ai.dto.GrowthStageResultDto;

/**
 * 성장단계 예측 서비스.
 *
 * <p>SPEC-CMS-AI-001 — ML 추론 + 비동기 로그 + 타임아웃/서킷 폴백.
 */
public interface GrowthStageService {

    /**
     * 성장단계를 예측한다. ML 실패 시 fallback=true 결과를 반환한다.
     */
    GrowthStageResultDto predict(GrowthStageQueryDto query);
}
