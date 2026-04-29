package kr.co.ircp.cms.domain.content.menu.dto;

import kr.co.ircp.cms.domain.content.menu.entity.Menu;

/**
 * 메뉴 응답 DTO (단건).
 * REQ-CONTENT-001-D: 메뉴 조회 응답
 */
public record MenuResponse(
        Long id,
        Long siteId,
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
        String status
) {
    public static MenuResponse from(Menu menu) {
        return new MenuResponse(
                menu.getId(),
                menu.getSiteId(),
                menu.getParentId(),
                menu.getCode(),
                menu.getName(),
                menu.getUrl(),
                menu.getTarget(),
                menu.getIcon(),
                menu.getSortOrder(),
                menu.getDepth(),
                menu.getPath(),
                menu.isVisible(),
                menu.getStatus()
        );
    }
}
