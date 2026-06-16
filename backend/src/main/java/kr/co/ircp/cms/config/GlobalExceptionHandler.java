package kr.co.ircp.cms.config;

import jakarta.validation.ConstraintViolationException;
import kr.co.ircp.cms.domain.auth.exception.AccessOutOfScopeException;
import kr.co.ircp.cms.domain.auth.exception.AdminEmailPartialSearchException;
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
import kr.co.ircp.cms.domain.auth.exception.UserPendingApprovalException;
import kr.co.ircp.cms.domain.approval.exception.UserNotPendingApprovalException;
import kr.co.ircp.cms.domain.ai.exception.AiPredictionNotFoundException;
import kr.co.ircp.cms.domain.ai.exception.AiRateLimitExceededException;
import kr.co.ircp.cms.domain.ai.exception.AiSimulationNotFoundException;
import kr.co.ircp.cms.domain.policy.aimatch.exception.AiFeedbackInvalidException;
import kr.co.ircp.cms.domain.board.exception.AttachmentDownloadDeniedException;
import kr.co.ircp.cms.domain.board.exception.AttachmentNotFoundException;
import kr.co.ircp.cms.domain.board.exception.AttachmentTooLargeException;
import kr.co.ircp.cms.domain.board.exception.BbsMasterNotFoundException;
import kr.co.ircp.cms.domain.board.exception.BoardAttachmentDisabledException;
import kr.co.ircp.cms.domain.board.exception.BoardCommentDisabledException;
import kr.co.ircp.cms.domain.board.exception.CommentModerationException;
import kr.co.ircp.cms.domain.board.exception.PublicationCategoryConflictException;
import kr.co.ircp.cms.domain.board.exception.CommentNotFoundException;
import kr.co.ircp.cms.domain.board.exception.DuplicateBbsCodeException;
import kr.co.ircp.cms.domain.board.exception.FaqNotFoundException;
import kr.co.ircp.cms.domain.board.exception.InvalidAttachmentTypeException;
import kr.co.ircp.cms.domain.board.exception.PostHistoryVersionNotFoundException;
import kr.co.ircp.cms.domain.board.exception.PostNotFoundException;
import kr.co.ircp.cms.domain.board.exception.PostScheduleConflictException;
import kr.co.ircp.cms.domain.board.exception.PublicationNotFoundException;
import kr.co.ircp.cms.domain.board.exception.QnaNotFoundException;
import kr.co.ircp.cms.domain.board.exception.SurveyNotFoundException;
import kr.co.ircp.cms.domain.board.exception.SurveyPeriodInvalidException;
import kr.co.ircp.cms.domain.content.banner.exception.BannerAltTextMissingException;
import kr.co.ircp.cms.domain.content.banner.exception.BannerPeriodInvalidException;
import kr.co.ircp.cms.domain.content.menu.exception.MenuCircularReferenceException;
import kr.co.ircp.cms.domain.content.menu.exception.MenuCodeDuplicateException;
import kr.co.ircp.cms.domain.content.menu.exception.MenuDepthExceededException;
import kr.co.ircp.cms.domain.content.page.exception.PageBlockTypeForbiddenException;
import kr.co.ircp.cms.domain.content.page.exception.PageHistoryVersionNotFoundException;
import kr.co.ircp.cms.domain.content.page.exception.PageNotFoundException;
import kr.co.ircp.cms.domain.content.page.exception.PageSlugDuplicateException;
import kr.co.ircp.cms.domain.content.page.exception.PageSlugInvalidException;
import kr.co.ircp.cms.domain.content.page.exception.PageStatusTransitionException;
import kr.co.ircp.cms.domain.content.popup.exception.PopupPeriodInvalidException;
import kr.co.ircp.cms.domain.content.popup.exception.PopupTargetMissingException;
import kr.co.ircp.cms.domain.content.site.exception.SiteMultiDisabledException;
import kr.co.ircp.cms.domain.content.template.exception.TemplateInUseException;
import kr.co.ircp.cms.domain.content.template.exception.TemplateMissingSlotException;
import kr.co.ircp.cms.domain.dashboard.exception.DashboardLayoutNotFoundException;
import kr.co.ircp.cms.domain.dashboard.exception.DashboardWidgetNotFoundException;
import kr.co.ircp.cms.domain.dashboard.exception.ExportAccessDeniedException;
import kr.co.ircp.cms.domain.dashboard.exception.ExportExpiredException;
import kr.co.ircp.cms.domain.dashboard.exception.ExportNotFoundException;
import kr.co.ircp.cms.domain.dashboard.exception.ExportSignatureInvalidException;
import kr.co.ircp.cms.domain.dashboard.exception.ExportSizeLimitException;
import kr.co.ircp.cms.domain.dashboard.exception.InvalidWidgetQueryException;
import kr.co.ircp.cms.domain.dashboard.exception.SavedViewNotFoundException;
import kr.co.ircp.cms.domain.dashboard.exception.WidgetAccessDeniedException;
import kr.co.ircp.cms.domain.dashboard.exception.WidgetDeptMismatchException;
import kr.co.ircp.cms.domain.dashboard.preference.exception.PreferenceConflictException;
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
import kr.co.ircp.cms.domain.search.exception.DuplicateSynonymException;
import kr.co.ircp.cms.domain.search.exception.SearchClickWindowExpiredException;
import kr.co.ircp.cms.domain.search.exception.SearchDomainInvalidException;
import kr.co.ircp.cms.domain.search.exception.SearchLocaleUnsupportedException;
import kr.co.ircp.cms.domain.search.exception.SearchLogNotFoundException;
import kr.co.ircp.cms.domain.search.exception.SearchQueryTooLongException;
import kr.co.ircp.cms.domain.search.exception.SynonymNotFoundException;
import kr.co.ircp.cms.domain.search.exception.SynonymSelfException;
import kr.co.ircp.cms.domain.system.code.exception.CodeDuplicateException;
import kr.co.ircp.cms.domain.system.code.exception.CodeGroupInUseException;
import kr.co.ircp.cms.domain.email.template.admin.exception.DuplicateEmailTemplateException;
import kr.co.ircp.cms.domain.email.template.admin.exception.EmailTemplateNotFoundException;
import kr.co.ircp.cms.domain.email.template.admin.exception.MissingTemplateVariableException;
import kr.co.ircp.cms.domain.email.template.admin.exception.TemplateInactiveException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
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
     * email partial 검색 차단 → HTTP 400 Bad Request.
     *
     * <p>REQ-PII-EMAIL-007 — email 컬럼 완전일치 검색만 허용, partial 패턴 입력 거부.
     */
    // @MX:NOTE: [AUTO] AdminEmailPartialSearchException 핸들러 — 와일드카드/부분 일치 입력을 RFC 9457 ProblemDetail 400으로 표준화
    // @MX:SPEC: SPEC-CMS-SECURITY-PII-002 §5.3 / REQ-PII-EMAIL-007 — 응답 코드는 ADMIN_EMAIL_PARTIAL_FORBIDDEN 고정, ConstraintViolationException 핸들러와 동일 코드 사용
    @ExceptionHandler(AdminEmailPartialSearchException.class)
    public ProblemDetail handleAdminEmailPartialSearch(AdminEmailPartialSearchException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, ex.getMessage());
        detail.setTitle("Email Partial Search Forbidden");
        detail.setProperty("code", AdminEmailPartialSearchException.CODE);
        return detail;
    }

    /**
     * Bean Validation @NoEmailWildcard 위반 → HTTP 400 Bad Request.
     *
     * <p>REQ-PII-EMAIL-007 — @Validated 컨트롤러에서 ConstraintViolationException 발생 시 처리.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException ex) {
        String message = ex.getConstraintViolations().stream()
                .map(cv -> cv.getMessage())
                .findFirst()
                .orElse("입력값 검증 실패");
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, message);
        detail.setTitle("Input Validation Failed");
        detail.setProperty("code", AdminEmailPartialSearchException.CODE);
        return detail;
    }

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

    /**
     * 가입 승인 대기 상태 로그인 시도 → HTTP 403 Forbidden.
     *
     * <p>SPEC-CMS-USER-APPROVAL-001 REQ-UA-004 — PENDING_APPROVAL 사용자는 승인 전 로그인 불가.
     */
    @ExceptionHandler(UserPendingApprovalException.class)
    public ProblemDetail handleUserPendingApproval(UserPendingApprovalException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN, ex.getMessage());
        detail.setTitle("User Pending Approval");
        detail.setProperty("code", "USER_PENDING_APPROVAL");
        return detail;
    }

    /**
     * 승인 대기 상태가 아닌 사용자에 대한 승인/거절 시도 → HTTP 409 Conflict.
     *
     * <p>SPEC-CMS-USER-APPROVAL-001 REQ-UA-013 — 이미 처리된 사용자 재처리 거부.
     */
    @ExceptionHandler(UserNotPendingApprovalException.class)
    public ProblemDetail handleUserNotPendingApproval(UserNotPendingApprovalException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, ex.getMessage());
        detail.setTitle("User Not Pending Approval");
        detail.setProperty("code", "USER_NOT_PENDING_APPROVAL");
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
     * 게시글 이력 버전 미존재 → HTTP 404 Not Found.
     *
     * <p>SPEC-CMS-POST-HISTORY-001 REQ-PH-005 — (postId, version) 스냅샷이 없는 경우.
     */
    @ExceptionHandler(PostHistoryVersionNotFoundException.class)
    public ProblemDetail handlePostHistoryVersionNotFound(PostHistoryVersionNotFoundException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, ex.getMessage());
        detail.setTitle("Post History Version Not Found");
        detail.setProperty("code", "POST_HISTORY_VERSION_NOT_FOUND");
        return detail;
    }

    /**
     * 게시글 예약/취소 상태 전이 충돌 → HTTP 409 Conflict.
     *
     * <p>SPEC-CMS-POST-SCHEDULE-001 REQ-POST-SCHEDULE-004-2 / 007-2 —
     * 비SCHEDULED 게시글 취소 또는 DELETED 게시글 예약 시도.
     */
    @ExceptionHandler(PostScheduleConflictException.class)
    public ProblemDetail handlePostScheduleConflict(PostScheduleConflictException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, ex.getMessage());
        detail.setTitle("Post Schedule Conflict");
        detail.setProperty("code", "POST_SCHEDULE_CONFLICT");
        return detail;
    }

    /**
     * 댓글 미존재 → HTTP 404 Not Found.
     *
     * <p>REQ-BOARD-003 — id에 해당하는 댓글이 없거나 소프트 삭제된 경우.
     */
    /**
     * 댓글 모더레이션 제약 위반 → HTTP 400 Bad Request.
     *
     * <p>SPEC-CMS-COMMENT-MODERATE-001 REQ-CMTM-003/004 —
     * DELETED 상태 댓글 상태 변경 시도 또는 유효하지 않은 상태값 지정.
     */
    @ExceptionHandler(CommentModerationException.class)
    public ProblemDetail handleCommentModeration(CommentModerationException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, ex.getMessage());
        detail.setTitle("Comment Moderation Error");
        detail.setProperty("code", "COMMENT_MODERATION_ERROR");
        return detail;
    }

    /**
     * 발간자료 카테고리 삭제 충돌 → HTTP 409 Conflict.
     *
     * <p>SPEC-CMS-PUB-CAT-001 REQ-PCA-003 —
     * 자식 카테고리 또는 연결된 발간자료 존재 시.
     */
    @ExceptionHandler(PublicationCategoryConflictException.class)
    public ProblemDetail handlePublicationCategoryConflict(PublicationCategoryConflictException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, ex.getMessage());
        detail.setTitle("Publication Category Conflict");
        detail.setProperty("code", "PUBLICATION_CATEGORY_CONFLICT");
        return detail;
    }

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

    /**
     * FAQ 미존재 → HTTP 404 Not Found.
     *
     * <p>REQ-BOARD-007 — id에 해당하는 FAQ가 없거나 소프트 삭제된 경우.
     */
    @ExceptionHandler(FaqNotFoundException.class)
    public ProblemDetail handleFaqNotFound(FaqNotFoundException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, ex.getMessage());
        detail.setTitle("FAQ Not Found");
        detail.setProperty("code", "FAQ_NOT_FOUND");
        return detail;
    }

    /**
     * Q&A 미존재 → HTTP 404 Not Found.
     *
     * <p>REQ-BOARD-008 — id에 해당하는 Q&A가 없거나, 비공개 항목에 권한 없는 사용자가 접근한 경우.
     */
    @ExceptionHandler(QnaNotFoundException.class)
    public ProblemDetail handleQnaNotFound(QnaNotFoundException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, ex.getMessage());
        detail.setTitle("Q&A Not Found");
        detail.setProperty("code", "QNA_NOT_FOUND");
        return detail;
    }

    /**
     * 발간자료 미존재 → HTTP 404 Not Found.
     *
     * <p>REQ-BOARD-012 — id에 해당하는 발간자료가 없거나 소프트 삭제된 경우.
     */
    @ExceptionHandler(PublicationNotFoundException.class)
    public ProblemDetail handlePublicationNotFound(PublicationNotFoundException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, ex.getMessage());
        detail.setTitle("Publication Not Found");
        detail.setProperty("code", "PUBLICATION_NOT_FOUND");
        return detail;
    }

    /**
     * 설문조사 미존재 → HTTP 404 Not Found.
     *
     * <p>REQ-BOARD-013 — id에 해당하는 설문이 없거나 소프트 삭제된 경우.
     */
    @ExceptionHandler(SurveyNotFoundException.class)
    public ProblemDetail handleSurveyNotFound(SurveyNotFoundException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, ex.getMessage());
        detail.setTitle("Survey Not Found");
        detail.setProperty("code", "SURVEY_NOT_FOUND");
        return detail;
    }

    /**
     * 설문 응답 가능 조건 위반 → HTTP 400 Bad Request.
     *
     * <p>REQ-BOARD-013-D-3 — 설문 기간 외 / 응답 한도 초과 / 중복 응답.
     */
    @ExceptionHandler(SurveyPeriodInvalidException.class)
    public ProblemDetail handleSurveyPeriodInvalid(SurveyPeriodInvalidException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, ex.getMessage());
        detail.setTitle("Survey Period Invalid");
        detail.setProperty("code", "SURVEY_PERIOD_INVALID");
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

    /** Export 행 수 상한 초과 → 400. SPEC-CMS-KPI-001 Phase 3 AC-010 */
    @ExceptionHandler(ExportSizeLimitException.class)
    public ProblemDetail handleExportSizeLimit(ExportSizeLimitException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        detail.setTitle("Export Size Limit Exceeded");
        detail.setProperty("code", "EXPORT_SIZE_LIMIT_EXCEEDED");
        return detail;
    }

    /** Export 다운로드 서명 위조/손상 → 400. SPEC-CMS-KPI-001 Phase 3 AC-020 */
    @ExceptionHandler(ExportSignatureInvalidException.class)
    public ProblemDetail handleExportSignatureInvalid(ExportSignatureInvalidException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        detail.setTitle("Export Signature Invalid");
        detail.setProperty("code", "EXPORT_SIGNATURE_INVALID");
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

    /** 위젯 부서 범위 위반 → 403. REQ-VIZ-001-D-8 A-8 */
    @ExceptionHandler(WidgetDeptMismatchException.class)
    public ProblemDetail handleWidgetDeptMismatch(WidgetDeptMismatchException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
        detail.setTitle("Widget Dept Mismatch");
        detail.setProperty("code", "WIDGET_DEPT_MISMATCH");
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

    /**
     * Spring Security 인가 거부(@PreAuthorize 등) → 403 Forbidden.
     *
     * <p>Spring Security 6의 {@link AuthorizationDeniedException}과
     * 레거시 {@link AccessDeniedException}을 모두 처리한다.
     */
    @ExceptionHandler({AuthorizationDeniedException.class, AccessDeniedException.class})
    public ProblemDetail handleAuthorizationDenied(RuntimeException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Access Denied");
        detail.setTitle("Forbidden");
        detail.setProperty("code", "ACCESS_DENIED");
        return detail;
    }

    /**
     * 도메인 서비스 소유권 위반 → 403 Forbidden.
     *
     * <p>대시보드 레이아웃·저장된 뷰 등에서 본인 소유가 아닌 리소스 변경 시도 시
     * 서비스 계층이 던지는 {@link SecurityException}을 표준 403 ProblemDetail 로 매핑한다.
     * 핸들러가 없으면 미처리 예외가 500 으로 전파되므로 인가 위반의 의미가 왜곡된다(REQ-DP-003-4).
     */
    // @MX:NOTE: [AUTO] SecurityException 핸들러 — 서비스 계층 소유권 검증 실패를 403 으로 표준화
    // @MX:REASON: 전용 핸들러 부재 시 인가 위반(SecurityException)이 500 으로 전파되어 보안 정합성·테스트(AC-DP-003-3)가 깨짐. 코드는 ACCESS_DENIED 로 통일.
    @ExceptionHandler(SecurityException.class)
    public ProblemDetail handleSecurityException(SecurityException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
        detail.setTitle("Forbidden");
        detail.setProperty("code", "ACCESS_DENIED");
        return detail;
    }

    // ─── REQ-SEARCH-001~009 통합 검색 예외 (SPEC-CMS-010) ──────────────────────

    /** 검색 쿼리 길이 초과 → 400. REQ-SEARCH-001 / REQ-SEARCH-005 */
    @ExceptionHandler(SearchQueryTooLongException.class)
    public ProblemDetail handleSearchQueryTooLong(SearchQueryTooLongException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        detail.setTitle("Search Query Too Long");
        detail.setProperty("code", "SEARCH_QUERY_TOO_LONG");
        detail.setProperty("actual", ex.getActual());
        detail.setProperty("max", ex.getMax());
        return detail;
    }

    /** 지원되지 않는 검색 도메인 → 400. REQ-SEARCH-004 */
    @ExceptionHandler(SearchDomainInvalidException.class)
    public ProblemDetail handleSearchDomainInvalid(SearchDomainInvalidException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        detail.setTitle("Search Domain Invalid");
        detail.setProperty("code", "SEARCH_DOMAIN_INVALID");
        detail.setProperty("domain", ex.getDomain());
        return detail;
    }

    /** 지원되지 않는 locale → 400. REQ-SEARCH-010 */
    @ExceptionHandler(SearchLocaleUnsupportedException.class)
    public ProblemDetail handleSearchLocaleUnsupported(SearchLocaleUnsupportedException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        detail.setTitle("Search Locale Unsupported");
        detail.setProperty("code", "SEARCH_LOCALE_UNSUPPORTED");
        detail.setProperty("locale", ex.getLocale());
        return detail;
    }

    /** 검색 클릭 윈도우 만료 → 410 Gone. REQ-SEARCH-008 */
    @ExceptionHandler(SearchClickWindowExpiredException.class)
    public ProblemDetail handleSearchClickWindowExpired(SearchClickWindowExpiredException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.GONE, ex.getMessage());
        detail.setTitle("Search Click Window Expired");
        detail.setProperty("code", "SEARCH_CLICK_WINDOW_EXPIRED");
        detail.setProperty("searchLogId", ex.getSearchLogId());
        return detail;
    }

    /** 검색 로그 미존재 → 404. REQ-SEARCH-008 */
    @ExceptionHandler(SearchLogNotFoundException.class)
    public ProblemDetail handleSearchLogNotFound(SearchLogNotFoundException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        detail.setTitle("Search Log Not Found");
        detail.setProperty("code", "SEARCH_LOG_NOT_FOUND");
        return detail;
    }

    /** 동의어 중복 → 409. REQ-SEARCH-009 */
    @ExceptionHandler(DuplicateSynonymException.class)
    public ProblemDetail handleDuplicateSynonym(DuplicateSynonymException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        detail.setTitle("Duplicate Synonym");
        detail.setProperty("code", "SEARCH_SYNONYM_DUPLICATE");
        return detail;
    }

    /** 동의어 자기참조 → 400. REQ-SEARCH-009 */
    @ExceptionHandler(SynonymSelfException.class)
    public ProblemDetail handleSynonymSelf(SynonymSelfException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        detail.setTitle("Synonym Self Reference");
        detail.setProperty("code", "SEARCH_SYNONYM_SELF");
        return detail;
    }

    /** 동의어 미존재 → 404. REQ-SEARCH-009 */
    @ExceptionHandler(SynonymNotFoundException.class)
    public ProblemDetail handleSynonymNotFound(SynonymNotFoundException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        detail.setTitle("Synonym Not Found");
        detail.setProperty("code", "SEARCH_SYNONYM_NOT_FOUND");
        return detail;
    }

    // ─── DB 무결성 제약 위반 공통 핸들러 ─────────────────────────────────────────

    /**
     * DB Unique 제약 위반 → 409 Conflict.
     *
     * <p>uk_dashboard_owner_name, uk_code_group_code 등 UNIQUE 인덱스 위반 시.
     * DuplicateKeyException 은 DataIntegrityViolationException 의 하위 클래스이므로
     * Spring 이 더 구체적인 이 핸들러를 우선 선택한다.
     */
    @ExceptionHandler(DuplicateKeyException.class)
    public ProblemDetail handleDuplicateKey(DuplicateKeyException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, "이미 존재하는 값입니다.");
        detail.setTitle("Duplicate Key");
        detail.setProperty("code", "DUPLICATE_KEY");
        return detail;
    }

    /**
     * DB 참조 무결성 위반 (FK RESTRICT 등) → 409 Conflict.
     *
     * <p>자식 레코드가 존재하는 부모 삭제 시도, NOT NULL 위반 외
     * DuplicateKeyException 으로 처리되지 않는 제약 위반을 처리한다.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, "데이터 무결성 제약 위반입니다.");
        detail.setTitle("Data Integrity Violation");
        detail.setProperty("code", "DATA_INTEGRITY_VIOLATION");
        return detail;
    }

    // ─── SPEC-CMS-004 Content Domain Exceptions ─────────────────────────────

    /** 메뉴 깊이 초과 → 400. REQ-CONTENT-001-D-1 */
    @ExceptionHandler(MenuDepthExceededException.class)
    public ProblemDetail handleMenuDepthExceeded(MenuDepthExceededException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        detail.setTitle("Menu Depth Exceeded");
        detail.setProperty("code", "MENU_DEPTH_EXCEEDED");
        return detail;
    }

    /** 메뉴 코드 중복 → 409. REQ-CONTENT-001-D-1 */
    @ExceptionHandler(MenuCodeDuplicateException.class)
    public ProblemDetail handleMenuCodeDuplicate(MenuCodeDuplicateException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        detail.setTitle("Menu Code Duplicate");
        detail.setProperty("code", "MENU_CODE_DUPLICATE");
        return detail;
    }

    /** 메뉴 순환 참조 → 400. REQ-CONTENT-001-D-4 */
    @ExceptionHandler(MenuCircularReferenceException.class)
    public ProblemDetail handleMenuCycle(MenuCircularReferenceException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        detail.setTitle("Menu Cycle Detected");
        detail.setProperty("code", "MENU_CYCLE_DETECTED");
        return detail;
    }

    /** 페이지 slug 패턴 위반 → 400. REQ-CONTENT-005-D-1 */
    @ExceptionHandler(PageSlugInvalidException.class)
    public ProblemDetail handlePageSlugInvalid(PageSlugInvalidException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        detail.setTitle("Page Slug Invalid");
        detail.setProperty("code", "SLUG_INVALID_PATTERN");
        return detail;
    }

    /** 페이지 상태 전이 위반 → 409. REQ-CONTENT-005-D */
    @ExceptionHandler(PageStatusTransitionException.class)
    public ProblemDetail handlePageStatusTransition(PageStatusTransitionException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        detail.setTitle("Page Status Transition Invalid");
        detail.setProperty("code", "PAGE_STATUS_TRANSITION_INVALID");
        return detail;
    }

    /** HTML 블록 SYSADMIN 한정 → 403. REQ-CONTENT-006-D-1 */
    @ExceptionHandler(PageBlockTypeForbiddenException.class)
    public ProblemDetail handlePageBlockTypeForbidden(PageBlockTypeForbiddenException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
        detail.setTitle("Page Block Type Forbidden");
        detail.setProperty("code", "BLOCK_HTML_REQUIRES_SYSADMIN");
        return detail;
    }

    /** 팝업 노출 기간 역전 → 400. REQ-CONTENT-008-D-1 */
    @ExceptionHandler(PopupPeriodInvalidException.class)
    public ProblemDetail handlePopupPeriodInvalid(PopupPeriodInvalidException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        detail.setTitle("Popup Period Invalid");
        detail.setProperty("code", "POPUP_PERIOD_INVALID");
        return detail;
    }

    /** 팝업 타겟 역할 누락 → 400. REQ-CONTENT-008-D-1 */
    @ExceptionHandler(PopupTargetMissingException.class)
    public ProblemDetail handlePopupTargetMissing(PopupTargetMissingException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        detail.setTitle("Popup Target Missing");
        detail.setProperty("code", "POPUP_TARGET_MISSING");
        return detail;
    }

    /** 배너 alt_text 누락 → 400. REQ-CONTENT-009-D-1 (KWCAG 2.2 AA 1.1.1) */
    @ExceptionHandler(BannerAltTextMissingException.class)
    public ProblemDetail handleBannerAltMissing(BannerAltTextMissingException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        detail.setTitle("Banner Alt Required");
        detail.setProperty("code", "BANNER_ALT_REQUIRED");
        return detail;
    }

    /** 배너 노출 기간 역전 → 400. REQ-CONTENT-009-D-1 */
    @ExceptionHandler(BannerPeriodInvalidException.class)
    public ProblemDetail handleBannerPeriodInvalid(BannerPeriodInvalidException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        detail.setTitle("Banner Period Invalid");
        detail.setProperty("code", "BANNER_PERIOD_INVALID");
        return detail;
    }

    /** 멀티사이트 비활성화 상태에서 site 생성 시도 → 409. REQ-CONTENT-003-D-3 */
    @ExceptionHandler(SiteMultiDisabledException.class)
    public ProblemDetail handleSiteMultiDisabled(SiteMultiDisabledException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        detail.setTitle("Site Multi Disabled");
        detail.setProperty("code", "SITE_MULTI_DISABLED");
        return detail;
    }

    /** 템플릿 필수 슬롯 누락 → 400. REQ-CONTENT-004-D-1 */
    @ExceptionHandler(TemplateMissingSlotException.class)
    public ProblemDetail handleTemplateMissingSlot(TemplateMissingSlotException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        detail.setTitle("Template Content Slot Missing");
        detail.setProperty("code", "TEMPLATE_CONTENT_SLOT_MISSING");
        return detail;
    }

    /** 사용 중인 템플릿 비활성화 거부 → 409. REQ-CONTENT-004-D-3 */
    @ExceptionHandler(TemplateInUseException.class)
    public ProblemDetail handleTemplateInUse(TemplateInUseException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        detail.setTitle("Template In Use");
        detail.setProperty("code", "TEMPLATE_IN_USE");
        return detail;
    }

    // ─── SPEC-CMS-005 System Domain Exceptions ──────────────────────────────

    /** (groupCode, code) UNIQUE 위반 → 409. REQ-SYSTEM-004-D-2 */
    @ExceptionHandler(CodeDuplicateException.class)
    public ProblemDetail handleCodeDuplicate(CodeDuplicateException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        detail.setTitle("Code Duplicate");
        detail.setProperty("code", "CODE_DUPLICATE");
        return detail;
    }

    /** 사용 중인 코드 그룹 삭제 거부 → 409. REQ-SYSTEM-004-D-1 */
    @ExceptionHandler(CodeGroupInUseException.class)
    public ProblemDetail handleCodeGroupInUse(CodeGroupInUseException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        detail.setTitle("Code Group In Use");
        detail.setProperty("code", "CODE_GROUP_IN_USE");
        return detail;
    }

    // ─── Content Page Domain Exceptions ─────────────────────────────────────

    /** 페이지 없음 또는 비공개 상태 → 404. REQ-CONTENT-005-D */
    @ExceptionHandler(PageNotFoundException.class)
    public ProblemDetail handlePageNotFound(PageNotFoundException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        detail.setTitle("Page Not Found");
        detail.setProperty("code", "PAGE_NOT_FOUND");
        return detail;
    }

    /** 동일 (siteId, slug) 중복 → 409. REQ-CONTENT-005-D-1 */
    @ExceptionHandler(PageSlugDuplicateException.class)
    public ProblemDetail handlePageSlugDuplicate(PageSlugDuplicateException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        detail.setTitle("Slug Duplicate");
        detail.setProperty("code", "SLUG_DUPLICATE");
        return detail;
    }

    /** 요청한 버전의 페이지 이력 없음 → 404. REQ-CONTENT-005-D-6 */
    @ExceptionHandler(PageHistoryVersionNotFoundException.class)
    public ProblemDetail handlePageHistoryVersionNotFound(PageHistoryVersionNotFoundException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        detail.setTitle("Page History Version Not Found");
        detail.setProperty("code", "PAGE_HISTORY_NOT_FOUND");
        return detail;
    }

    /**
     * AI 시뮬레이션 세션 미존재/만료 → HTTP 404 Not Found.
     *
     * <p>SPEC-CMS-AI-001 — sessionId 미존재 또는 expires_at &lt; now().
     */
    @ExceptionHandler(AiSimulationNotFoundException.class)
    public ProblemDetail handleAiSimulationNotFound(AiSimulationNotFoundException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, ex.getMessage());
        detail.setTitle("AI Simulation Not Found");
        detail.setProperty("code", AiSimulationNotFoundException.CODE);
        return detail;
    }

    /**
     * AI 예측 로그 미존재 → HTTP 404 Not Found.
     *
     * <p>SPEC-CMS-AI-001 — risk-score explain 대상 예측 ID가 없는 경우.
     */
    @ExceptionHandler(AiPredictionNotFoundException.class)
    public ProblemDetail handleAiPredictionNotFound(AiPredictionNotFoundException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, ex.getMessage());
        detail.setTitle("AI Prediction Not Found");
        detail.setProperty("code", AiPredictionNotFoundException.CODE);
        return detail;
    }

    /**
     * AI 시뮬레이션 레이트리밋 초과 → HTTP 429 Too Many Requests.
     *
     * <p>SPEC-CMS-AI-001 — ip-hash 기준 시간당 호출 한도 초과.
     */
    @ExceptionHandler(AiRateLimitExceededException.class)
    public ProblemDetail handleAiRateLimitExceeded(AiRateLimitExceededException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.TOO_MANY_REQUESTS, ex.getMessage());
        detail.setTitle("AI Rate Limit Exceeded");
        detail.setProperty("code", AiRateLimitExceededException.CODE);
        return detail;
    }

    /**
     * 추천 피드백 무결성 위반 → HTTP 400 Bad Request.
     *
     * <p>SPEC-CMS-AI-002 REQ-PM-013 — interaction_type=VIEWED 또는 policy_id 누락.
     * DB chk_aprl_feedback 제약과 일관된다.
     */
    @ExceptionHandler(AiFeedbackInvalidException.class)
    public ProblemDetail handleAiFeedbackInvalid(AiFeedbackInvalidException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, ex.getMessage());
        detail.setTitle("AI Feedback Invalid");
        detail.setProperty("code", AiFeedbackInvalidException.CODE);
        return detail;
    }

    // ─── SPEC-CMS-CONTENT-REVISION-001 — 리비전 낙관적 잠금 충돌 ──────────────────

    /**
     * 콘텐츠 리비전 낙관적 잠금 충돌 → HTTP 409 Conflict.
     *
     * <p>REQ-REV-005 — 게시물·페이지 수정 시 expectedVersion 이 서버 현재 버전과
     * 불일치하면 발생. 응답 바디에 현재 버전(currentVersion)을 실어 재시도를 유도한다.
     */
    // @MX:NOTE: [AUTO] RevisionConflictException 핸들러 — 게시물/페이지 공통 낙관적 잠금 충돌을 409 ProblemDetail 로 표준화
    // @MX:SPEC: SPEC-CMS-CONTENT-REVISION-001 REQ-REV-005 — code=REVISION_CONFLICT 고정, currentVersion 속성 포함
    @ExceptionHandler(kr.co.ircp.cms.common.exception.RevisionConflictException.class)
    public ProblemDetail handleRevisionConflict(kr.co.ircp.cms.common.exception.RevisionConflictException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        detail.setTitle("Revision Conflict");
        detail.setProperty("code", kr.co.ircp.cms.common.exception.RevisionConflictException.CODE);
        detail.setProperty("currentVersion", ex.getCurrentVersion());
        return detail;
    }

    /** 미처리 IllegalArgumentException → 400 (비즈니스 로직 검증 실패 fallback). */
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        detail.setTitle("Bad Request");
        detail.setProperty("code", "INVALID_ARGUMENT");
        return detail;
    }

    // ─── SPEC-CMS-DASHBOARD-PERSONALIZE-001 — 개인화 SPEC 예외 ─────────────────

    /**
     * 사용자 환경설정 / 레이아웃 위치의 낙관적 잠금 충돌 → 409 Conflict.
     *
     * <p>REQ-DP-002-4 / REQ-DP-003-5 — 다른 탭에서 동일 사용자의 preference 또는
     * layout positions 가 먼저 갱신된 경우.
     */
    @ExceptionHandler(PreferenceConflictException.class)
    public ProblemDetail handlePreferenceConflict(PreferenceConflictException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        detail.setTitle("Preference Conflict");
        detail.setProperty("code", "PREFERENCE_CONFLICT");
        return detail;
    }

    /**
     * 관리자 알림 미존재 또는 권한 없음 → 403 Forbidden.
     *
     * <p>SPEC-CMS-NOTIFICATION-CENTER-001 REQ-NC-010 — 본인 소유가 아닌 알림
     * id 호출 시도는 enumeration 방지를 위해 단일 응답으로 통합.
     */
    @ExceptionHandler(kr.co.ircp.cms.domain.notification.admin.exception.AdminNotificationNotFoundException.class)
    public ProblemDetail handleAdminNotificationNotFound(
            kr.co.ircp.cms.domain.notification.admin.exception.AdminNotificationNotFoundException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
        detail.setTitle("Admin Notification Not Found");
        detail.setProperty("code", "ADMIN_NOTIFICATION_NOT_FOUND");
        return detail;
    }

    // ─── SPEC-CMS-EMAIL-TEMPLATE-001 — 이메일 템플릿 예외 매핑 ──────────────────

    /** 이메일 템플릿 미존재 → 404 (REQ-ET-004/005). */
    @ExceptionHandler(EmailTemplateNotFoundException.class)
    public ProblemDetail handleEmailTemplateNotFound(EmailTemplateNotFoundException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        detail.setTitle("Email Template Not Found");
        detail.setProperty("code", "EMAIL_TEMPLATE_NOT_FOUND");
        return detail;
    }

    /** code+language 중복 → 409 (REQ-ET-002). */
    @ExceptionHandler(DuplicateEmailTemplateException.class)
    public ProblemDetail handleDuplicateEmailTemplate(DuplicateEmailTemplateException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        detail.setTitle("Duplicate Email Template");
        detail.setProperty("code", "EMAIL_TEMPLATE_DUPLICATE");
        return detail;
    }

    /** 필수 변수 누락 → 400 + 누락 목록 (REQ-ET-011). */
    @ExceptionHandler(MissingTemplateVariableException.class)
    public ProblemDetail handleMissingTemplateVariable(MissingTemplateVariableException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        detail.setTitle("Missing Template Variable");
        detail.setProperty("code", "EMAIL_TEMPLATE_MISSING_VARIABLE");
        detail.setProperty("missingVariables", ex.getMissingVariables());
        return detail;
    }

    /** 비활성 템플릿 실발송/테스트 발송 → 409 (REQ-ET-012). */
    @ExceptionHandler(TemplateInactiveException.class)
    public ProblemDetail handleTemplateInactive(TemplateInactiveException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        detail.setTitle("Template Inactive");
        detail.setProperty("code", "EMAIL_TEMPLATE_INACTIVE");
        return detail;
    }
}
