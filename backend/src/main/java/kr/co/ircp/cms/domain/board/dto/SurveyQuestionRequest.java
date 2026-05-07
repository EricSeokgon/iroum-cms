package kr.co.ircp.cms.domain.board.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 설문 질문 생성/수정 요청 DTO.
 * REQ-BOARD-013-C: 설문 생성 시 questions 배열 항목.
 *
 * <p>questionType 이 SINGLE/MULTI 인 경우 options 가 필수(JSON 문자열).
 * TEXT/RATING/DATE 인 경우 options 는 NULL 허용.
 */
public record SurveyQuestionRequest(
        @NotBlank String questionText,
        @NotBlank String questionType,
        boolean required,
        int sortOrder,
        String options
) {
}
