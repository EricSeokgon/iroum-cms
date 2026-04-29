package kr.co.ircp.cms.domain.auth.exception;

/**
 * OTP 코드 불일치 예외 (HTTP 401).
 *
 * <p>REQ-AUTH-017-D-2 — 입력한 OTP가 저장된 해시와 일치하지 않을 때 발생.
 * 시도 횟수는 별도로 증가시킨 후 이 예외를 던진다.
 */
public class VerificationCodeMismatchException extends AuthException {

    public VerificationCodeMismatchException() {
        super("VERIFICATION_CODE_MISMATCH");
    }
}
