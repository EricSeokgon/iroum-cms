package kr.co.ircp.cms.domain.safety.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Data;

/**
 * 키워드 동의어 엔티티.
 * REQ-SAFETY-002-D: 형태소·신조어 대응
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SafetyKeywordSynonym {
    private Long id;
    private Long keywordId;
    private String synonym;
}
