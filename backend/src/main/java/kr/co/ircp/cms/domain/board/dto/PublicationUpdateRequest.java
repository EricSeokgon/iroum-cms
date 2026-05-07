package kr.co.ircp.cms.domain.board.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * 발간자료 수정 요청 DTO (모든 필드 선택적).
 * REQ-BOARD-012-U: 발간자료 부분 수정 (관리자)
 */
public record PublicationUpdateRequest(
        @Size(max = 500) String title,
        String contentHtml,
        String contentText,
        @Min(1900) @Max(2100) Integer publicationYear,
        @Min(1) @Max(12) Integer publicationMonth,
        @Size(max = 30) String documentType,
        Long publicationCategoryId,
        @Size(max = 30) String isbn,
        @Size(max = 200) String publisher,
        String metadata
) {
}
