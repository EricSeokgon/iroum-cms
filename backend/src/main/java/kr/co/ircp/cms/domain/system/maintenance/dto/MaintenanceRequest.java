package kr.co.ircp.cms.domain.system.maintenance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/**
 * 점검 등록/수정 요청 DTO.
 *
 * <p>REQ-SYSTEM-005-D
 */
public record MaintenanceRequest(
        @NotBlank
        String title,

        String messageKo,
        String messageEn,

        @NotNull
        Instant startAt,

        @NotNull
        Instant endAt,

        Boolean allowAdminAccess
) {}
