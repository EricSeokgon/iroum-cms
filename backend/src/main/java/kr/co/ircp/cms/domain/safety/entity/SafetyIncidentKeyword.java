package kr.co.ircp.cms.domain.safety.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 사고-키워드 매핑 엔티티.
 * REQ-SAFETY-001-D-3: 자동 키워드 추출 결과 저장
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SafetyIncidentKeyword {
    private Long incidentId;
    private Long keywordId;
    private BigDecimal weight;
    /** join 결과 시 카테고리도 같이 채움 (Mapper resultMap 확장용) */
    private String category;
}
