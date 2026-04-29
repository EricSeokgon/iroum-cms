package kr.co.ircp.cms.domain.auth.exception;

/**
 * 조직 코드 중복 시 발생하는 예외.
 *
 * <p>SPEC-CMS-002 REQ-AUTH-014 — HTTP 409 Conflict 매핑.
 */
public class DuplicateOrganizationCodeException extends RuntimeException {

    public DuplicateOrganizationCodeException(String code) {
        super("이미 사용 중인 조직 코드입니다: " + code);
    }
}
