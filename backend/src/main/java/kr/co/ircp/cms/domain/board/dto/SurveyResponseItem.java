package kr.co.ircp.cms.domain.board.dto;

import java.time.Instant;
import java.util.List;

/**
 * 설문 개별 응답 항목 DTO.
 * SPEC-CMS-SURVEY-001 REQ-SURVEY-008/009/010: 응답 목록 + 익명 표기 + 답변 펼침.
 *
 * <p>익명 설문이거나 비로그인 응답이면 respondentId/respondentName 은 null 이다.
 */
public record SurveyResponseItem(
        Long responseId,
        Long respondentId,
        String respondentName,
        Instant submittedAt,
        List<SurveyAnswerDetail> answers
) {
}
