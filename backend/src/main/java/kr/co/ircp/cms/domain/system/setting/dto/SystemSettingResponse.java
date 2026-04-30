package kr.co.ircp.cms.domain.system.setting.dto;

import kr.co.ircp.cms.domain.system.setting.entity.SystemSetting;
import lombok.Builder;

import java.time.Instant;

/**
 * 시스템 설정 응답 DTO.
 *
 * <p>REQ-SYSTEM-005-D
 */
@Builder
public record SystemSettingResponse(
        String key,
        String value,
        String valueType,
        String description,
        Instant updatedAt
) {
    public static SystemSettingResponse from(SystemSetting s) {
        return SystemSettingResponse.builder()
                .key(s.getKey())
                .value(s.getValue())
                .valueType(s.getValueType())
                .description(s.getDescription())
                .updatedAt(s.getUpdatedAt())
                .build();
    }
}
