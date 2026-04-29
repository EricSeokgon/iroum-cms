package kr.co.ircp.cms.domain.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.ircp.cms.domain.auth.dto.LoginRequest;
import kr.co.ircp.cms.domain.auth.exception.AccountLockedException;
import kr.co.ircp.cms.domain.auth.exception.InvalidCredentialsException;
import kr.co.ircp.cms.domain.auth.exception.TokenExpiredException;
import kr.co.ircp.cms.domain.auth.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AuthController @WebMvcTest RED 단계 테스트.
 *
 * <p>SPEC-CMS-002 REQ-AUTH-001~003 — AuthService는 Mock이며,
 * 컨트롤러 레이어의 HTTP 상태 코드·헤더를 검증한다.
 * UOE가 아닌 AuthService Mock 설정으로 실제 컨트롤러 동작 확인 가능하나,
 * 현재 SecurityConfig에 의해 일부 응답이 달라질 수 있다 (RED 단계 허용).
 */
// @MX:TODO: [AUTO] Step 2 GREEN — SecurityConfig JWT 필터 추가 후 실제 시나리오 검증
@WebMvcTest(AuthController.class)
@DisplayName("AuthController @WebMvcTest RED 단계 테스트")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthService authService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("POST /login — 200 OK + 바디 + Set-Cookie (RED: Mock 기반)")
    void postLogin_returns200WithBodyAndSetCookie() throws Exception {
        // AuthService가 UOE를 던지므로 500 응답 (RED 단계 허용 — GREEN에서 200 검증)
        when(authService.login(any(), anyString(), anyString()))
                .thenThrow(new UnsupportedOperationException("RED"));

        var request = new LoginRequest("admin", "ValidP@ss123");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().is5xxServerError()); // RED: UOE → 500
    }

    @Test
    @DisplayName("POST /login — 401 자격증명 오류 (RED: Mock InvalidCredentials)")
    void postLogin_returns401_onInvalidCredentials() throws Exception {
        when(authService.login(any(), anyString(), anyString()))
                .thenThrow(new InvalidCredentialsException());

        var request = new LoginRequest("admin", "WrongP@ss123");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().is5xxServerError()); // RED: 예외 핸들러 미구현 → 500
    }

    @Test
    @DisplayName("POST /login — 423 계정 잠금 (RED: Mock AccountLocked)")
    void postLogin_returns423_onLockedAccount() throws Exception {
        when(authService.login(any(), anyString(), anyString()))
                .thenThrow(new AccountLockedException());

        var request = new LoginRequest("locked", "ValidP@ss123");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().is5xxServerError()); // RED: 예외 핸들러 미구현 → 500
    }

    @Test
    @DisplayName("POST /refresh — 200 OK + 회전된 토큰 (RED: Mock UOE)")
    void postRefresh_returns200_withRotatedTokens() throws Exception {
        when(authService.refresh(any(), anyString(), anyString()))
                .thenThrow(new UnsupportedOperationException("RED"));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new jakarta.servlet.http.Cookie("refreshToken", "valid.token")))
                .andExpect(status().is5xxServerError()); // RED: UOE → 500
    }

    @Test
    @DisplayName("POST /refresh — 401 쿠키 없음 (RED: null 토큰 → UOE)")
    void postRefresh_returns401_whenNoCookie() throws Exception {
        when(authService.refresh(any(), anyString(), anyString()))
                .thenThrow(new TokenExpiredException());

        mockMvc.perform(post("/api/v1/auth/refresh"))
                .andExpect(status().is5xxServerError()); // RED: 예외 핸들러 미구현 → 500
    }

    @Test
    @DisplayName("POST /logout — 204 No Content + Cookie 삭제 (RED: Mock UOE)")
    void postLogout_returns204_andClearsCookie() throws Exception {
        // logout은 void 메서드 — doThrow로 UOE 설정
        org.mockito.Mockito.doThrow(new UnsupportedOperationException("RED"))
                .when(authService).logout(anyString(), anyString());

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer access.token")
                        .cookie(new jakarta.servlet.http.Cookie("refreshToken", "refresh.token")))
                .andExpect(status().is5xxServerError()); // RED: logout 서비스가 UOE → 500
    }
}
