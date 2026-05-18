package kr.co.ircp.cms.infra.ml.dto;

import java.util.List;
import java.util.Map;

/**
 * 정책 시맨틱 매칭 ML 요청 DTO.
 *
 * <p>SPEC-CMS-AI-002 — {@code POST /ml/v1/policy-match} 계약.
 * {@code companyProfile}은 PII를 포함하지 않는다
 * (ksic_code/employee_count/growth_stage/region_code/annual_revenue 한정).
 */
public record MlPolicyMatchRequest(
        Map<String, Object> companyProfile,
        String queryText,
        List<Long> candidatePolicyIds,
        int topK) {
}
