package kr.co.ircp.cms.domain.ai.dto;

import java.util.UUID;

/**
 * 시뮬레이션 결과 DTO.
 *
 * <p>SPEC-CMS-AI-001 — projectionResult는 ML 응답 JSON 문자열. 평문 IP는 포함하지 않는다.
 * <p>SPEC-CMS-SIM-001 — 적용된 투영기간(horizonApplied)·추천정책(recommendedPolicies) 추가.
 *
 * @param horizonApplied      실제 적용된 투영 기간(년) — 3 또는 5
 * @param recommendedPolicies 추천 정책 번들 JSON 문자열 (현재 미연동, null 가능)
 */
public record SimulationResultDto(
        UUID sessionId,
        String pdfStatus,
        String projectionResult,
        int horizonApplied,
        String recommendedPolicies
) {
}
