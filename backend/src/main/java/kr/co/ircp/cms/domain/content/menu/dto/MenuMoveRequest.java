package kr.co.ircp.cms.domain.content.menu.dto;

/**
 * 메뉴 이동 요청 DTO.
 * REQ-CONTENT-001-D-4: 메뉴 이동 (순환 참조 방지)
 */
public record MenuMoveRequest(
        Long newParentId
) {}
