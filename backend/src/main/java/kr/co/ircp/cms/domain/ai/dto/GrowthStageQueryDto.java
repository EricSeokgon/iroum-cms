package kr.co.ircp.cms.domain.ai.dto;

/**
 * 성장단계 예측 질의 DTO.
 *
 * <p>SPEC-CMS-AI-001 — PII 없음. 비식별 4개 필드만 사용한다.
 */
public record GrowthStageQueryDto(
        String ksicCode,
        Long capitalAmount,
        Integer foundingYear,
        Long revenueAmount
) {
}
