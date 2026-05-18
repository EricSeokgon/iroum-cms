package kr.co.ircp.cms.domain.ai.dto;

import kr.co.ircp.cms.domain.ai.model.AiRetrainQueue;

import java.time.Instant;

/**
 * 재학습 큐 상태 응답 DTO.
 *
 * <p>SPEC-CMS-AI-001.
 */
public record RetrainStatusDto(
        Long id,
        String modelName,
        String triggerReason,
        String status,
        Long requestedBy,
        Instant requestedAt,
        Instant updatedAt
) {
    public static RetrainStatusDto from(AiRetrainQueue q) {
        return new RetrainStatusDto(
                q.getId(), q.getModelName(), q.getTriggerReason(),
                q.getStatus(), q.getRequestedBy(),
                q.getRequestedAt(), q.getUpdatedAt());
    }
}
