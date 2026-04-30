package kr.co.ircp.cms.domain.system.code.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 공통코드 생성/수정 요청 DTO.
 *
 * <p>REQ-SYSTEM-004-D
 */
public record CodeRequest(
        @NotBlank @Size(max = 50)
        String groupCode,

        @NotBlank @Size(max = 50)
        String code,

        @NotBlank @Size(max = 200)
        String name,

        String description,
        Integer sortOrder,
        String extraData
) {}
