package kr.co.ircp.cms.domain.board.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Q&A 답변 등록 요청 DTO.
 * REQ-BOARD-008-A: 관리자 답변 등록
 */
public record QnaAnswerRequest(
        @NotBlank String answerHtml
) {
}
