package kr.co.ircp.cms.domain.board.dto;

/**
 * 설문 질문 응답 DTO.
 * REQ-BOARD-013-R: 설문 단건 조회 시 questions 배열에 포함되는 항목.
 *
 * <p>options 는 SINGLE/MULTI 일 때 JSON 문자열, 그 외(TEXT/RATING/DATE) 는 NULL 이다.
 */
public record SurveyQuestionDto(
        Long id,
        Long surveyId,
        String questionText,
        String questionType,
        boolean required,
        int sortOrder,
        String options
) {
}
