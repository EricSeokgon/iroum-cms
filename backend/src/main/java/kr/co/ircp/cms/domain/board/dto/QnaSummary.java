package kr.co.ircp.cms.domain.board.dto;

import java.time.Instant;

/**
 * Q&A 목록 응답 DTO.
 * REQ-BOARD-008: Q&A 목록 조회
 */
public record QnaSummary(
        Long id,
        String title,
        Long questionerId,
        String status,
        boolean isPrivate,
        Instant createdAt
) {
}
