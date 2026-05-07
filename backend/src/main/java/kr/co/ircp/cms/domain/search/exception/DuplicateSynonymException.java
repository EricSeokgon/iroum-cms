package kr.co.ircp.cms.domain.search.exception;

/**
 * 동의어 중복 등록 예외.
 *
 * <p>SPEC-CMS-010 REQ-SEARCH-009: UNIQUE(term, synonym, locale) 위반 시 409 SEARCH_SYNONYM_DUPLICATE.
 */
public class DuplicateSynonymException extends RuntimeException {

    private final String term;
    private final String synonym;
    private final String locale;

    public DuplicateSynonymException(String term, String synonym, String locale) {
        super("동의어 중복: term=" + term + ", synonym=" + synonym + ", locale=" + locale);
        this.term = term;
        this.synonym = synonym;
        this.locale = locale;
    }

    public String getTerm() {
        return term;
    }

    public String getSynonym() {
        return synonym;
    }

    public String getLocale() {
        return locale;
    }
}
