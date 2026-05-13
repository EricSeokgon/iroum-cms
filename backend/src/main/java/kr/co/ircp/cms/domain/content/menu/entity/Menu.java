package kr.co.ircp.cms.domain.content.menu.entity;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * 메뉴 트리 엔티티.
 * REQ-CONTENT-001-D: 메뉴 트리 관리 (Adjacency List + Materialized Path)
 *
 * // @MX:ANCHOR: [AUTO] Menu — 메뉴 트리의 핵심 엔티티
 * // @MX:REASON: MenuService, MenuPermissionService, PageService에서 fan_in >= 3으로 참조
 */
@Data
@Builder
public class Menu {

    private Long id;
    /** 사이트 ID (FK → site.id) */
    private Long siteId;
    /** 부모 메뉴 ID (null이면 루트) */
    private Long parentId;
    /** 사이트 내 유일 코드 */
    private String code;
    private String name;
    private String url;
    /** _self | _blank */
    private String target;
    private String icon;
    private int sortOrder;
    /** 깊이 (루트=1, 최대=5) */
    private short depth;
    /** Materialized Path (예: /1/3/12) */
    private String path;
    private boolean isVisible;
    private String status;
    private String metadata;
    private Instant createdAt;
    private Instant updatedAt;
}
