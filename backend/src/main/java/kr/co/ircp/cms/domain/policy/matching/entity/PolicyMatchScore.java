package kr.co.ircp.cms.domain.policy.matching.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 기업-정책 매칭 결과 (TTL 캐시: matched_at + 7d).
 * REQ-POLICY-003-D-4 / D-5
 * SPEC-CMS-007 §4.2.5
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PolicyMatchScore {
    private Long id;
    private Long companyId;
    private Long policyId;
    private BigDecimal score;
    /** A / B / C / D */
    private String grade;
    /** JSONB raw text — {industry:30, region:0, ...} */
    private String scoreBreakdown;
    private Instant matchedAt;
    private Instant expiresAt;
    private Instant viewedAt;
    private Instant appliedAt;
}
