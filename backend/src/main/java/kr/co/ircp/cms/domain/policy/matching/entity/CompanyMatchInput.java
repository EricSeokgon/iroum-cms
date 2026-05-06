package kr.co.ircp.cms.domain.policy.matching.entity;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

/**
 * 기업 프로필 — 매칭 알고리즘 입력.
 * REQ-POLICY-002 / REQ-POLICY-003
 * SPEC-CMS-007 §4.2.4
 */
@Data
@Builder
public class CompanyMatchInput {
    private Long id;
    private Long companyId;
    private List<String> industryCodes;
    private List<String> regionCodes;
    private Integer employeeCount;
    private Long annualRevenue;
    private Integer businessAgeMonths;
    private List<String> certifications;
    /** JSONB raw text — {keywords: [...]} */
    private String customAttrs;
    private Instant lastUpdatedAt;
}
