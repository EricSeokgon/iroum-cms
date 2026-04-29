package kr.co.ircp.cms.domain.auth.dto;

import jakarta.validation.constraints.Size;

/**
 * 조직 수정 요청 DTO.
 *
 * <p>SPEC-CMS-002 REQ-AUTH-014 — PUT /api/v1/organizations/{id} 요청 바디.
 * 모든 필드가 선택적 (null이면 기존 값 유지).
 */
public record OrganizationUpdateRequest(
        @Size(max = 200) String name,
        String description,
        Long parentId,
        Integer sortOrder,
        String status
) {}
