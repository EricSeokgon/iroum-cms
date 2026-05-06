package kr.co.ircp.cms.domain.safety.dto;

/**
 * 매칭 실행 요청.
 * REQ-SAFETY-002-D-3: TOP N (1~20, 기본 5)
 */
public record MatchRequest(Integer topN) {
    public int topNOrDefault() {
        if (topN == null) return 5;
        if (topN < 1) return 1;
        if (topN > 20) return 20;
        return topN;
    }
}
