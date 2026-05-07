package kr.co.ircp.cms.domain.search.exception;

/**
 * 검색 로그 미존재 예외.
 *
 * <p>SPEC-CMS-010 REQ-SEARCH-008: 클릭 추적 시 searchLogId 미존재 → 404.
 */
public class SearchLogNotFoundException extends RuntimeException {

    private final Long id;

    public SearchLogNotFoundException(Long id) {
        super("검색 로그를 찾을 수 없습니다: id=" + id);
        this.id = id;
    }

    public Long getId() {
        return id;
    }
}
