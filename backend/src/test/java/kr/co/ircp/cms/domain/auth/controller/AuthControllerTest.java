package kr.co.ircp.cms.domain.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import kr.co.ircp.cms.config.GlobalExceptionHandler;
import kr.co.ircp.cms.config.JwtProperties;
import kr.co.ircp.cms.domain.auth.dto.LoginRequest;
import kr.co.ircp.cms.domain.auth.dto.LoginResponse;
import kr.co.ircp.cms.domain.auth.dto.RefreshResult;
import kr.co.ircp.cms.domain.auth.exception.AccountLockedException;
import kr.co.ircp.cms.domain.auth.exception.InvalidCredentialsException;
import kr.co.ircp.cms.domain.auth.exception.TokenExpiredException;
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
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
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
}
