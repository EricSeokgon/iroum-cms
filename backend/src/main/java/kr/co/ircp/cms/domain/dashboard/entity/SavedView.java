package kr.co.ircp.cms.domain.dashboard.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * 저장된 필터/뷰 엔티티.
 * REQ-VIZ-004-D-3
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SavedView {
    private Long id;
    private Long ownerId;
    private Long dashboardId;
    private String name;
    private String description;
    /** {period:{...}, feature:[...], industry:[...], region:[...], role:[...]} */
    private String filterState;
    private boolean isDefault;
    private boolean isShared;
    private List<String> sharedWith;
    private Instant createdAt;
    private Instant lastUsedAt;
}
