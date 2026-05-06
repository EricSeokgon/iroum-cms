package kr.co.ircp.cms.domain.policy.program.entity;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

/**
 * 정책사업 마스터 엔티티.
 * REQ-POLICY-001: 정책 데이터 마스터
 * SPEC-CMS-007 §4.2.1
 */
@Data
@Builder
public class PolicyProgram {
    private Long id;
    private String code;
    private String ministry;
    private String programName;
    /** JSONB raw text — {ko, en} */
    private String programNameI18n;
    private String descriptionHtml;
    private List<String> targetIndustries;
    private List<String> targetRegions;
    private Integer minEmployees;
    private Integer maxEmployees;
    private Long minRevenue;
    private Long maxRevenue;
    private Integer minBusinessAgeMonths;
    private Integer maxBusinessAgeMonths;
    private Instant applicationStart;
    private Instant applicationEnd;
    private Long budgetTotal;
    private Long budgetPerCompany;
    private String sourceUrl;
    private String sourceApiId;
    private Long sourceId;
    private Instant lastSyncedAt;
    /** JSONB raw text — 표준 코드 미매핑 경고 */
    private String importWarnings;
    /** DRAFT / ACTIVE / CLOSED / EXPIRED */
    private String status;
    private Instant createdAt;
    private Instant updatedAt;
}
