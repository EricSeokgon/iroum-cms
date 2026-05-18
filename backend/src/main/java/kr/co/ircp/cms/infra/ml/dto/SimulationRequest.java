package kr.co.ircp.cms.infra.ml.dto;

/**
 * 사업 시뮬레이션 요청 (ML 서비스).
 *
 * <p>SPEC-CMS-AI-001 — PII 없음.
 */
public record SimulationRequest(
        String ksicCode,
        Long capitalAmount,
        Integer foundingYear,
        Long revenueAmount
) {
}
