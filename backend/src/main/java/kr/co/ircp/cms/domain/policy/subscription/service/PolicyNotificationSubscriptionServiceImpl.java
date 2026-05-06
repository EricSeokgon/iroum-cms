package kr.co.ircp.cms.domain.policy.subscription.service;

import kr.co.ircp.cms.domain.policy.subscription.dto.SubscriptionEntry;
import kr.co.ircp.cms.domain.policy.subscription.dto.SubscriptionUpdateRequest;
import kr.co.ircp.cms.domain.policy.subscription.entity.NotificationSubscription;
import kr.co.ircp.cms.domain.policy.subscription.repository.NotificationSubscriptionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 정책 알림 수신 동의 서비스 구현.
 * REQ-POLICY-004 — 채널·카테고리별 옵트인/옵트아웃, 이중 검증 지원.
 *
 * // @MX:NOTE: [AUTO] 개인정보보호법 제22조의2 자기결정권 — 모든 변경은 audit_log 적재 (후속 sub-task)
 * // @MX:SPEC: REQ-POLICY-004
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PolicyNotificationSubscriptionServiceImpl implements PolicyNotificationSubscriptionService {

    private final NotificationSubscriptionMapper subscriptionMapper;

    @Override
    public List<SubscriptionEntry> getMySubscriptions(Long userId) {
        return subscriptionMapper.findByUserId(userId).stream()
                .map(s -> new SubscriptionEntry(s.getChannel(), s.getCategory(), Boolean.TRUE.equals(s.getOptedIn())))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void updateMySubscriptions(Long userId, SubscriptionUpdateRequest request) {
        for (SubscriptionEntry entry : request.entries()) {
            NotificationSubscription sub = NotificationSubscription.builder()
                    .userId(userId)
                    .channel(entry.channel())
                    .category(entry.category())
                    .optedIn(entry.optedIn())
                    .source("USER")
                    .build();
            subscriptionMapper.upsert(sub);
        }
    }

    @Override
    public boolean isOptedIn(Long userId, String channel, String category) {
        return subscriptionMapper.isOptedIn(userId, channel, category);
    }
}
