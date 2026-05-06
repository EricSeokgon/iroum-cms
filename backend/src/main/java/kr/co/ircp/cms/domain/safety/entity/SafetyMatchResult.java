package kr.co.ircp.cms.domain.safety.entity;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 매칭 결과 엔티티 (TTL 캐시).
 * REQ-SAFETY-002-D-5: 1시간 TTL 캐시
 */
@Data
@Builder
public class SafetyMatchResult {
    private Long id;
    private Long companyProfileId;
    private Long incidentId;
    private BigDecimal similarityScore;
    /** JSONB raw text — XAI 매칭 사유 */
    private String matchReason;
    private Instant generatedAt;
    private Instant expiresAt;
}
