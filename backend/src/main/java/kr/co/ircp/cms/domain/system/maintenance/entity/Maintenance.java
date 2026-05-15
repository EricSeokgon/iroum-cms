package kr.co.ircp.cms.domain.system.maintenance.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Getter;

import java.time.Instant;

/**
 * 점검 모드 엔티티.
 *
 * <p>REQ-SYSTEM-005-D — 점검 등록·활성화·자동완료.
 * status: SCHEDULED → ACTIVE → COMPLETED | CANCELLED
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Maintenance {

    private Long id;
    private String title;
    private String messageKo;
    private String messageEn;
    private Instant startAt;
    private Instant endAt;
    /** SCHEDULED | ACTIVE | COMPLETED | CANCELLED */
    private String status;
    /** false 시 ADMIN도 점검 중 차단 */
    private Boolean allowAdminAccess;
    private Long createdBy;
    private Instant createdAt;
    private Instant updatedAt;
}
