package kr.co.ircp.cms.domain.content.page.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/**
 * 페이지 예약 발행 요청 DTO.
 * REQ-CONTENT-005-D-4: scheduled_at > now 검증
 */
public record PageScheduleRequest(
        @NotNull @Future
        Instant scheduledAt
) {}
