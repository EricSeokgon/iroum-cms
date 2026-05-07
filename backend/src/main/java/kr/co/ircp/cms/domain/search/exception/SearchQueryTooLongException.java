package kr.co.ircp.cms.domain.search.exception;

/**
 * 검색 쿼리 길이 초과 예외.
 *
 * <p>SPEC-CMS-010 REQ-SEARCH-001: 200자(자동완성 50자) 초과 시 400 SEARCH_QUERY_TOO_LONG.
 */
public class SearchQueryTooLongException extends RuntimeException {

    private final int actual;
    private final int max;

    public SearchQueryTooLongException(int actual, int max) {
        super("검색 쿼리 길이 초과: actual=" + actual + ", max=" + max);
        this.actual = actual;
        this.max = max;
    }

    public int getActual() {
        return actual;
    }

    public int getMax() {
        return max;
    }
}
