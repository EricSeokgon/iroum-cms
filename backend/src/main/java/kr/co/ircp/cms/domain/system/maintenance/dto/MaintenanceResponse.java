package kr.co.ircp.cms.domain.system.maintenance.dto;

import kr.co.ircp.cms.domain.system.maintenance.entity.Maintenance;
import lombok.Builder;

import java.time.Instant;

/**
 * 점검 응답 DTO.
 *
 * <p>REQ-SYSTEM-005-D
 */
@Builder
public record MaintenanceResponse(
        Long id,
        String title,
        String messageKo,
        String messageEn,
        Instant startAt,
        Instant endAt,
        String status,
        Boolean allowAdminAccess,
        Instant createdAt,
        Instant updatedAt
) {
    public static MaintenanceResponse from(Maintenance m) {
        return MaintenanceResponse.builder()
                .id(m.getId())
                .title(m.getTitle())
                .messageKo(m.getMessageKo())
                .messageEn(m.getMessageEn())
                .startAt(m.getStartAt())
                .endAt(m.getEndAt())
                .status(m.getStatus())
                .allowAdminAccess(m.getAllowAdminAccess())
                .createdAt(m.getCreatedAt())
                .updatedAt(m.getUpdatedAt())
                .build();
    }
}
