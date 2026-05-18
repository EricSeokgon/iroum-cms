package kr.co.ircp.cms.infra.ml.dto;

/**
 * 성장단계 예측 요청 (ML 서비스).
 *
 * <p>SPEC-CMS-AI-001 — PII 없음. ksicCode/capitalAmount/foundingYear/revenueAmount 4개 필드만 전송.
 */
public record GrowthStageRequest(
        String ksicCode,
        Long capitalAmount,
        Integer foundingYear,
        Long revenueAmount
) {
}
