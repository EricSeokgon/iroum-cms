package kr.co.ircp.cms.domain.board.dto;

import java.time.Instant;

/**
 * 발간자료 단건 상세 응답 DTO.
 * REQ-BOARD-012-R: 발간자료 단건 조회 (contentHtml + categoryId 포함)
 */
public record PublicationDetail(
        Long postId,
        String title,
        String contentHtml,
        int publicationYear,
        Integer publicationMonth,
        String documentType,
        Long categoryId,
        String categoryName,
        int fileCount,
        String isbn,
        String publisher,
        long viewCount,
        Instant publishedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
