package kr.co.ircp.cms.domain.search.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 검색 로그 엔티티.
 *
 * <p>SPEC-CMS-010 REQ-SEARCH-008: 사용자/세션/쿼리/응답시간/클릭 추적.
 * 시계열 INSERT-ONLY 테이블이며 6개월 보존(retention_policy 시드).
 */
// @MX:NOTE: [AUTO] SPEC-CMS-010 검색 로그 엔티티 (REQ-SEARCH-008)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchLog {

    private Long id;
    /** 비로그인 시 NULL */
    private Long userId;
    /** 비로그인 추적용 세션 ID (쿠키 기반) */
    private String sessionId;
    /** 원본 쿼리 */
    private String query;
    /** 공백제거+소문자 정규화 (인기 검색어 집계 키) */
    private String normalizedQuery;
    /** 동의어 확장 후 ts_query 문자열 (REQ-SEARCH-009) */
    private String expandedQuery;
    private int resultCount;
    private int responseMs;
    /** board/content/policy/safety/media/publication */
    private String clickedDocType;
    private Long clickedDocId;
    private Instant clickedAt;
    /** 클릭된 결과의 순위 (1=최상위) */
    private Integer clickedRank;
    /** ko | en */
    private String locale;
    /** ALL/board/content/policy/safety/media/publication */
    private String domainFilter;
    /** SHA-256 해시 (PII 보호) */
    private String ipHash;
    private Instant createdAt;
}
