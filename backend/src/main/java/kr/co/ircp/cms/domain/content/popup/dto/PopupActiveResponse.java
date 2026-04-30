package kr.co.ircp.cms.domain.content.popup.dto;

import kr.co.ircp.cms.domain.content.popup.entity.Popup;

import java.time.Instant;
import java.util.List;

/**
 * 활성 팝업 응답 DTO (공개 API용).
 * REQ-CONTENT-008-D-2: show_today_close=true 시 cookie_key 포함
 * REQ-CONTENT-008-D-3: 타겟 타입별 필터링 후 반환
 */
public record PopupActiveResponse(
        Long id,
        String title,
        String contentHtml,
        String position,
        Integer xOffset,
        Integer yOffset,
        Integer width,
        Integer height,
        Instant showFrom,
        Instant showUntil,
        boolean showTodayClose,
        /** show_today_close=true 시 쿠키 키 (null이면 오늘 그만 보기 미지원) */
        String cookieKey,
        int displayPriority,
        String targetType,
        List<String> targetRoleCodes
) {
    public static PopupActiveResponse from(Popup popup) {
        return new PopupActiveResponse(
                popup.getId(),
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
                popup.isShowTodayClose() ? popup.getCookieKey() : null,
                popup.getDisplayPriority(),
                popup.getTargetType(),
                popup.getTargetRoleCodes()
        );
    }
}
