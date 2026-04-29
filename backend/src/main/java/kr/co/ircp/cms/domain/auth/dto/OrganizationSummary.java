package kr.co.ircp.cms.domain.auth.dto;

/**
 * 조직 목록(flat) 응답 DTO.
 *
 * <p>SPEC-CMS-002 REQ-AUTH-014 — GET /api/v1/organizations?status=ACTIVE 응답.
 */
public record OrganizationSummary(
        long id,
        String code,
        String name,
        Long parentId,
        int depth,
        int sortOrder,
        String status
) {}
