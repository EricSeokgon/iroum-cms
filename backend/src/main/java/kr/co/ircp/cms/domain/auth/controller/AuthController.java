package kr.co.ircp.cms.domain.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import kr.co.ircp.cms.domain.auth.dto.LoginRequest;
import kr.co.ircp.cms.domain.auth.dto.LoginResponse;
import kr.co.ircp.cms.domain.auth.dto.RefreshResult;
import kr.co.ircp.cms.domain.auth.service.AuthService;
import lombok.RequiredArgsConstructor;
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
 * 모든 실제 로직은 {@link AuthService}에 위임한다.
 */
// @MX:ANCHOR: [AUTO] AuthController — 인증 API의 진입 엔드포인트
// @MX:REASON: 외부 클라이언트, Spring Security 필터, API 문서 등 fan_in >= 3
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

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
        // AuthService에 위임 — AuthService가 RED 상태이므로 UOE 전파
        String ipAddress = httpRequest.getRemoteAddr();
        String userAgent = httpRequest.getHeader("User-Agent");
        LoginResponse response = authService.login(request, ipAddress, userAgent);
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/v1/auth/refresh — Refresh Token 갱신 (Rotation).
     *
     * <p>REQ-AUTH-002 — 기존 Refresh Cookie를 읽어 새 Access Token + 새 Refresh Cookie 발급.
     */
    @PostMapping("/refresh")
    public ResponseEntity<RefreshResult> refresh(
            @CookieValue(name = "refreshToken", required = false) String refreshToken,
            HttpServletRequest httpRequest) {
        String ipAddress = httpRequest.getRemoteAddr();
        String userAgent = httpRequest.getHeader("User-Agent");
        RefreshResult result = authService.refresh(refreshToken, ipAddress, userAgent);
        return ResponseEntity.ok(result);
    }

    /**
     * POST /api/v1/auth/logout — 로그아웃.
     *
     * <p>REQ-AUTH-003 — Access Token 블랙리스트 등록 + Refresh Token 회수 + Cookie 삭제.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @CookieValue(name = "refreshToken", required = false) String refreshToken) {
        String accessToken = authorization != null && authorization.startsWith("Bearer ")
                ? authorization.substring(7)
                : null;
        authService.logout(accessToken, refreshToken);
        return ResponseEntity.noContent().build();
    }
}
