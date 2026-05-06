package kr.co.ircp.cms.domain.safety.dto;

import java.time.Instant;
import java.util.UUID;

/** 체크 결과 + 항목 결합 응답. */
public record CheckResultResponse(
        Long itemId,
        String category,
        String itemText,
        String severity,
        String status,            // DONE/IN_PROGRESS/NA/BLOCKED, null=미체크
        String evidenceText,
        UUID evidenceAttachmentUuid,
        Long checkedBy,
        Instant checkedAt
) {}
