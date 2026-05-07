package kr.co.ircp.cms.domain.search.exception;

/**
 * 동의어 자기참조(term=synonym) 예외.
 *
 * <p>SPEC-CMS-010 REQ-SEARCH-009: chk_ss_self CHECK 제약 위반 시 400 SEARCH_SYNONYM_SELF.
 */
public class SynonymSelfException extends RuntimeException {

    public SynonymSelfException() {
        super("동의어는 term과 synonym이 동일할 수 없습니다");
    }
}
