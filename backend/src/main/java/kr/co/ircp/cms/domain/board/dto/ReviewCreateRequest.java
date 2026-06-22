package kr.co.ircp.cms.domain.board.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * 리뷰 작성 요청 DTO.
 * SPEC-CMS-REVIEW-001 REQ-REV-001/008 — 별점(1~5) + 선택적 리뷰 텍스트.
 *
 * @param rating  별점 1~5 정수 (필수)
 * @param content 리뷰 텍스트 (선택 — 별점만 작성 가능)
 */
public record ReviewCreateRequest(
        @Min(value = 1, message = "별점은 1 이상이어야 합니다.")
        @Max(value = 5, message = "별점은 5 이하여야 합니다.")
        int rating,

        @Size(max = 4000, message = "리뷰는 4000자 이하여야 합니다.")
        String content
) {
}
