package kr.co.ircp.cms.domain.search.entity;

/**
 * 동의어 사전 status enum.
 *
 * <p>SPEC-CMS-010 REQ-SEARCH-009: ACTIVE만 검색 시 OR 확장에 사용.
 * PAUSED는 soft delete 상태로 확장에서 제외된다.
 */
public enum SearchSynonymStatus {
    ACTIVE,
    PAUSED
}
