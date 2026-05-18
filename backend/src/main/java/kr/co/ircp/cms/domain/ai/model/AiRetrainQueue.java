package kr.co.ircp.cms.domain.ai.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * AI 재학습 큐 엔티티.
 *
 * <p>SPEC-CMS-AI-001 — 드리프트 자동 트리거 또는 수동 요청으로 큐잉되는 재학습 작업.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiRetrainQueue {
    private Long id;
    private String modelName;
    private String triggerReason;   // DRIFT_ACCURACY / DRIFT_ERROR / MANUAL
    private String triggerDetail;   // JSONB (JSON 문자열)
    private String status;          // QUEUED / ACKNOWLEDGED / IN_PROGRESS / DONE / CANCELED
    private Long requestedBy;
    private Instant requestedAt;
    private Instant updatedAt;
}
