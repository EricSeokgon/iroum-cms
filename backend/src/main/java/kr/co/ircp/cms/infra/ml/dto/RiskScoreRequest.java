package kr.co.ircp.cms.infra.ml.dto;

/**
 * 사업 위험도 점수 예측 요청 (ML 서비스).
 *
 * <p>SPEC-CMS-AI-001 — PII 없음.
 */
public record RiskScoreRequest(
        String ksicCode,
        Long capitalAmount,
        Integer foundingYear,
        Long revenueAmount
) {
}
