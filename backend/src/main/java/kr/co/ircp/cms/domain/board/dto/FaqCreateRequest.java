package kr.co.ircp.cms.domain.board.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * FAQ 생성 요청 DTO.
 * REQ-BOARD-007-C: FAQ 생성
 */
public record FaqCreateRequest(
        @NotBlank @Size(max = 50) String categoryCode,
        @NotBlank @Size(max = 500) String question,
        @NotBlank String answerHtml,
        int sortOrder
) {
}
