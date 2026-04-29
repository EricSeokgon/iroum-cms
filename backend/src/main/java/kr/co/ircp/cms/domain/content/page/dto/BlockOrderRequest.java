package kr.co.ircp.cms.domain.content.page.dto;

import java.util.List;

/**
 * 블록 순서 변경 요청 DTO.
 * REQ-CONTENT-006-D-2: 블록 정렬 (트랜잭션 일괄 갱신)
 */
public record BlockOrderRequest(
        List<BlockOrderItem> items
) {
    public record BlockOrderItem(
            Long id,
            int sortOrder
    ) {}
}
