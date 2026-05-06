package kr.co.ircp.cms.domain.safety.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

/**
 * 체크 결과 기록·변경 요청.
 * REQ-SAFETY-004-D-2
 */
public record CheckResultRequest(
        @NotBlank String status,           // DONE/IN_PROGRESS/NA/BLOCKED
        String evidenceText,
        UUID evidenceAttachmentUuid
) {}
