package kr.co.ircp.cms.domain.board.dto;

import java.time.Instant;

/**
 * Q&A 단건 상세 응답 DTO.
 * REQ-BOARD-008: Q&A 단건 조회
 */
public record QnaDetail(
        Long id,
        String title,
        String questionHtml,
        String questionText,
        Long questionerId,
        String answerHtml,
        String answerText,
        Long answererId,
        Instant answeredAt,
        boolean isPrivate,
        String status,
        Instant createdAt,
        Instant updatedAt
) {
}
