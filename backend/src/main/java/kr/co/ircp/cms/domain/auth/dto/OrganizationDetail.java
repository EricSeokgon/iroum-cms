package kr.co.ircp.cms.domain.auth.dto;

import java.time.Instant;

/**
 * 조직 상세 응답 DTO.
 *
 * <p>SPEC-CMS-002 REQ-AUTH-014 — GET /api/v1/organizations/{id},
 * POST /api/v1/organizations, PUT /api/v1/organizations/{id} 응답.
 */
public record OrganizationDetail(
        long id,
        String code,
        String name,
        String description,
        Long parentId,
        int depth,
        String path,
        int sortOrder,
        String status,
        Instant createdAt,
        Instant updatedAt
) {}
