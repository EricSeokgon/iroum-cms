package kr.co.ircp.cms.domain.content.menu.dto;

import java.util.List;

/**
 * 메뉴 트리 노드 DTO (children 중첩 구조).
 * REQ-CONTENT-001-D-2: 메뉴 트리 조회 응답
 */
public record MenuTreeNode(
        Long id,
        Long parentId,
        String code,
        String name,
        String url,
        String target,
        String icon,
        int sortOrder,
        short depth,
        String path,
        boolean isVisible,
        boolean accessible,
        List<MenuTreeNode> children
) {}
