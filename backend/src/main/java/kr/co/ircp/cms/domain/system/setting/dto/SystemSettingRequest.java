package kr.co.ircp.cms.domain.system.setting.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 시스템 설정 수정 요청 DTO.
 *
 * <p>REQ-SYSTEM-005-D
 */
public record SystemSettingRequest(
        @NotBlank
        String value,

        String description
) {}
