package kr.co.ircp.cms.domain.ai.dto;

/**
 * AI 지표 조회 질의 DTO.
 *
 * <p>SPEC-CMS-AI-001 — 운영자 지표 목록 필터.
 */
public record AiMetricQueryDto(
        String predictionType,
        int limit
) {
}
