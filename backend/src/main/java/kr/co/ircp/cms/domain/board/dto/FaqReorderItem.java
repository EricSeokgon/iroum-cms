package kr.co.ircp.cms.domain.board.dto;

import jakarta.validation.constraints.NotNull;

/**
 * FAQ 정렬 순서 변경 항목.
 * REQ-BOARD-007: FAQ 일괄 정렬 변경
 */
public record FaqReorderItem(
        @NotNull Long id,
        int sortOrder
) {
}
