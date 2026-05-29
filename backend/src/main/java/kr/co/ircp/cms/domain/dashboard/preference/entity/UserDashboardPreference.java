package kr.co.ircp.cms.domain.dashboard.preference.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 사용자별 대시보드 개인화 환경설정 엔티티 (1:1, PK = userId).
 *
 * <p>SPEC-CMS-DASHBOARD-PERSONALIZE-001 §5.1
 *
 * <ul>
 *   <li>{@code hiddenWidgetInstanceIds} : layout_id → [instance_id, ...] JSON 텍스트.
 *       MyBatis 단계에서는 JSON 문자열로 보관하고, 서비스에서 ObjectMapper 로 직렬화/역직렬화한다.</li>
 *   <li>{@code theme/density/fontScale/colorPalettePreference} : DB CHECK 제약과 동일한 enum 집합.</li>
 *   <li>{@code schemaVersion} : 향후 스키마 변경 시 lazy migration 의 분기 기준.</li>
 * </ul>
 */
// @MX:NOTE: [AUTO] hidden_widget_instance_ids 는 JSON {"layout_id": [instance_id, ...]} 구조
// @MX:SPEC: SPEC-CMS-DASHBOARD-PERSONALIZE-001 §5.1
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDashboardPreference {

    /** users.id 외래키 + PK (1:1). */
    private Long userId;

    /** JSON 문자열. 형식: {"{layout_id}": ["{instance_id}", ...]} */
    private String hiddenWidgetInstanceIds;

    /** LIGHT / DARK / SYSTEM */
    private String theme;

    /** COMPACT / NORMAL / COMFORTABLE */
    private String density;

    /** 0.875 / 1.00 / 1.125 */
    private BigDecimal fontScale;

    /** DEFAULT / COLORBLIND / MONOCHROME */
    private String colorPalettePreference;

    private boolean sidebarCollapsed;

    private short schemaVersion;

    private Instant createdAt;

    private Instant updatedAt;

    /** SPEC-CMS-DASHBOARD-PERSONALIZE-001 §5.1 DEFAULT 값으로 생성. */
    public static UserDashboardPreference defaults(Long userId) {
        return UserDashboardPreference.builder()
                .userId(userId)
                .hiddenWidgetInstanceIds("{}")
                .theme("SYSTEM")
                .density("NORMAL")
                .fontScale(new BigDecimal("1.00"))
                .colorPalettePreference("DEFAULT")
                .sidebarCollapsed(false)
                .schemaVersion((short) 1)
                .build();
    }
}
