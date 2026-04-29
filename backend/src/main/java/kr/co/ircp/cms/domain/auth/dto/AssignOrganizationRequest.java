package kr.co.ircp.cms.domain.auth.dto;

/**
 * 사용자 조직 배정 요청 DTO.
 *
 * <p>SPEC-CMS-002 REQ-AUTH-014 — POST /api/v1/users/{userId}/organization 요청 바디.
 * organizationId가 null이면 조직 배정 해제.
 */
public record AssignOrganizationRequest(
        Long organizationId
) {}
