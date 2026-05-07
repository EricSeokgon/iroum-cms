package kr.co.ircp.cms.domain.board.dto;

import java.time.Instant;

/**
 * 발간자료 목록 응답 DTO.
 * REQ-BOARD-012-R: 발간자료 목록 페이징 조회
 */
public record PublicationSummary(
        Long postId,
        String title,
        int publicationYear,
        Integer publicationMonth,
        String documentType,
        String categoryName,
        int fileCount,
        String isbn,
        String publisher,
        long viewCount,
        Instant publishedAt
) {
}
