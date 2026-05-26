package kr.co.ircp.cms.domain.content.popup.dto;

import kr.co.ircp.cms.domain.content.popup.entity.Popup;

import java.time.Instant;
import java.util.List;

/**
 * 팝업 응답 DTO (관리자용 — 전체 필드).
 * REQ-CONTENT-008-D
 */
public record PopupResponse(
        Long id,
        Long siteId,
        String title,
        String name,
        String contentHtml,
        String position,
        Integer xOffset,
        Integer yOffset,
        Integer width,
        Integer height,
        Instant showFrom,
        Instant showUntil,
        boolean showTodayClose,
        int displayPriority,
        String targetType,
        List<String> targetRoleCodes,
        String status,
        boolean isActive,
        Instant createdAt,
        Instant updatedAt
) {
    public static PopupResponse from(Popup popup) {
        return new PopupResponse(
                popup.getId(),
                popup.getSiteId(),
                popup.getTitle(),
                popup.getTitle(),
                popup.getContentHtml(),
                popup.getPosition(),
                popup.getXOffset(),
                popup.getYOffset(),
                popup.getWidth(),
                popup.getHeight(),
                popup.getShowFrom(),
                popup.getShowUntil(),
                popup.isShowTodayClose(),
                popup.getDisplayPriority(),
                popup.getTargetType(),
                popup.getTargetRoleCodes(),
                popup.getStatus(),
                "ACTIVE".equals(popup.getStatus()),
                popup.getCreatedAt(),
                popup.getUpdatedAt()
        );
    }
}
