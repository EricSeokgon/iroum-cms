package kr.co.ircp.cms.domain.policy.matching.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** 단건 매칭 결과 (정책 + 점수 + 등급 + 사유). */
public record MatchedPolicy(
        Long policyId,
        String programName,
        String ministry,
        Instant applicationEnd,
        BigDecimal score,
        String grade,
        /** JSONB raw text: {industry:30, region:0, ...} */
        String scoreBreakdown,
        Instant matchedAt
) {}
