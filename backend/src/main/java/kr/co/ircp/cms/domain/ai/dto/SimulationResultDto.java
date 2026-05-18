package kr.co.ircp.cms.domain.ai.dto;

import java.util.UUID;

/**
 * 시뮬레이션 결과 DTO.
 *
 * <p>SPEC-CMS-AI-001 — projectionResult는 ML 응답 JSON 문자열.
 * 평문 IP는 포함하지 않는다.
 */
public record SimulationResultDto(
        UUID sessionId,
        String pdfStatus,
        String projectionResult
) {
}
