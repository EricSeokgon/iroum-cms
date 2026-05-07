package kr.co.ircp.cms.domain.board.dto;

import java.time.Instant;

/**
 * FAQ 단건 상세 응답 DTO.
 * REQ-BOARD-007: FAQ 단건 조회
 */
public record FaqDetail(
        Long id,
        String categoryCode,
        String question,
        String answerHtml,
        String answerText,
        int sortOrder,
        long viewCount,
        String status,
        Instant createdAt,
        Instant updatedAt
) {
}
