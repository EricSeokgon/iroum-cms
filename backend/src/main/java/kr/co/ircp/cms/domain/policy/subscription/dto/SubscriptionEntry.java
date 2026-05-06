package kr.co.ircp.cms.domain.policy.subscription.dto;

/** 단일 채널·카테고리 수신 동의 항목. */
public record SubscriptionEntry(
        String channel,
        String category,
        boolean optedIn
) {}
