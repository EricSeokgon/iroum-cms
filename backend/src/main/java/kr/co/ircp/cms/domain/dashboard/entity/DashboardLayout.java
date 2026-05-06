package kr.co.ircp.cms.domain.dashboard.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * 사용자별 대시보드 레이아웃 엔티티.
 * REQ-VIZ-002
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardLayout {
    private Long id;
    private Long ownerId;
    private String name;
    private String description;
    private boolean isDefault;
    /** {"columns":12,"row_height":80} */
    private String gridConfig;
    /** 공유 역할 코드 목록 */
    private List<String> sharedWith;
    private Instant createdAt;
    private Instant updatedAt;
}
