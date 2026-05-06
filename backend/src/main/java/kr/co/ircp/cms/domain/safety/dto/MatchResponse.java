package kr.co.ircp.cms.domain.safety.dto;

import java.util.List;

/**
 * 매칭 실행 응답.
 * REQ-SAFETY-002-D
 */
public record MatchResponse(
        Long profileId,
        int topN,
        boolean fromCache,
        List<MatchedIncident> results
) {}
