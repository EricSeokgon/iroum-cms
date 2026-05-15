package kr.co.ircp.cms.domain.safety.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 기업 안전 프로필 엔티티.
 * REQ-SAFETY-002-D-1: 매칭 입력 — 업종/공정/위험요소
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanySafetyProfile {
    private Long id;
    private Long companyId;
    private String industryCode;
    private String subIndustry;
    private Integer employeeCount;
    private String primaryProcess;
    /** JSONB raw text — 예: ["고소작업","유해물질"] */
    private String hazardFactors;
    private BigDecimal riskScore;
    private String riskGrade;     // A~E
    private Instant updatedAt;
}
