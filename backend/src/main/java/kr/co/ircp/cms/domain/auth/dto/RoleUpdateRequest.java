package kr.co.ircp.cms.domain.auth.dto;

import jakarta.validation.constraints.Size;

import java.util.Set;

/**
 * 역할 수정 요청 DTO.
 *
 * <p>SPEC-CMS-002 REQ-AUTH-013 — PUT /api/v1/roles/{code} 요청.
 * is_system=true인 경할 name/description만 수정 가능.
 *
 * @param name            역할 표시명 (null이면 변경 없음)
 * @param description     역할 설명 (null이면 변경 없음)
 * @param permissionCodes 권한 코드 집합 (null이면 변경 없음, 빈 Set이면 전체 해제)
 */
public record RoleUpdateRequest(
        @Size(max = 100)
        String name,

        String description,

        Set<String> permissionCodes
) {}
