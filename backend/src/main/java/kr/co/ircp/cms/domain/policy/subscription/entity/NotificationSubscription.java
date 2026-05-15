package kr.co.ircp.cms.domain.policy.subscription.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Data;

import java.time.Instant;

/**
 * 수신 동의/거부 엔티티.
 * REQ-POLICY-004 — 채널·카테고리별 옵트인/옵트아웃.
 * SPEC-CMS-007 §4.2.6
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationSubscription {
    private Long id;
    private Long userId;
    /** KAKAO / EMAIL / SMS / INAPP */
    private String channel;
    /** POLICY_MATCH / ANNOUNCEMENT / REMINDER / MARKETING */
    private String category;
    private Boolean optedIn;
    private Instant updatedAt;
    /** USER / ADMIN / SYSTEM */
    private String source;
}
