package kr.co.ircp.cms.domain.board.dto;

/**
 * 개별 응답의 질문별 답변 상세.
 * SPEC-CMS-SURVEY-001 REQ-SURVEY-010: 응답 펼침 시 질문 순서대로 표시.
 *
 * <p>answerText 는 질문 유형에 따라 의미가 다르다:
 * TEXT=자유응답, SINGLE/MULTI=선택 옵션 JSON, RATING=점수(문자열), DATE=날짜(문자열).
 */
public record SurveyAnswerDetail(
        Long questionId,
        String questionText,
        String questionType,
        String answerText
) {
}
