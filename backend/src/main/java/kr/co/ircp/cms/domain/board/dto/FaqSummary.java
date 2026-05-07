package kr.co.ircp.cms.domain.board.dto;

import java.time.Instant;

/**
 * FAQ 목록 응답 DTO.
 * REQ-BOARD-007: FAQ 카테고리·정렬·검색
 */
public record FaqSummary(
        Long id,
        String categoryCode,
        String question,
        int sortOrder,
        long viewCount,
        String status,
        Instant createdAt
) {
}
