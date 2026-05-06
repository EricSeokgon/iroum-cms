package kr.co.ircp.cms.domain.safety.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 단건 매칭 결과 (사고사례 + 점수 + 사유).
 * REQ-SAFETY-002-D-4: match_reason XAI
 */
public record MatchedIncident(
        Long incidentId,
        String industryCode,
        String incidentType,
        String severity,
        Instant occurredAt,
        String summary,
        BigDecimal similarityScore,
        /** JSON: contributions / explain_ko (XAI). */
        String matchReason
) {}
