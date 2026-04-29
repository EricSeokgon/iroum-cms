package kr.co.ircp.cms.domain.content.menu.dto;

/**
 * 메뉴 순서 변경 요청 DTO.
 * REQ-CONTENT-001-D-3: 메뉴 순서 변경
 */
public record MenuOrderRequest(
        int newSortOrder
) {}
