package kr.co.ircp.cms.domain.policy.dispatch.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 발송 예약 응답 (with night-time 보정 안내). */
public record DispatchScheduleResponse(
        Long id,
        UUID scheduleUuid,
        Long policyId,
        String dispatchType,
        String targetFilter,
        Instant scheduledAt,
        boolean nighttimeAdjusted,
        Instant originalScheduledAt,
        List<String> channels,
        Long templateId,
        Integer priority,
        String status,
        Long createdBy,
        Instant createdAt
) {}
