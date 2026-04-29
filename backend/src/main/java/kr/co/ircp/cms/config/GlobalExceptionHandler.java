package kr.co.ircp.cms.config;

import kr.co.ircp.cms.domain.auth.exception.AccountLockedException;
import kr.co.ircp.cms.domain.auth.exception.InvalidCredentialsException;
import kr.co.ircp.cms.domain.auth.exception.PasswordPolicyViolationException;
import kr.co.ircp.cms.domain.auth.exception.TokenExpiredException;
import kr.co.ircp.cms.domain.auth.exception.TokenReuseException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 전역 예외 핸들러.
 *
 * <p>SPEC-CMS-002 REQ-AUTH-001~005 — 인증·인가 예외를 표준 HTTP 상태 코드로 매핑.
 * RFC 9457 ProblemDetail 형식 사용.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 잘못된 자격증명 → HTTP 401 Unauthorized.
     *
     * <p>REQ-AUTH-001 — 사용자 미존재 또는 비밀번호 불일치 (Enumeration 방지 단일 응답).
     */
    @ExceptionHandler(InvalidCredentialsException.class)
    public ProblemDetail handleInvalidCredentials(InvalidCredentialsException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED, ex.getMessage());
        detail.setTitle("Authentication Failed");
        detail.setProperty("code", "AUTH_INVALID_CREDENTIALS");
        return detail;
    }

    /**
     * 계정 잠금 → HTTP 423 Locked.
     *
     * <p>REQ-AUTH-005 — 5회 실패 시 30분 잠금.
     */
    @ExceptionHandler(AccountLockedException.class)
    public ProblemDetail handleAccountLocked(AccountLockedException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.LOCKED, ex.getMessage());
        detail.setTitle("Account Locked");
        detail.setProperty("code", "AUTH_ACCOUNT_LOCKED");
        return detail;
    }

    /**
     * 토큰 만료 → HTTP 401 Unauthorized.
     *
     * <p>REQ-AUTH-002 — 만료된 Access / Refresh Token 사용 시도.
     */
    @ExceptionHandler(TokenExpiredException.class)
    public ProblemDetail handleTokenExpired(TokenExpiredException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED, ex.getMessage());
        detail.setTitle("Token Expired");
        detail.setProperty("code", "TOKEN_EXPIRED");
        return detail;
    }

    /**
     * 토큰 재사용 탐지 → HTTP 401 Unauthorized.
     *
     * <p>REQ-AUTH-002 — 탈취 감지 시 해당 사용자 전체 세션 강제 종료.
     */
    @ExceptionHandler(TokenReuseException.class)
    public ProblemDetail handleTokenReuse(TokenReuseException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED, ex.getMessage());
        detail.setTitle("Token Reuse Detected");
        detail.setProperty("code", "TOKEN_REUSE_DETECTED");
        return detail;
    }

    /**
     * 비밀번호 정책 위반 → HTTP 400 Bad Request.
     *
     * <p>REQ-AUTH-004 — 비밀번호 강도 정책 위반.
     */
    @ExceptionHandler(PasswordPolicyViolationException.class)
    public ProblemDetail handlePasswordPolicy(PasswordPolicyViolationException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, ex.getMessage());
        detail.setTitle("Password Policy Violation");
        detail.setProperty("code", "PASSWORD_POLICY");
        return detail;
    }
}
