package kr.co.ircp.cms.domain.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import kr.co.ircp.cms.config.GlobalExceptionHandler;
import kr.co.ircp.cms.config.JwtProperties;
import kr.co.ircp.cms.domain.auth.dto.LoginRequest;
import kr.co.ircp.cms.domain.auth.dto.LoginResponse;
import kr.co.ircp.cms.domain.auth.dto.PasswordChangeRequest;
import kr.co.ircp.cms.domain.auth.dto.RefreshResult;
import kr.co.ircp.cms.domain.auth.exception.AccountLockedException;
import kr.co.ircp.cms.domain.auth.exception.InvalidCredentialsException;
import kr.co.ircp.cms.domain.auth.exception.PasswordPolicyViolationException;
import kr.co.ircp.cms.domain.auth.exception.PasswordReuseException;
import kr.co.ircp.cms.domain.auth.security.JwtPrincipal;
import kr.co.ircp.cms.domain.auth.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AuthController @WebMvcTest GREEN 단계 테스트.
 *
 * <p>SPEC-CMS-002 REQ-AUTH-001~003 — HTTP 상태 코드·헤더·쿠키를 검증한다.
 * AuthService는 Mock이며, 컨트롤러·예외 핸들러 레이어만 검증.
 */
// @MX:NOTE: [AUTO] Step 2 GREEN — UOE 기반 5xx 검증에서 실제 HTTP 시나리오 검증으로 전환
@WebMvcTest(AuthController.class)
@ImportAutoConfiguration(exclude = {SecurityAutoConfiguration.class})
@Import(GlobalExceptionHandler.class)
@DisplayName("AuthController GREEN 단계 테스트")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthService authService;

    @MockBean
    private JwtProperties jwtProperties;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        // JwtProperties Mock 설정 — Cookie maxAge 계산용
        when(jwtProperties.refreshTokenTtl()).thenReturn(Duration.ofDays(7));
    }

    @Test
    @DisplayName("POST /login — 200 OK + 바디 + Set-Cookie(refresh_token, HttpOnly, SameSite=Strict)")
    void postLogin_returns200WithBodyAndSetCookie() throws Exception {
        when(authService.login(any(), anyString(), anyString())).thenReturn(
                new AuthService.LoginOutcome(
                        new LoginResponse("access-jwt", 900L, "Bearer"),
                        "refresh-jwt"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("admin", "ValidP@ss123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-jwt"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresInSeconds").value(900))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refresh_token=refresh-jwt")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Strict")));
    }

    @Test
    @DisplayName("POST /login — 401 자격증명 오류 (InvalidCredentialsException → 401)")
    void postLogin_returns401_onInvalidCredentials() throws Exception {
        when(authService.login(any(), anyString(), anyString()))
                .thenThrow(new InvalidCredentialsException());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("admin", "WrongP@ss123"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /login — 423 계정 잠금 (AccountLockedException → 423)")
    void postLogin_returns423_onLockedAccount() throws Exception {
        when(authService.login(any(), anyString(), anyString()))
                .thenThrow(new AccountLockedException());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("locked", "ValidP@ss123"))))
                .andExpect(status().isLocked());
    }

    @Test
    @DisplayName("POST /refresh — 200 OK + 회전된 토큰 + 새 Set-Cookie")
    void postRefresh_returns200_withRotatedTokens() throws Exception {
        when(authService.refresh(eq("old-refresh"), anyString(), anyString())).thenReturn(
                new RefreshResult("new-access", "new-refresh", 900L, 604800L));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie("refresh_token", "old-refresh")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new-access"))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refresh_token=new-refresh")));
    }

    @Test
    @DisplayName("POST /refresh — 401 쿠키 없음 (InvalidCredentialsException → 401)")
    void postRefresh_returns401_whenNoCookie() throws Exception {
        // 쿠키 없으면 컨트롤러가 직접 InvalidCredentialsException throw
        mockMvc.perform(post("/api/v1/auth/refresh"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /logout — 204 No Content + Cookie 삭제(Max-Age=0)")
    void postLogout_returns204_andClearsCookie() throws Exception {
        doNothing().when(authService).logout(anyString(), anyString());

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer access-jwt")
                        .cookie(new Cookie("refresh_token", "old-refresh")))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refresh_token=")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));
    }

    // ============================================================
    // REQ-AUTH-009 / REQ-AUTH-010: POST /password/change
    // ============================================================

    /**
     * JwtPrincipal을 SecurityContext에 주입하는 헬퍼.
     */
    private UsernamePasswordAuthenticationToken principalAuth(long userId) {
        JwtPrincipal principal = new JwtPrincipal(userId, "admin", Set.of("VIEWER"));
        return new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_VIEWER")));
    }

    @Test
    @DisplayName("POST /password/change — 200 OK + Cookie 삭제(Max-Age=0)")
    void postPasswordChange_returns200_andClearsCookie() throws Exception {
        doNothing().when(authService).changePassword(anyLong(), anyString(), anyString());

        mockMvc.perform(post("/api/v1/auth/password/change")
                        .with(authentication(principalAuth(1L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PasswordChangeRequest("OldP@ss123", "NewP@ss456!"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("비밀번호가 변경되었습니다. 다시 로그인해 주세요."))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refresh_token=")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));
    }

    @Test
    @DisplayName("POST /password/change — 401 현재 비밀번호 불일치 (InvalidCredentialsException)")
    void postPasswordChange_returns401_onCurrentMismatch() throws Exception {
        doThrow(new InvalidCredentialsException("현재 비밀번호가 일치하지 않습니다"))
                .when(authService).changePassword(anyLong(), anyString(), anyString());

        mockMvc.perform(post("/api/v1/auth/password/change")
                        .with(authentication(principalAuth(1L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PasswordChangeRequest("WrongOld", "NewP@ss456!"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"));
    }

    @Test
    @DisplayName("POST /password/change — 400 새 비밀번호 정책 위반 (PASSWORD_POLICY)")
    void postPasswordChange_returns400_onPasswordPolicy() throws Exception {
        doThrow(new PasswordPolicyViolationException("비밀번호 정책 위반"))
                .when(authService).changePassword(anyLong(), anyString(), anyString());

        mockMvc.perform(post("/api/v1/auth/password/change")
                        .with(authentication(principalAuth(1L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PasswordChangeRequest("OldP@ss123", "weak"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_POLICY"));
    }

    @Test
    @DisplayName("POST /password/change — 400 비밀번호 재사용 금지 (PASSWORD_REUSE)")
    void postPasswordChange_returns400_onPasswordReuse() throws Exception {
        doThrow(new PasswordReuseException("최근 5회 사용한 비밀번호는 재사용할 수 없습니다"))
                .when(authService).changePassword(anyLong(), anyString(), anyString());

        mockMvc.perform(post("/api/v1/auth/password/change")
                        .with(authentication(principalAuth(1L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PasswordChangeRequest("OldP@ss123", "OldP@ss123"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_REUSE"));
    }
}
