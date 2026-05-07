package kr.co.ircp.cms.domain.board.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Q&A 질문 작성 요청 DTO.
 * REQ-BOARD-008-C: Q&A 질문 등록
 */
public record QnaCreateRequest(
        @NotBlank @Size(max = 500) String title,
        @NotBlank String questionHtml,
        boolean isPrivate
) {
}
