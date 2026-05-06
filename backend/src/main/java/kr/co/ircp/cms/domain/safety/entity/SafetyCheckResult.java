package kr.co.ircp.cms.domain.safety.entity;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

/**
 * 체크 결과 엔티티 (보고서별 체크리스트 진행).
 * REQ-SAFETY-004-D-2: status / evidence 추적
 */
@Data
@Builder
public class SafetyCheckResult {
    private Long id;
    private Long reportId;
    private Long itemId;
    private Long checkedBy;
    private String status;       // DONE / IN_PROGRESS / NA / BLOCKED
    private String evidenceText;
    private UUID evidenceAttachmentUuid;
    private Instant checkedAt;
}
