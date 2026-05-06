package kr.co.ircp.cms.domain.policy.tracking.dto;

/** 정책별 클릭/전환 통계 (POLICY_APPLY_CVR). */
public record ConversionStats(
        Long policyId,
        long viewCount,
        long clickCount,
        long redirectCount,
        long savedCount,
        double conversionRate
) {
    public static ConversionStats compute(Long policyId,
                                          long views, long clicks,
                                          long redirects, long saves) {
        long denominator = views == 0 ? 1 : views;
        double cvr = ((double) clicks / denominator);
        return new ConversionStats(policyId, views, clicks, redirects, saves, cvr);
    }
}
