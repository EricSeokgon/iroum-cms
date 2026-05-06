package kr.co.ircp.cms.domain.policy.tracking.dto;

import jakarta.validation.constraints.NotBlank;

/** POST /api/v1/policy/programs/{id}/track 요청. */
public record TrackEventRequest(
        @NotBlank String source,
        @NotBlank String action,
        Long notificationSendId,
        String userAgent,
        String ipAddress
) {}
