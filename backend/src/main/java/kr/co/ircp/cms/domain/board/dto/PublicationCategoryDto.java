package kr.co.ircp.cms.domain.board.dto;

import java.util.List;

/**
 * 발간자료 카테고리 트리 노드 DTO.
 * REQ-BOARD-012-R: 카테고리 트리 조회 (계층형, 최대 depth 3)
 */
public record PublicationCategoryDto(
        Long id,
        String code,
        String name,
        Long parentId,
        int depth,
        int sortOrder,
        String status,
        List<PublicationCategoryDto> children
) {
}
