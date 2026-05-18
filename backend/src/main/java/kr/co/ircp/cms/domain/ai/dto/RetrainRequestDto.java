package kr.co.ircp.cms.domain.ai.dto;

/**
 * 수동 재학습 요청 DTO.
 *
 * <p>SPEC-CMS-AI-001 — 운영자가 명시적으로 재학습을 큐잉(trigger_reason=MANUAL).
 */
public record RetrainRequestDto(
        String modelName,
        String triggerDetail
) {
}
