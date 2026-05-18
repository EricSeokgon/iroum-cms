package kr.co.ircp.cms.domain.ai.dto;

/**
 * 시뮬레이션 시작 요청 DTO.
 *
 * <p>SPEC-CMS-AI-001 — PII 없음. 비식별 4개 필드만 전달한다.
 */
public record SimulationStartDto(
        String ksicCode,
        Long capitalAmount,
        Integer foundingYear,
        Long revenueAmount
) {
}
