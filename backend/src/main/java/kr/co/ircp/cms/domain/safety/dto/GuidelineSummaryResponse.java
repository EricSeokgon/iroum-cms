package kr.co.ircp.cms.domain.safety.dto;

import kr.co.ircp.cms.domain.safety.entity.SafetyGuidelineTemplate;

import java.util.List;

/** 공개 가이드라인 목록 응답. REQ-PUBLIC-SAFETY-001 */
public record GuidelineSummaryResponse(
        Long id,
        String title,
        String industryCode,
        String processCode,
        String updatedAt
) {
    public static GuidelineSummaryResponse from(SafetyGuidelineTemplate t) {
        List<String> codes = t.getApplicableIndustryCodes();
        String industry = (codes != null && !codes.isEmpty()) ? codes.get(0) : "";
        String updated = t.getCreatedAt() != null ? t.getCreatedAt().toString() : "";
        return new GuidelineSummaryResponse(t.getId(), t.getName(), industry, t.getCode(), updated);
    }
}
