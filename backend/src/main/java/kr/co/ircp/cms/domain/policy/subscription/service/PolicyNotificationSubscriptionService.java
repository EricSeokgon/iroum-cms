package kr.co.ircp.cms.domain.policy.subscription.service;

import kr.co.ircp.cms.domain.policy.subscription.dto.SubscriptionEntry;
import kr.co.ircp.cms.domain.policy.subscription.dto.SubscriptionUpdateRequest;

import java.util.List;

/**
 * 정책 알림 수신 동의 서비스.
 * REQ-POLICY-004
 */
public interface PolicyNotificationSubscriptionService {

    List<SubscriptionEntry> getMySubscriptions(Long userId);

    void updateMySubscriptions(Long userId, SubscriptionUpdateRequest request);

    /** 발송 직전 옵트아웃 검증용 (이중 검증). */
    boolean isOptedIn(Long userId, String channel, String category);
}
