package kr.co.ircp.cms.domain.board.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/**
 * 설문조사 수정 요청 DTO.
 * REQ-BOARD-013-U: 설문 부분 수정 (관리자) — 모든 필드 선택적.
 *
 * <p>questions 가 NOT NULL 인 경우 기존 질문을 모두 삭제하고 새 목록으로 대체한다.
 */
public record SurveyUpdateRequest(
        @Size(max = 500) String title,
        String descriptionHtml,
        String descriptionText,
        Instant startAt,
        Instant endAt,
        Boolean isAnonymous,
        Integer maxResponses,
        String status,
        @Valid List<SurveyQuestionRequest> questions
) {
}
