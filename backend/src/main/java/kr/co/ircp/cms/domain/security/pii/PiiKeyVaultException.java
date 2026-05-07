package kr.co.ircp.cms.domain.security.pii;

/**
 * PII KeyVault 관련 예외.
 *
 * <p>키 조회 실패, 버전 불일치, 인증 실패 등 키 관리 계층의 모든 오류.
 *
 * @MX:SPEC SPEC-CMS-SECURITY-PII-001#REQ-PII-EMAIL-004
 */
public class PiiKeyVaultException extends RuntimeException {

    public PiiKeyVaultException(String message) {
        super(message);
    }

    public PiiKeyVaultException(String message, Throwable cause) {
        super(message, cause);
    }
}
