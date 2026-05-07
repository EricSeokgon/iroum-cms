package kr.co.ircp.cms.domain.board.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * 설문 응답 제출 요청 DTO.
 * REQ-BOARD-013-D-3: POST /api/v1/surveys/{id}/responses
 */
public record SurveySubmitRequest(
        @NotEmpty @Valid List<SurveyAnswerRequest> answers
) {
}
