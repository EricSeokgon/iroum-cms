package kr.co.ircp.cms.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 조직 생성 요청 DTO.
 *
 * <p>SPEC-CMS-002 REQ-AUTH-014 — POST /api/v1/organizations 요청 바디.
 */
public record OrganizationCreateRequest(
        @NotBlank @Size(max = 50)  String code,
        @NotBlank @Size(max = 200) String name,
        String description,
        Long parentId,
        int sortOrder
) {}
