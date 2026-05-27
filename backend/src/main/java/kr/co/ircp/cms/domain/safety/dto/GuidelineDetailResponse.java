package kr.co.ircp.cms.domain.safety.dto;

import kr.co.ircp.cms.domain.safety.entity.SafetyChecklistItem;
import kr.co.ircp.cms.domain.safety.entity.SafetyGuidelineTemplate;

import java.util.List;

/** 공개 가이드라인 상세 응답. REQ-PUBLIC-SAFETY-001 */
public record GuidelineDetailResponse(
        Long id,
        String title,
        String industryCode,
        String processCode,
        String updatedAt,
        String descriptionHtml,
        List<ChecklistEntry> checklist,
        List<Long> relatedIncidentIds
) {
    public record ChecklistEntry(Long id, String text, int order) {}

    public static GuidelineDetailResponse from(SafetyGuidelineTemplate t, List<SafetyChecklistItem> items) {
        List<String> codes = t.getApplicableIndustryCodes();
        String industry = (codes != null && !codes.isEmpty()) ? codes.get(0) : "";
        String updated = t.getCreatedAt() != null ? t.getCreatedAt().toString() : "";
        List<ChecklistEntry> entries = items.stream()
                .map(i -> new ChecklistEntry(i.getId(), i.getItemText(), i.getSortOrder()))
                .toList();
        return new GuidelineDetailResponse(
                t.getId(), t.getName(), industry, t.getCode(), updated,
                t.getDescription(), entries, List.of());
    }
}
