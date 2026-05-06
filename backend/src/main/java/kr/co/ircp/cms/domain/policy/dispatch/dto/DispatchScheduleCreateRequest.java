package kr.co.ircp.cms.domain.policy.dispatch.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;

/** POST /api/v1/policy/admin/dispatch/schedules 요청. */
public record DispatchScheduleCreateRequest(
        Long policyId,
        @NotBlank String dispatchType,
        String targetFilter,
        @NotNull Instant scheduledAt,
        @NotNull List<String> channels,
        @NotNull Long templateId,
        Integer priority,
        @NotNull Long createdBy
) {}
