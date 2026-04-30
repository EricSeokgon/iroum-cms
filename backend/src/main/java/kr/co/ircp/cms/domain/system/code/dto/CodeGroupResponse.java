package kr.co.ircp.cms.domain.system.code.dto;

import kr.co.ircp.cms.domain.system.code.entity.CodeGroup;
import lombok.Builder;

import java.time.Instant;

/**
 * 공통코드 그룹 응답 DTO.
 *
 * <p>REQ-SYSTEM-004-D
 */
@Builder
public record CodeGroupResponse(
        Long id,
        String groupCode,
        String name,
        String description,
        String status,
        Instant createdAt,
        Instant updatedAt
) {
    public static CodeGroupResponse from(CodeGroup g) {
        return CodeGroupResponse.builder()
                .id(g.getId())
                .groupCode(g.getGroupCode())
                .name(g.getName())
                .description(g.getDescription())
                .status(g.getStatus())
                .createdAt(g.getCreatedAt())
                .updatedAt(g.getUpdatedAt())
                .build();
    }
}
