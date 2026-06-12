package kr.co.ircp.cms.domain.auth.dto;

import java.util.List;

/**
 * 현재 사용자 유효 권한 집합 응답 DTO.
 *
 * <p>SPEC-CMS-RBAC-001 REQ-RBAC-003 — GET /api/v1/me/permissions 응답.
 * 프론트엔드 권한 판정(usePermission)의 단일 진실 소스.
 *
 * @param roles       사용자 역할 코드 목록 (alias 포함, 정렬됨)
 * @param permissions 유효 권한 코드 목록 (alias·계층 상속 반영, 정렬됨)
 */
public record MePermissionsResponse(
        List<String> roles,
        List<String> permissions
) {}
