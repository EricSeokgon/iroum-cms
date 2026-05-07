package kr.co.ircp.cms.domain.board.dto;

import jakarta.validation.constraints.Size;

/**
 * FAQ 수정 요청 DTO.
 * REQ-BOARD-007-U: FAQ 수정
 */
public record FaqUpdateRequest(
        @Size(max = 50) String categoryCode,
        @Size(max = 500) String question,
        String answerHtml,
        Integer sortOrder,
        @Size(max = 20) String status
) {
}
