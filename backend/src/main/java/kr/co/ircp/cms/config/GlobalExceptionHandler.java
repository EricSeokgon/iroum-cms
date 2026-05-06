package kr.co.ircp.cms.config;

import kr.co.ircp.cms.domain.auth.exception.AccessOutOfScopeException;
import kr.co.ircp.cms.domain.auth.exception.InvalidVerifiedTokenException;
import kr.co.ircp.cms.domain.auth.exception.VerificationAttemptExceededException;
import kr.co.ircp.cms.domain.auth.exception.VerificationCodeMismatchException;
import kr.co.ircp.cms.domain.auth.exception.VerificationCooldownException;
import kr.co.ircp.cms.domain.auth.exception.VerificationExpiredException;
import kr.co.ircp.cms.domain.auth.exception.VerificationIpBlockedException;
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
import kr.co.ircp.cms.domain.board.exception.AttachmentDownloadDeniedException;
import kr.co.ircp.cms.domain.board.exception.AttachmentNotFoundException;
import kr.co.ircp.cms.domain.board.exception.AttachmentTooLargeException;
import kr.co.ircp.cms.domain.board.exception.BbsMasterNotFoundException;
import kr.co.ircp.cms.domain.board.exception.BoardAttachmentDisabledException;
import kr.co.ircp.cms.domain.board.exception.BoardCommentDisabledException;
import kr.co.ircp.cms.domain.board.exception.CommentNotFoundException;
import kr.co.ircp.cms.domain.board.exception.DuplicateBbsCodeException;
import kr.co.ircp.cms.domain.board.exception.InvalidAttachmentTypeException;
import kr.co.ircp.cms.domain.board.exception.PostNotFoundException;
import kr.co.ircp.cms.domain.dashboard.exception.DashboardLayoutNotFoundException;
import kr.co.ircp.cms.domain.dashboard.exception.DashboardWidgetNotFoundException;
import kr.co.ircp.cms.domain.dashboard.exception.ExportAccessDeniedException;
import kr.co.ircp.cms.domain.dashboard.exception.ExportExpiredException;
import kr.co.ircp.cms.domain.dashboard.exception.ExportNotFoundException;
import kr.co.ircp.cms.domain.dashboard.exception.InvalidWidgetQueryException;
import kr.co.ircp.cms.domain.dashboard.exception.SavedViewNotFoundException;
import kr.co.ircp.cms.domain.dashboard.exception.WidgetAccessDeniedException;
import kr.co.ircp.cms.domain.policy.dispatch.exception.DispatchScheduleConflictException;
import kr.co.ircp.cms.domain.policy.dispatch.exception.DispatchScheduleNotFoundException;
import kr.co.ircp.cms.domain.policy.matching.exception.CompanyMatchInputNotFoundException;
import kr.co.ircp.cms.domain.policy.program.exception.PolicyProgramNotFoundException;
import kr.co.ircp.cms.domain.safety.exception.SafetyChecklistItemNotFoundException;
import kr.co.ircp.cms.domain.safety.exception.SafetyIncidentNotFoundException;
import kr.co.ircp.cms.domain.safety.exception.SafetyKeywordNotFoundException;
import kr.co.ircp.cms.domain.safety.exception.SafetyProfileNotFoundException;
import kr.co.ircp.cms.domain.safety.exception.SafetyReportNotFoundException;
import kr.co.ircp.cms.domain.safety.exception.SafetyTemplateNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
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

    // ─── REQ-AUTH-017 본인인증 예외 핸들러 ────────────────────────────────────

    /**
     * OTP 쿨다운 → HTTP 429 Too Many Requests + Retry-After 헤더.
     *
     * <p>REQ-AUTH-017-D-1 — 동일 대상에 1분 이내 재요청 시.
     */
    @ExceptionHandler(VerificationCooldownException.class)
    public ResponseEntity<ProblemDetail> handleVerificationCooldown(VerificationCooldownException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.TOO_MANY_REQUESTS, "인증 요청 쿨다운 중입니다. 잠시 후 재시도해 주세요.");
        detail.setTitle("Verification Cooldown");
        detail.setProperty("code", "VERIFICATION_COOLDOWN");
        detail.setProperty("retryAfterSeconds", ex.getRetryAfterSeconds());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(ex.getRetryAfterSeconds()))
                .body(detail);
    }

    /**
     * IP 차단 → HTTP 423 Locked.
     *
     * <p>REQ-AUTH-017-D-5 — 시간당 10회 초과 요청.
     */
    @ExceptionHandler(VerificationIpBlockedException.class)
    public ProblemDetail handleVerificationIpBlocked(VerificationIpBlockedException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.LOCKED, "IP가 일시 차단되었습니다. 1시간 후 재시도해 주세요.");
        detail.setTitle("IP Blocked");
        detail.setProperty("code", "VERIFICATION_IP_BLOCKED");
        return detail;
    }

    /**
     * 인증 요청 만료 / 이미 사용된 requestId → HTTP 403 Forbidden.
     *
     * <p>REQ-AUTH-017-D-2 — expires_at 초과 또는 PENDING 외 상태.
     */
    @ExceptionHandler(VerificationExpiredException.class)
    public ProblemDetail handleVerificationExpired(VerificationExpiredException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN, "인증 요청이 만료되었거나 이미 사용되었습니다.");
        detail.setTitle("Verification Expired");
        detail.setProperty("code", "VERIFICATION_EXPIRED");
        return detail;
    }

    /**
     * 시도 횟수 초과 → HTTP 403 Forbidden.
     *
     * <p>REQ-AUTH-017-D-2 — 3회 초과.
     */
    @ExceptionHandler(VerificationAttemptExceededException.class)
    public ProblemDetail handleVerificationAttemptExceeded(VerificationAttemptExceededException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN, "인증 시도 횟수를 초과했습니다. 새로운 인증 코드를 요청해 주세요.");
        detail.setTitle("Verification Attempt Exceeded");
        detail.setProperty("code", "VERIFICATION_ATTEMPT_EXCEEDED");
        return detail;
    }

    /**
     * OTP 코드 불일치 → HTTP 401 Unauthorized.
     *
     * <p>REQ-AUTH-017-D-2 — 입력한 OTP가 저장된 코드와 다를 때.
     */
    @ExceptionHandler(VerificationCodeMismatchException.class)
    public ProblemDetail handleVerificationCodeMismatch(VerificationCodeMismatchException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED, "인증 코드가 일치하지 않습니다.");
        detail.setTitle("Verification Code Mismatch");
        detail.setProperty("code", "VERIFICATION_CODE_MISMATCH");
        return detail;
    }

    /**
     * 유효하지 않은 verifiedToken → HTTP 401 Unauthorized.
     *
     * <p>REQ-AUTH-017-D-4 — 토큰 무효, 만료, 또는 목적 불일치.
     */
    @ExceptionHandler(InvalidVerifiedTokenException.class)
    public ProblemDetail handleInvalidVerifiedToken(InvalidVerifiedTokenException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED, "유효하지 않거나 만료된 인증 토큰입니다. 다시 인증해 주세요.");
        detail.setTitle("Invalid Verified Token");
        detail.setProperty("code", "VERIFICATION_TOKEN_INVALID");
        return detail;
    }

    // ─── REQ-BOARD-001~005 게시판 예외 핸들러 ──────────────────────────────────

    /**
     * 게시판 마스터 미존재 → HTTP 404 Not Found.
     *
     * <p>REQ-BOARD-001 — id 또는 code에 해당하는 게시판이 없는 경우.
     */
    @ExceptionHandler(BbsMasterNotFoundException.class)
    public ProblemDetail handleBbsMasterNotFound(BbsMasterNotFoundException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, ex.getMessage());
        detail.setTitle("Board Not Found");
        detail.setProperty("code", "BOARD_NOT_FOUND");
        return detail;
    }

    /**
     * 게시판 코드 중복 → HTTP 409 Conflict.
     *
     * <p>REQ-BOARD-001 — 이미 사용 중인 게시판 코드로 생성 시도.
     */
    @ExceptionHandler(DuplicateBbsCodeException.class)
    public ProblemDetail handleDuplicateBbsCode(DuplicateBbsCodeException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, ex.getMessage());
        detail.setTitle("Duplicate Board Code");
        detail.setProperty("code", "BOARD_CODE_DUPLICATE");
        return detail;
    }

    /**
     * 게시글 미존재 → HTTP 404 Not Found.
     *
     * <p>REQ-BOARD-002 — id에 해당하는 게시글이 없거나 소프트 삭제된 경우.
     */
    @ExceptionHandler(PostNotFoundException.class)
    public ProblemDetail handlePostNotFound(PostNotFoundException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, ex.getMessage());
        detail.setTitle("Post Not Found");
        detail.setProperty("code", "POST_NOT_FOUND");
        return detail;
    }

    /**
     * 댓글 미존재 → HTTP 404 Not Found.
     *
     * <p>REQ-BOARD-003 — id에 해당하는 댓글이 없거나 소프트 삭제된 경우.
     */
    @ExceptionHandler(CommentNotFoundException.class)
    public ProblemDetail handleCommentNotFound(CommentNotFoundException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, ex.getMessage());
        detail.setTitle("Comment Not Found");
        detail.setProperty("code", "COMMENT_NOT_FOUND");
        return detail;
    }

    /**
     * 첨부파일 미존재 → HTTP 404 Not Found.
     *
     * <p>REQ-BOARD-004 — id에 해당하는 첨부파일이 없거나 소프트 삭제된 경우.
     */
    @ExceptionHandler(AttachmentNotFoundException.class)
    public ProblemDetail handleAttachmentNotFound(AttachmentNotFoundException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, ex.getMessage());
        detail.setTitle("Attachment Not Found");
        detail.setProperty("code", "ATTACHMENT_NOT_FOUND");
        return detail;
    }

    /**
     * 첨부파일 크기 초과 → HTTP 413 Content Too Large.
     *
     * <p>REQ-BOARD-004 — 업로드 파일이 게시판 최대 크기를 초과하는 경우.
     */
    @ExceptionHandler(AttachmentTooLargeException.class)
    public ProblemDetail handleAttachmentTooLarge(AttachmentTooLargeException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.PAYLOAD_TOO_LARGE, ex.getMessage());
        detail.setTitle("Attachment Too Large");
        detail.setProperty("code", "ATTACHMENT_TOO_LARGE");
        return detail;
    }

    /**
     * 허용되지 않는 첨부파일 타입 → HTTP 400 Bad Request.
     *
     * <p>REQ-BOARD-004 — 게시판 allowedMimeTypes 외 파일 업로드 시도.
     */
    @ExceptionHandler(InvalidAttachmentTypeException.class)
    public ProblemDetail handleInvalidAttachmentType(InvalidAttachmentTypeException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, ex.getMessage());
        detail.setTitle("Invalid Attachment Type");
        detail.setProperty("code", "ATTACHMENT_TYPE_INVALID");
        return detail;
    }

    /**
     * 첨부파일 다운로드 거부 → HTTP 403 Forbidden.
     *
     * <p>REQ-BOARD-005 — HMAC 서명 불일치, 토큰 만료, 또는 형식 오류.
     */
    @ExceptionHandler(AttachmentDownloadDeniedException.class)
    public ProblemDetail handleAttachmentDownloadDenied(AttachmentDownloadDeniedException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN, ex.getMessage());
        detail.setTitle("Attachment Download Denied");
        detail.setProperty("code", "ATTACHMENT_DOWNLOAD_DENIED");
        return detail;
    }

    /**
     * 댓글 기능 비활성 게시판 → HTTP 400 Bad Request.
     *
     * <p>REQ-BOARD-003 — useComment=false 게시판에 댓글 작성 시도.
     */
    @ExceptionHandler(BoardCommentDisabledException.class)
    public ProblemDetail handleBoardCommentDisabled(BoardCommentDisabledException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, ex.getMessage());
        detail.setTitle("Board Comment Disabled");
        detail.setProperty("code", "BOARD_COMMENT_DISABLED");
        return detail;
    }

    /**
     * 첨부파일 기능 비활성 게시판 → HTTP 400 Bad Request.
     *
     * <p>REQ-BOARD-004 — useAttachment=false 게시판에 첨부파일 업로드 시도.
     */
    @ExceptionHandler(BoardAttachmentDisabledException.class)
    public ProblemDetail handleBoardAttachmentDisabled(BoardAttachmentDisabledException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, ex.getMessage());
        detail.setTitle("Board Attachment Disabled");
        detail.setProperty("code", "BOARD_ATTACHMENT_DISABLED");
        return detail;
    }

    // ─── REQ-SAFETY-001~005 안전경영 가이드라인 + 사고사례 매칭 예외 ──────────

    /** 사고사례 미존재 → 404. REQ-SAFETY-001 */
    @ExceptionHandler(SafetyIncidentNotFoundException.class)
    public ProblemDetail handleSafetyIncidentNotFound(SafetyIncidentNotFoundException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        detail.setTitle("Safety Incident Not Found");
        detail.setProperty("code", "SAFETY_INCIDENT_NOT_FOUND");
        return detail;
    }

    /** 안전 키워드 미존재 → 404. REQ-SAFETY-002 */
    @ExceptionHandler(SafetyKeywordNotFoundException.class)
    public ProblemDetail handleSafetyKeywordNotFound(SafetyKeywordNotFoundException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        detail.setTitle("Safety Keyword Not Found");
        detail.setProperty("code", "SAFETY_KEYWORD_NOT_FOUND");
        return detail;
    }

    /** 기업 안전 프로필 미존재 → 404. REQ-SAFETY-002 */
    @ExceptionHandler(SafetyProfileNotFoundException.class)
    public ProblemDetail handleSafetyProfileNotFound(SafetyProfileNotFoundException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        detail.setTitle("Safety Profile Not Found");
        detail.setProperty("code", "SAFETY_PROFILE_NOT_FOUND");
        return detail;
    }

    /** 가이드라인 템플릿 미존재 → 404. REQ-SAFETY-005 */
    @ExceptionHandler(SafetyTemplateNotFoundException.class)
    public ProblemDetail handleSafetyTemplateNotFound(SafetyTemplateNotFoundException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        detail.setTitle("Safety Template Not Found");
        detail.setProperty("code", "SAFETY_TEMPLATE_NOT_FOUND");
        return detail;
    }

    /** 가이드라인 보고서 미존재 → 404. REQ-SAFETY-003 */
    @ExceptionHandler(SafetyReportNotFoundException.class)
    public ProblemDetail handleSafetyReportNotFound(SafetyReportNotFoundException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        detail.setTitle("Safety Report Not Found");
        detail.setProperty("code", "SAFETY_REPORT_NOT_FOUND");
        return detail;
    }

    /** 체크리스트 항목 미존재 → 404. REQ-SAFETY-004 */
    @ExceptionHandler(SafetyChecklistItemNotFoundException.class)
    public ProblemDetail handleSafetyChecklistItemNotFound(SafetyChecklistItemNotFoundException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        detail.setTitle("Safety Checklist Item Not Found");
        detail.setProperty("code", "SAFETY_CHECKLIST_ITEM_NOT_FOUND");
        return detail;
    }

    // ─── REQ-POLICY-001~005 정책 매칭·발송 예외 (SPEC-CMS-007) ──────────────

    /** 정책사업 미존재 → 404. REQ-POLICY-001 */
    @ExceptionHandler(PolicyProgramNotFoundException.class)
    public ProblemDetail handlePolicyProgramNotFound(PolicyProgramNotFoundException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        detail.setTitle("Policy Program Not Found");
        detail.setProperty("code", "POLICY_PROGRAM_NOT_FOUND");
        return detail;
    }

    /** 기업 프로필(매칭 입력) 미존재 → 404. REQ-POLICY-002 */
    @ExceptionHandler(CompanyMatchInputNotFoundException.class)
    public ProblemDetail handleCompanyMatchInputNotFound(CompanyMatchInputNotFoundException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        detail.setTitle("Company Match Input Not Found");
        detail.setProperty("code", "COMPANY_MATCH_INPUT_NOT_FOUND");
        return detail;
    }

    /** 발송 예약 미존재 → 404. REQ-POLICY-003 */
    @ExceptionHandler(DispatchScheduleNotFoundException.class)
    public ProblemDetail handleDispatchScheduleNotFound(DispatchScheduleNotFoundException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        detail.setTitle("Dispatch Schedule Not Found");
        detail.setProperty("code", "DISPATCH_SCHEDULE_NOT_FOUND");
        return detail;
    }

    /** 발송 예약 상태 충돌 → 409. REQ-POLICY-003 */
    @ExceptionHandler(DispatchScheduleConflictException.class)
    public ProblemDetail handleDispatchScheduleConflict(DispatchScheduleConflictException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        detail.setTitle("Dispatch Schedule Conflict");
        detail.setProperty("code", "DISPATCH_SCHEDULE_CONFLICT");
        return detail;
    }

    // ─── REQ-VIZ-001~006 시각화 대시보드 예외 (SPEC-CMS-008) ───────────────────

    /** 위젯 미존재 → 404. REQ-VIZ-001 */
    @ExceptionHandler(DashboardWidgetNotFoundException.class)
    public ProblemDetail handleDashboardWidgetNotFound(DashboardWidgetNotFoundException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        detail.setTitle("Dashboard Widget Not Found");
        detail.setProperty("code", "DASHBOARD_WIDGET_NOT_FOUND");
        return detail;
    }

    /** 레이아웃 미존재 → 404. REQ-VIZ-002 */
    @ExceptionHandler(DashboardLayoutNotFoundException.class)
    public ProblemDetail handleDashboardLayoutNotFound(DashboardLayoutNotFoundException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        detail.setTitle("Dashboard Layout Not Found");
        detail.setProperty("code", "DASHBOARD_LAYOUT_NOT_FOUND");
        return detail;
    }

    /** 저장된 뷰 미존재 → 404. REQ-VIZ-004 */
    @ExceptionHandler(SavedViewNotFoundException.class)
    public ProblemDetail handleSavedViewNotFound(SavedViewNotFoundException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        detail.setTitle("Saved View Not Found");
        detail.setProperty("code", "SAVED_VIEW_NOT_FOUND");
        return detail;
    }

    /** Export 미존재 → 404. REQ-VIZ-006 */
    @ExceptionHandler(ExportNotFoundException.class)
    public ProblemDetail handleExportNotFound(ExportNotFoundException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        detail.setTitle("Export Not Found");
        detail.setProperty("code", "EXPORT_NOT_FOUND");
        return detail;
    }

    /** Export 만료 → 410 Gone. REQ-VIZ-006-D-5 */
    @ExceptionHandler(ExportExpiredException.class)
    public ProblemDetail handleExportExpired(ExportExpiredException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.GONE, ex.getMessage());
        detail.setTitle("Export Expired");
        detail.setProperty("code", "EXPORT_EXPIRED");
        return detail;
    }

    /** Export 권한 거부 → 403. REQ-VIZ-006-D-5 */
    @ExceptionHandler(ExportAccessDeniedException.class)
    public ProblemDetail handleExportAccessDenied(ExportAccessDeniedException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
        detail.setTitle("Export Access Denied");
        detail.setProperty("code", "EXPORT_ACCESS_DENIED");
        return detail;
    }

    /** 위젯 권한 거부 → 403. REQ-VIZ-001-D-3 */
    @ExceptionHandler(WidgetAccessDeniedException.class)
    public ProblemDetail handleWidgetAccessDenied(WidgetAccessDeniedException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
        detail.setTitle("Widget Access Denied");
        detail.setProperty("code", "WIDGET_ACCESS_DENIED");
        return detail;
    }

    /** CUSTOM_QUERY 위젯 DDL/DML 토큰 거부 → 400. REQ-VIZ-005-D-2 */
    @ExceptionHandler(InvalidWidgetQueryException.class)
    public ProblemDetail handleInvalidWidgetQuery(InvalidWidgetQueryException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        detail.setTitle("Invalid Widget Query");
        detail.setProperty("code", "INVALID_WIDGET_QUERY");
        return detail;
    }
}
