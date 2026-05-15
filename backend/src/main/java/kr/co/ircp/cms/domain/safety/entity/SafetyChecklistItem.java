package kr.co.ircp.cms.domain.safety.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Data;

/**
 * 체크리스트 항목 엔티티.
 * REQ-SAFETY-004-D: 템플릿 종속 체크리스트
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SafetyChecklistItem {
    private Long id;
    private Long templateId;
    private String category;
    private String itemText;
    private String severity;     // CRITICAL / HIGH / NORMAL / LOW
    private int sortOrder;
    private String status;
}
