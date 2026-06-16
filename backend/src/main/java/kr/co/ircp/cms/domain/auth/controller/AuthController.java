package kr.co.ircp.cms.domain.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import kr.co.ircp.cms.config.JwtProperties;
import kr.co.ircp.cms.domain.auth.dto.LoginRequest;
import kr.co.ircp.cms.domain.auth.dto.LoginResponse;
import kr.co.ircp.cms.domain.auth.dto.PasswordChangeRequest;
import kr.co.ircp.cms.domain.auth.dto.PasswordChangeResponse;
import kr.co.ircp.cms.domain.auth.dto.PasswordResetConfirmDto;
import kr.co.ircp.cms.domain.auth.dto.PasswordResetConfirmResponse;
import kr.co.ircp.cms.domain.auth.dto.PasswordResetRequestDto;
import kr.co.ircp.cms.domain.auth.dto.PasswordResetRequestResponse;
import kr.co.ircp.cms.domain.auth.dto.PublicRegisterRequest;
import kr.co.ircp.cms.domain.auth.dto.RefreshResult;
import kr.co.ircp.cms.domain.auth.dto.VerifyConfirmRequest;
import kr.co.ircp.cms.domain.auth.dto.VerifyConfirmResponse;
import kr.co.ircp.cms.domain.auth.dto.VerifyRequestRequest;
import kr.co.ircp.cms.domain.auth.dto.VerifyRequestResponse;
import kr.co.ircp.cms.domain.auth.exception.InvalidCredentialsException;
import kr.co.ircp.cms.domain.auth.security.JwtPrincipal;
import kr.co.ircp.cms.domain.auth.service.AuthService;
import kr.co.ircp.cms.domain.auth.service.VerificationService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인증 REST 컨트롤러.
 *
 * <p>SPEC-CMS-002 REQ-AUTH-001~003 — 로그인, 토큰 갱신, 로그아웃 엔드포인트.
 * Refresh Token은 HttpOnly Secure SameSite=Strict 쿠키로 관리한다.
 */
// @MX:ANCHOR: [AUTO] AuthController — 인증 API의 진입 엔드포인트
// @MX:REASON: 외부 클라이언트, Spring Security 필터, API 문서 등 fan_in >= 3
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final String REFRESH_COOKIE = "refresh_token";

    private final AuthService authService;
    private final JwtProperties jwtProperties;
    private final VerificationService verificationService;

    public AuthController(AuthService authService, JwtProperties jwtProperties,
            VerificationService verificationService) {
        this.authService = authService;
        this.jwtProperties = jwtProperties;
        this.verificationService = verificationService;
    }

    /**
     * POST /api/v1/auth/login — 일반 로그인.
     *
     * <p>REQ-AUTH-001 — 200 OK + Access Token (바디) + Refresh Token (Set-Cookie HttpOnly).
     * 실패 시: 401 (잘못된 자격증명), 423 (계정 잠금).
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {
        String ipAddress = httpRequest.getRemoteAddr();
        String userAgent = resolveUserAgent(httpRequest);

        AuthService.LoginOutcome outcome = authService.login(request, ipAddress, userAgent);

        ResponseCookie refreshCookie = buildRefreshCookie(outcome.refreshToken());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(outcome.response());
    }

    /**
     * POST /api/v1/auth/register — 공개 사이트(시민) 회원가입.
     *
     * <p>관리자 콘솔의 {@code POST /api/v1/users} (SUPER_ADMIN 전용) 와 달리 anonymous 호출 가능.
     * 가입 즉시 MEMBER 역할이 부여되고 access/refresh 토큰이 함께 발급되며,
     * refresh 토큰은 HttpOnly Secure SameSite=Strict 쿠키로 내려간다.
     * 이미 가입된 이메일이면 409, 비밀번호 정책 위반이면 400 을 반환한다.
     */
    @PostMapping("/register")
    public ResponseEntity<?> register(
            @Valid @RequestBody PublicRegisterRequest request,
            HttpServletRequest httpRequest) {
        String ipAddress = httpRequest.getRemoteAddr();
        String userAgent = resolveUserAgent(httpRequest);

        AuthService.RegisterResult result = authService.registerPublicUser(request, ipAddress, userAgent);

        // SPEC-CMS-USER-APPROVAL-001 REQ-UA-001 — 게이트 ON: JWT/쿠키 없이 202 Accepted + 안내 메시지.
        if (result instanceof AuthService.RegisterResult.PendingApproval) {
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(Map.of("message", "가입 신청이 접수되었습니다. 관리자 승인 후 로그인 가능합니다."));
        }

        // 게이트 OFF — 기존 동작: 201 Created + Access Token(바디) + Refresh Token(Set-Cookie).
        AuthService.LoginOutcome outcome = ((AuthService.RegisterResult.Approved) result).loginOutcome();
        ResponseCookie refreshCookie = buildRefreshCookie(outcome.refreshToken());
        return ResponseEntity.status(201)
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(outcome.response());
    }

    /**
     * POST /api/v1/auth/refresh — Refresh Token 갱신 (Rotation).
     *
     * <p>REQ-AUTH-002 — 기존 Refresh Cookie를 읽어 새 Access Token + 새 Refresh Cookie 발급.
     * 쿠키 없음 시 401 반환.
     */
    @PostMapping("/refresh")
    public ResponseEntity<RefreshResult> refresh(
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken,
            HttpServletRequest httpRequest) {
        if (refreshToken == null) {
            throw new InvalidCredentialsException("refresh_token cookie missing");
        }
        String ipAddress = httpRequest.getRemoteAddr();
        String userAgent = resolveUserAgent(httpRequest);

        RefreshResult result = authService.refresh(refreshToken, ipAddress, userAgent);

        ResponseCookie newCookie = buildRefreshCookie(result.newRefreshToken());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, newCookie.toString())
                .body(result);
    }

    /**
     * POST /api/v1/auth/logout — 로그아웃.
     *
     * <p>REQ-AUTH-003 — Access Token 블랙리스트 등록 + Refresh Token 회수 + Cookie 삭제.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken) {
        String accessToken = authorization != null && authorization.startsWith("Bearer ")
                ? authorization.substring(7)
                : null;
        authService.logout(accessToken, refreshToken);

        // Refresh Token 쿠키 삭제 (Max-Age=0)
        ResponseCookie clearCookie = ResponseCookie.from(REFRESH_COOKIE, "")
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/api/v1/auth")
                .maxAge(0)
                .build();

        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, clearCookie.toString())
                .build();
    }

    /**
     * Refresh Token HttpOnly 쿠키 생성 헬퍼.
     */
    private ResponseCookie buildRefreshCookie(String value) {
        return ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/api/v1/auth")
                .maxAge(jwtProperties.refreshTokenTtl())
                .build();
    }

    /**
     * POST /api/v1/auth/password/change — 비밀번호 변경.
     *
     * <p>REQ-AUTH-009 — 현재 비밀번호 확인 후 새 비밀번호로 변경.
     * REQ-AUTH-010 — 직전 5회 사용한 비밀번호 재사용 금지.
     * 변경 성공 시 모든 Refresh Token 무효화 + refresh_token 쿠키 즉시 삭제.
     */
    @PostMapping("/password/change")
    public ResponseEntity<PasswordChangeResponse> changePassword(
            @Valid @RequestBody PasswordChangeRequest req,
            @AuthenticationPrincipal JwtPrincipal principal) {
        authService.changePassword(principal.userId(), req.currentPassword(), req.newPassword());

        // 모든 refresh token 무효화 후 클라이언트 쿠키도 즉시 삭제
        ResponseCookie clearCookie = ResponseCookie.from(REFRESH_COOKIE, "")
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/api/v1/auth")
                .maxAge(0)
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, clearCookie.toString())
                .body(new PasswordChangeResponse("비밀번호가 변경되었습니다. 다시 로그인해 주세요."));
    }

    /**
     * POST /api/v1/auth/verify/request — 본인인증 OTP 발송 요청.
     *
     * <p>REQ-AUTH-017-D-1 — anonymous 허용. EMAIL 채널 OTP(6자리) 발송.
     * 429(쿨다운), 423(IP 차단), 400(형식 위반) 반환 가능.
     */
    @PostMapping("/verify/request")
    public ResponseEntity<VerifyRequestResponse> verifyRequest(
            @Valid @RequestBody VerifyRequestRequest req,
            HttpServletRequest httpRequest) {
        String ip = extractIp(httpRequest);
        String ua = resolveUserAgent(httpRequest);
        VerifyRequestResponse response = verificationService.request(req, ip, ua);
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/v1/auth/verify/confirm — OTP 코드 검증.
     *
     * <p>REQ-AUTH-017-D-2 — anonymous 허용. 성공 시 verifiedToken(5분) 반환.
     * 401(코드 불일치), 403(만료/초과/이미 사용) 반환 가능.
     */
    @PostMapping("/verify/confirm")
    public ResponseEntity<VerifyConfirmResponse> verifyConfirm(
            @Valid @RequestBody VerifyConfirmRequest req,
            HttpServletRequest httpRequest) {
        String ip = extractIp(httpRequest);
        VerifyConfirmResponse response = verificationService.confirm(req, ip);
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/v1/auth/password/reset-request — 비밀번호 재설정 이메일 발송.
     *
     * <p>REQ-AUTH-017-D-3 — anonymous 허용. 사용자 존재 여부와 무관하게 항상 200.
     */
    @PostMapping("/password/reset-request")
    public ResponseEntity<PasswordResetRequestResponse> passwordResetRequest(
            @Valid @RequestBody PasswordResetRequestDto req,
            HttpServletRequest httpRequest) {
        String ip = extractIp(httpRequest);
        String ua = resolveUserAgent(httpRequest);
        authService.requestPasswordReset(req.email(), ip, ua);
        return ResponseEntity.ok(new PasswordResetRequestResponse(
            "이메일이 등록되어 있다면 인증 코드를 발송했습니다."));
    }

    /**
     * POST /api/v1/auth/password/reset-confirm — 비밀번호 재설정 확인.
     *
     * <p>REQ-AUTH-017-D-4 — anonymous 허용. verifiedToken + 새 비밀번호로 재설정.
     * 400(정책 위반/재사용), 401(토큰 무효/만료) 반환 가능.
     */
    @PostMapping("/password/reset-confirm")
    public ResponseEntity<PasswordResetConfirmResponse> passwordResetConfirm(
            @Valid @RequestBody PasswordResetConfirmDto req) {
        authService.confirmPasswordReset(req.verifiedToken(), req.newPassword());
        return ResponseEntity.ok(new PasswordResetConfirmResponse("비밀번호가 재설정되었습니다."));
    }

    /**
     * 클라이언트 IP 추출 (X-Forwarded-For 우선).
     */
    private String extractIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        return (xff != null && !xff.isBlank()) ? xff.split(",")[0].trim() : req.getRemoteAddr();
    }

    /**
     * User-Agent 헤더 추출 — null인 경우 빈 문자열로 정규화.
     *
     * <p>다운스트림 서비스에 null이 전파되지 않도록 하고, 테스트의 {@code anyString()}
     * Mockito 매처가 일치하도록 보장한다.
     */
    private String resolveUserAgent(HttpServletRequest req) {
        String ua = req.getHeader("User-Agent");
        return ua != null ? ua : "";
    }
}
