package kr.co.ircp.cms.domain.policy.matching.dto;

import java.util.List;

/** 정책 매칭 응답 (TOP N). */
public record PolicyMatchResponse(
        Long companyId,
        int topN,
        boolean fromCache,
        List<MatchedPolicy> results
) {}
