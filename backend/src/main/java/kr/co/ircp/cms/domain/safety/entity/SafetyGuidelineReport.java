package kr.co.ircp.cms.domain.safety.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

/**
 * 가이드라인 보고서 엔티티.
 * REQ-SAFETY-003-D: 자동 생성된 보고서
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SafetyGuidelineReport {
    private Long id;
    private UUID uuid;
    private Long companyProfileId;
    private Long templateId;
    private String riskGrade;
    /** JSONB raw text — 생성 시점 매칭 결과 스냅샷 */
    private String matchedIncidentsJsonb;
    private String contentHtml;
    private String contentPdfPath;
    private Instant generatedAt;
    private int accessedCount;
}
