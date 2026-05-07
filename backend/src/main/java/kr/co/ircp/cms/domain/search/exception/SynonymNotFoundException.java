package kr.co.ircp.cms.domain.search.exception;

/**
 * 동의어 미존재 예외.
 *
 * <p>SPEC-CMS-010 REQ-SEARCH-009: id에 해당하는 동의어 미존재 시 404 SEARCH_SYNONYM_NOT_FOUND.
 */
public class SynonymNotFoundException extends RuntimeException {

    private final Long id;

    public SynonymNotFoundException(Long id) {
        super("동의어를 찾을 수 없습니다: id=" + id);
        this.id = id;
    }

    public Long getId() {
        return id;
    }
}
