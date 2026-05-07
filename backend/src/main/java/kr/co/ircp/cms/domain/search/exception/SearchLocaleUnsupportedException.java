package kr.co.ircp.cms.domain.search.exception;

/**
 * 지원되지 않는 검색 locale 예외.
 *
 * <p>SPEC-CMS-010 REQ-SEARCH-010 다국어 정책: ko/en 외 → 400 SEARCH_LOCALE_UNSUPPORTED.
 */
public class SearchLocaleUnsupportedException extends RuntimeException {

    private final String locale;

    public SearchLocaleUnsupportedException(String locale) {
        super("지원되지 않는 locale: " + locale);
        this.locale = locale;
    }

    public String getLocale() {
        return locale;
    }
}
