package kr.co.ircp.cms.domain.policy.tracking.entity;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * 정책 신청·클릭 추적 엔티티.
 * REQ-POLICY-005 — POLICY_APPLY_CVR KPI 원천.
 * SPEC-CMS-007 §4.2.9
 */
@Data
@Builder
public class PolicyApplicationLog {
    private Long id;
    private Long userId;
    private Long policyId;
    /** NOTIFICATION / SEARCH / RECOMMENDATION / DIRECT */
    private String source;
    private Long notificationSendId;
    /** VIEW / CLICK_APPLY / EXTERNAL_REDIRECT / SAVED */
    private String action;
    private Instant occurredAt;
    private String userAgent;
    private String ipAddress;
}
