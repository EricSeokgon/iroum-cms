package kr.co.ircp.cms.domain.search.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 동의어 사전 엔티티.
 *
 * <p>SPEC-CMS-010 REQ-SEARCH-009: 운영자가 등록한 동의어로 OR 쿼리 확장.
 * soft delete 정책(status=PAUSED). UNIQUE(term, synonym, locale) 제약.
 */
// @MX:NOTE: [AUTO] SPEC-CMS-010 동의어 사전 엔티티 (REQ-SEARCH-009)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchSynonym {

    private Long id;
    /** 검색어 (사용자 입력) */
    private String term;
    /** 확장 동의어 (OR 매칭) */
    private String synonym;
    /** ko | en */
    private String locale;
    /** ACTIVE | PAUSED */
    private String status;
    /** 등록 사유 */
    private String description;
    private Long createdBy;
    private Instant createdAt;
    private Long updatedBy;
    private Instant updatedAt;
}
