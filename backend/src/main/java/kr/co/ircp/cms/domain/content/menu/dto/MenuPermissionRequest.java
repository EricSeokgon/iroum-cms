package kr.co.ircp.cms.domain.content.menu.dto;

import java.util.List;

/**
 * 메뉴-권한 매핑 요청 DTO.
 * REQ-CONTENT-002-D-1: 메뉴별 권한 일괄 저장(replace)
 */
public record MenuPermissionRequest(
        List<String> permissionCodes
) {}
