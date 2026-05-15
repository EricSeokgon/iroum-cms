package kr.co.ircp.cms.domain.policy.dispatch.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 발송 예약 엔티티.
 * REQ-POLICY-003 / SPEC-CMS-007 §4.2.7
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationDispatchSchedule {
    private Long id;
    private UUID scheduleUuid;
    private Long policyId;
    /** APPLICATION_OPEN / CLOSING_SOON / RESULT / REMINDER / ANNOUNCEMENT */
    private String dispatchType;
    /** JSONB raw text — {min_score:70, regions:[...]} */
    private String targetFilter;
    private Instant scheduledAt;
    private List<String> channels;
    private Long templateId;
    private Integer priority;
    /** PENDING / PROCESSING / COMPLETED / CANCELLED / FAILED */
    private String status;
    private Long createdBy;
    private Instant createdAt;
    private Instant startedAt;
    private Instant completedAt;
}
