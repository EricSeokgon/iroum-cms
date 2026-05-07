package kr.co.ircp.cms.domain.board.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 발간자료 생성 요청 DTO.
 * REQ-BOARD-012-C: 발간자료 신규 등록 (관리자)
 */
public record PublicationCreateRequest(
        @NotBlank @Size(max = 500) String title,
        String contentHtml,
        String contentText,
        @NotNull @Min(1900) @Max(2100) Integer publicationYear,
        @Min(1) @Max(12) Integer publicationMonth,
        // REPORT/BROCHURE/RESEARCH/GUIDE/OTHER
        @NotBlank @Size(max = 30) String documentType,
        Long publicationCategoryId,
        @Size(max = 30) String isbn,
        @Size(max = 200) String publisher,
        String metadata
) {
}
