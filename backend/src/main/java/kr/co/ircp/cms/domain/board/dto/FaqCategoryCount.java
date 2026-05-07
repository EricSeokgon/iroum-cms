package kr.co.ircp.cms.domain.board.dto;

/**
 * FAQ 카테고리별 개수 응답 DTO.
 * REQ-BOARD-007: 카테고리 통계
 */
public record FaqCategoryCount(
        String categoryCode,
        long count
) {
}
