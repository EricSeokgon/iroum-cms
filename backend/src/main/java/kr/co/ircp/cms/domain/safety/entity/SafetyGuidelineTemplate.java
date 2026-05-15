package kr.co.ircp.cms.domain.safety.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Data;

import java.time.Instant;
import java.util.List;

/**
 * 가이드라인 템플릿 엔티티.
 * REQ-SAFETY-005-D: 템플릿 CRUD + 버전 관리
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SafetyGuidelineTemplate {
    private Long id;
    private String code;
    private String name;
    private String description;
    private List<String> applicableIndustryCodes;
    private List<String> applicableGrades;
    /** JSONB raw text — 섹션 정의 */
    private String structure;
    private String status;        // DRAFT / PUBLISHED / ARCHIVED
    private String version;       // v1.0 / v1.1 / v2.0
    private String reviewStatus;  // LEGAL_REVIEWED / SAFETY_REVIEWED / NONE
    private Long reviewedBy;
    private Instant reviewedAt;
    private Long createdBy;
    private Instant createdAt;
}
