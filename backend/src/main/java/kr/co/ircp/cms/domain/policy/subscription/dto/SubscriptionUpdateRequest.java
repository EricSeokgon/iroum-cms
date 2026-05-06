package kr.co.ircp.cms.domain.policy.subscription.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/** PUT /api/v1/policy/subscriptions/me 요청. */
public record SubscriptionUpdateRequest(
        @NotEmpty List<SubscriptionEntry> entries
) {}
