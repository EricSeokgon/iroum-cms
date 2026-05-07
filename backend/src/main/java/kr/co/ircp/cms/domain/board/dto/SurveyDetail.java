package kr.co.ircp.cms.domain.board.dto;

import java.time.Instant;
import java.util.List;

/**
 * 설문조사 단건 상세 응답 DTO.
 * REQ-BOARD-013-R: 설문 단건 조회 (descriptionHtml + questions 포함)
 */
public record SurveyDetail(
        Long id,
        String title,
        String descriptionHtml,
        String status,
        boolean isAnonymous,
        Integer maxResponses,
        int responseCount,
        Instant startAt,
        Instant endAt,
        Instant createdAt,
        List<SurveyQuestionDto> questions
) {
}
