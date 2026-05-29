package kr.co.ircp.cms.domain.notification.admin.dto;

import java.time.Instant;
import kr.co.ircp.cms.domain.notification.admin.entity.AdminNotification;

/**
 * 관리자 알림 단건 응답 DTO.
 *
 * <p>SPEC-CMS-NOTIFICATION-CENTER-001 REQ-NC-001/002/004 — 목록 및 단건 응답에 사용.
 */
public record AdminNotificationDto(
        Long id,
        Long adminUserId,
        String type,
        String severity,
        String title,
        String body,
        String refType,
        Long refId,
        String status,
        Instant readAt,
        Instant archivedAt,
        Instant createdAt
) {
    /** 엔티티 → DTO 변환 헬퍼. */
    public static AdminNotificationDto from(AdminNotification e) {
        return new AdminNotificationDto(
                e.getId(),
                e.getAdminUserId(),
                e.getType(),
                e.getSeverity(),
                e.getTitle(),
                e.getBody(),
                e.getRefType(),
                e.getRefId(),
                e.getStatus(),
                e.getReadAt(),
                e.getArchivedAt(),
                e.getCreatedAt()
        );
    }
}
