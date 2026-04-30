package kr.co.ircp.cms.domain.system.code.dto;

import kr.co.ircp.cms.domain.system.code.entity.Code;
import lombok.Builder;

import java.time.Instant;

/**
 * 공통코드 응답 DTO.
 *
 * <p>REQ-SYSTEM-004-D
 */
@Builder
public record CodeResponse(
        Long id,
        String groupCode,
        String code,
        String name,
        String description,
        Integer sortOrder,
        String status,
        String extraData,
        Instant createdAt,
        Instant updatedAt
) {
    public static CodeResponse from(Code c) {
        return CodeResponse.builder()
                .id(c.getId())
                .groupCode(c.getGroupCode())
                .code(c.getCode())
                .name(c.getName())
                .description(c.getDescription())
                .sortOrder(c.getSortOrder())
                .status(c.getStatus())
                .extraData(c.getExtraData())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .build();
    }
}
