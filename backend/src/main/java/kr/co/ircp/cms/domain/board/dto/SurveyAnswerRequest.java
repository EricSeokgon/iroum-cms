package kr.co.ircp.cms.domain.board.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * 설문 답변 요청 DTO (질문 1개당 1행).
 * REQ-BOARD-013-D-3: 설문 응답 제출 시 answers 배열 항목.
 *
 * <p>질문 유형별로 채워야 하는 필드:
 * <ul>
 *   <li>TEXT       → answerText</li>
 *   <li>SINGLE/MULTI → answerOptions (JSON 배열 문자열)</li>
 *   <li>RATING     → answerRating (1~5)</li>
 *   <li>DATE       → answerDate</li>
 * </ul>
 */
public record SurveyAnswerRequest(
        @NotNull Long questionId,
        String answerText,
        String answerOptions,
        Short answerRating,
        LocalDate answerDate
) {
}
