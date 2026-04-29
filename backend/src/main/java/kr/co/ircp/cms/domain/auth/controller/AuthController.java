package kr.co.ircp.cms.domain.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import kr.co.ircp.cms.config.JwtProperties;
import kr.co.ircp.cms.domain.auth.dto.LoginRequest;
import kr.co.ircp.cms.domain.auth.dto.LoginResponse;
import kr.co.ircp.cms.domain.auth.dto.RefreshResult;
import kr.co.ircp.cms.domain.auth.exception.InvalidCredentialsException;
import kr.co.ircp.cms.domain.auth.service.AuthService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
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

    public AuthController(AuthService authService, JwtProperties jwtProperties) {
        this.authService = authService;
        this.jwtProperties = jwtProperties;
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
        String userAgent = httpRequest.getHeader("User-Agent");

        AuthService.LoginOutcome outcome = authService.login(request, ipAddress, userAgent);

        ResponseCookie refreshCookie = buildRefreshCookie(outcome.refreshToken());
        return ResponseEntity.ok()
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
        String userAgent = httpRequest.getHeader("User-Agent");

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
     * 클라이언트 IP 추출 (X-Forwarded-For 우선).
     */
    private String extractIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        return (xff != null && !xff.isBlank()) ? xff.split(",")[0].trim() : req.getRemoteAddr();
    }
}
