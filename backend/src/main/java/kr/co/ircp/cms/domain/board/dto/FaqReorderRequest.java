package kr.co.ircp.cms.domain.board.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * FAQ 일괄 정렬 변경 요청 DTO.
 * REQ-BOARD-007: 정렬 순서 일괄 업데이트
 */
public record FaqReorderRequest(
        @NotEmpty @Valid List<FaqReorderItem> items
) {
}
