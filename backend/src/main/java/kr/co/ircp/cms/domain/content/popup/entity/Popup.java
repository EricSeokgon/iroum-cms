package kr.co.ircp.cms.domain.content.popup.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Data;

import java.time.Instant;
import java.util.List;

/**
 * 팝업 엔티티.
 * REQ-CONTENT-008-D: 팝업 마스터 (노출 기간·타겟·우선순위 관리)
 *
 * // @MX:ANCHOR: [AUTO] Popup — 팝업 콘텐츠 루트 엔티티
 * // @MX:REASON: PopupService, PopupController, SitemapService에서 fan_in >= 3으로 참조
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Popup {

    private Long id;
    private Long siteId;
    private String title;
    /** Jsoup sanitize 적용 HTML 콘텐츠 */
    private String contentHtml;
    /** CENTER|TOP_RIGHT|BOTTOM_RIGHT|TOP_LEFT|BOTTOM_LEFT|CUSTOM */
    private String position;
    private Integer xOffset;
    private Integer yOffset;
    private Integer width;
    private Integer height;
    private Instant showFrom;
    private Instant showUntil;
    /** true면 클라이언트 "오늘 그만 보기" 쿠키 설정 가능 */
    private boolean showTodayClose;
    private int displayPriority;
    /** ALL|MEMBER|ROLE */
    private String targetType;
    /** target_type=ROLE 일 때 허용 역할 코드 배열 */
    private List<String> targetRoleCodes;
    /** ACTIVE|INACTIVE */
    private String status;
    /** show_today_close=true 시 응답에 포함되는 쿠키 키 */
    private String cookieKey;
    private Instant createdAt;
    private Instant updatedAt;
}
