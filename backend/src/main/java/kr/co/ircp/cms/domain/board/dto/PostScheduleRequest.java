package kr.co.ircp.cms.domain.board.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/**
 * 게시글 예약 발행 요청 DTO.
 * SPEC-CMS-POST-SCHEDULE-001 REQ-POST-SCHEDULE-002: scheduledAt > now 검증.
 * Page 도메인 PageScheduleRequest 패턴과 동일.
 */
public record PostScheduleRequest(
        @NotNull @Future
        Instant scheduledAt
) {}
