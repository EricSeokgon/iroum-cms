package kr.co.ircp.cms.domain.board.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/**
 * 설문조사 생성 요청 DTO.
 * REQ-BOARD-013-C: 설문 신규 등록 (관리자)
 */
public record SurveyCreateRequest(
        @NotBlank @Size(max = 500) String title,
        String descriptionHtml,
        String descriptionText,
        @NotNull Instant startAt,
        @NotNull Instant endAt,
        boolean isAnonymous,
        Integer maxResponses,
        @NotEmpty @Valid List<SurveyQuestionRequest> questions
) {
}
