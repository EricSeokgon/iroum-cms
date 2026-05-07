package kr.co.ircp.cms.domain.search.exception;

/**
 * 지원되지 않는 검색 도메인 파라미터 예외.
 *
 * <p>SPEC-CMS-010 REQ-SEARCH-004: domain 화이트리스트(ALL/board/content/policy/safety/media/publication) 외 → 400.
 */
public class SearchDomainInvalidException extends RuntimeException {

    private final String domain;

    public SearchDomainInvalidException(String domain) {
        super("지원되지 않는 검색 도메인: " + domain);
        this.domain = domain;
    }

    public String getDomain() {
        return domain;
    }
}
