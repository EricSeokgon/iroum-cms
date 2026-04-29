package kr.co.ircp.cms.config;

import kr.co.ircp.cms.domain.auth.exception.AccessOutOfScopeException;
import kr.co.ircp.cms.domain.auth.exception.AccountLockedException;
import kr.co.ircp.cms.domain.auth.exception.CyclicReferenceException;
import kr.co.ircp.cms.domain.auth.exception.DepthExceededException;
import kr.co.ircp.cms.domain.auth.exception.DuplicateOrganizationCodeException;
import kr.co.ircp.cms.domain.auth.exception.DuplicateUserException;
import kr.co.ircp.cms.domain.auth.exception.InvalidCredentialsException;
import kr.co.ircp.cms.domain.auth.exception.OrganizationHasChildrenException;
import kr.co.ircp.cms.domain.auth.exception.OrganizationHasUsersException;
import kr.co.ircp.cms.domain.auth.exception.OrganizationNotFoundException;
import kr.co.ircp.cms.domain.auth.exception.PasswordPolicyViolationException;
import kr.co.ircp.cms.domain.auth.exception.PasswordReuseException;
import kr.co.ircp.cms.domain.auth.exception.RoleHasUsersException;
import kr.co.ircp.cms.domain.auth.exception.SystemRoleProtectedException;
import kr.co.ircp.cms.domain.auth.exception.TokenExpiredException;
import kr.co.ircp.cms.domain.auth.exception.TokenReuseException;
import kr.co.ircp.cms.domain.auth.exception.UserNotFoundException;
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

    /**
     * 사용자 미존재 → HTTP 404 Not Found.
     *
     * <p>REQ-AUTH-006 — id에 해당하는 사용자가 없거나 소프트 삭제된 경우.
     */
    @ExceptionHandler(UserNotFoundException.class)
    public ProblemDetail handleUserNotFound(UserNotFoundException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, ex.getMessage());
        detail.setTitle("User Not Found");
        detail.setProperty("code", "USER_NOT_FOUND");
        return detail;
    }

    /**
     * 중복 사용자 → HTTP 409 Conflict.
     *
     * <p>REQ-AUTH-006 — username 또는 email이 이미 존재하는 경우.
     */
    @ExceptionHandler(DuplicateUserException.class)
    public ProblemDetail handleDuplicateUser(DuplicateUserException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, ex.getMessage());
        detail.setTitle("Duplicate User");
        detail.setProperty("code", "USER_DUPLICATE");
        return detail;
    }

    /**
     * 비밀번호 재사용 금지 위반 → HTTP 400 Bad Request.
     *
     * <p>REQ-AUTH-010 — 직전 5회 사용한 비밀번호 재사용 시도.
     */
    @ExceptionHandler(PasswordReuseException.class)
    public ProblemDetail handlePasswordReuse(PasswordReuseException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, ex.getMessage());
        detail.setTitle("Password Reuse Prohibited");
        detail.setProperty("code", "PASSWORD_REUSE");
        return detail;
    }

    /**
     * 조직 미존재 → HTTP 404 Not Found.
     *
     * <p>REQ-AUTH-014 — id에 해당하는 조직이 없거나 소프트 삭제된 경우.
     */
    @ExceptionHandler(OrganizationNotFoundException.class)
    public ProblemDetail handleOrganizationNotFound(OrganizationNotFoundException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, ex.getMessage());
        detail.setTitle("Organization Not Found");
        detail.setProperty("code", "ORG_NOT_FOUND");
        return detail;
    }

    /**
     * 조직 코드 중복 → HTTP 409 Conflict.
     *
     * <p>REQ-AUTH-014 — 이미 사용 중인 조직 코드로 생성 시도.
     */
    @ExceptionHandler(DuplicateOrganizationCodeException.class)
    public ProblemDetail handleDuplicateOrgCode(DuplicateOrganizationCodeException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, ex.getMessage());
        detail.setTitle("Duplicate Organization Code");
        detail.setProperty("code", "ORG_CODE_DUPLICATE");
        return detail;
    }

    /**
     * 조직 트리 순환 참조 → HTTP 400 Bad Request.
     *
     * <p>REQ-AUTH-014 — 자신의 자손을 부모로 이동 시도.
     */
    @ExceptionHandler(CyclicReferenceException.class)
    public ProblemDetail handleCyclicReference(CyclicReferenceException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, ex.getMessage());
        detail.setTitle("Cyclic Reference Detected");
        detail.setProperty("code", "ORG_CYCLIC_REFERENCE");
        return detail;
    }

    /**
     * 조직 트리 깊이 초과 → HTTP 400 Bad Request.
     *
     * <p>REQ-AUTH-014 — 최대 깊이 5를 초과하는 조직 생성/이동 시도.
     */
    @ExceptionHandler(DepthExceededException.class)
    public ProblemDetail handleDepthExceeded(DepthExceededException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, ex.getMessage());
        detail.setTitle("Organization Depth Exceeded");
        detail.setProperty("code", "ORG_DEPTH_EXCEEDED");
        return detail;
    }

    /**
     * 자식 조직 존재로 인한 삭제 불가 → HTTP 409 Conflict.
     *
     * <p>REQ-AUTH-014 — 자식 노드를 먼저 이동 또는 삭제해야 함.
     */
    @ExceptionHandler(OrganizationHasChildrenException.class)
    public ProblemDetail handleOrgHasChildren(OrganizationHasChildrenException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, ex.getMessage());
        detail.setTitle("Organization Has Children");
        detail.setProperty("code", "ORG_HAS_CHILDREN");
        return detail;
    }

    /**
     * 소속 사용자 존재로 인한 삭제 불가 → HTTP 409 Conflict.
     *
     * <p>REQ-AUTH-014 — 사용자 조직 이동 후 삭제해야 함.
     */
    @ExceptionHandler(OrganizationHasUsersException.class)
    public ProblemDetail handleOrgHasUsers(OrganizationHasUsersException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, ex.getMessage());
        detail.setTitle("Organization Has Users");
        detail.setProperty("code", "ORG_HAS_USERS");
        return detail;
    }

    /**
     * 시스템 역할 보호 위반 → HTTP 400 Bad Request.
     *
     * <p>REQ-AUTH-013 — is_system=true 역할 수정·삭제 시도.
     */
    @ExceptionHandler(SystemRoleProtectedException.class)
    public ProblemDetail handleSystemRoleProtected(SystemRoleProtectedException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, ex.getMessage());
        detail.setTitle("System Role Protected");
        detail.setProperty("code", "ROLE_SYSTEM_PROTECTED");
        return detail;
    }

    /**
     * 역할에 사용자 존재 → HTTP 409 Conflict.
     *
     * <p>REQ-AUTH-013 — 역할을 사용 중인 사용자가 있어 삭제 불가.
     */
    @ExceptionHandler(RoleHasUsersException.class)
    public ProblemDetail handleRoleHasUsers(RoleHasUsersException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, ex.getMessage());
        detail.setTitle("Role Has Users");
        detail.setProperty("code", "ROLE_HAS_USERS");
        return detail;
    }

    /**
     * 접근 범위 초과 → HTTP 403 Forbidden.
     *
     * <p>Q-24 — DEPT_ADMIN이 자기 부서·자손 외 사용자/조직 접근 시도.
     */
    @ExceptionHandler(AccessOutOfScopeException.class)
    public ProblemDetail handleAccessOutOfScope(AccessOutOfScopeException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN, ex.getMessage());
        detail.setTitle("Access Out Of Scope");
        detail.setProperty("code", "ACCESS_OUT_OF_SCOPE");
        return detail;
    }
}
