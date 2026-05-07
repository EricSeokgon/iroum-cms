package kr.co.ircp.cms.domain.search.exception;

/**
 * 검색 클릭 추적 30분 윈도우 초과 예외.
 *
 * <p>SPEC-CMS-010 REQ-SEARCH-008: searchLogId 30분 이상 경과 시 410 SEARCH_CLICK_WINDOW_EXPIRED.
 */
public class SearchClickWindowExpiredException extends RuntimeException {

    private final Long searchLogId;

    public SearchClickWindowExpiredException(Long searchLogId) {
        super("검색 클릭 추적 윈도우 만료: searchLogId=" + searchLogId);
        this.searchLogId = searchLogId;
    }

    public Long getSearchLogId() {
        return searchLogId;
    }
}
