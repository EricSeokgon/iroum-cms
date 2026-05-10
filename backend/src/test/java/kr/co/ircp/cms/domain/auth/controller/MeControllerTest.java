package kr.co.ircp.cms.domain.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.ircp.cms.config.GlobalExceptionHandler;
import kr.co.ircp.cms.domain.auth.dto.UserSelf;
import kr.co.ircp.cms.domain.auth.dto.UserSelfUpdateRequest;
import kr.co.ircp.cms.domain.auth.security.JwtPrincipal;
import kr.co.ircp.cms.domain.auth.service.UserService;
import kr.co.ircp.cms.domain.board.service.QnaNotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MeController @WebMvcTest (GREEN 단계).
 *
 * <p>SPEC-CMS-002 REQ-AUTH-006 §6.3 — GET/PUT /api/v1/me 검증.
 */
@WebMvcTest(MeController.class)
@ImportAutoConfiguration(exclude = {SecurityAutoConfiguration.class})
@Import({GlobalExceptionHandler.class, kr.co.ircp.cms.support.WebMvcTestInfraConfig.class})
@DisplayName("MeController GREEN 단계 테스트")
class MeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private QnaNotificationService qnaNotificationService;

    @Autowired
    private ObjectMapper objectMapper;

    private static final JwtPrincipal ME_PRINCIPAL =
            new JwtPrincipal(2L, "me", Set.of("EDITOR"));

    // ──────────────────────────────────────────────────────────────
    // GET /api/v1/me
    // ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/v1/me — 인증 사용자면 200 OK + UserSelf 반환")
    void getMe_returns200_whenAuthenticated() throws Exception {
        UserSelf self = new UserSelf(2L, "uuid-2", "me", "me@test.com",
                "나", Set.of("EDITOR"));
        when(userService.getMe(2L)).thenReturn(self);

        mockMvc.perform(get("/api/v1/me")
                        .with(SecurityMockMvcRequestPostProcessors
                                .authentication(jwtAuth(ME_PRINCIPAL))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("me"))
                .andExpect(jsonPath("$.email").value("me@test.com"));
    }

    // ──────────────────────────────────────────────────────────────
    // PUT /api/v1/me
    // ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("PUT /api/v1/me — 이메일·이름 수정 시 200 OK")
    void updateMe_returns200() throws Exception {
        UserSelfUpdateRequest req = new UserSelfUpdateRequest("updated@test.com", "새이름");
        UserSelf updated = new UserSelf(2L, "uuid-2", "me", "updated@test.com",
                "새이름", Set.of("EDITOR"));
        when(userService.updateMe(anyLong(), any(UserSelfUpdateRequest.class))).thenReturn(updated);

        mockMvc.perform(put("/api/v1/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .with(SecurityMockMvcRequestPostProcessors
                                .authentication(jwtAuth(ME_PRINCIPAL))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("updated@test.com"))
                .andExpect(jsonPath("$.name").value("새이름"));
    }

    @Test
    @DisplayName("PUT /api/v1/me — 유효하지 않은 email 형식 시 400 Bad Request")
    void updateMe_returns400_onInvalidEmail() throws Exception {
        UserSelfUpdateRequest req = new UserSelfUpdateRequest("not-an-email", "이름");

        mockMvc.perform(put("/api/v1/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .with(SecurityMockMvcRequestPostProcessors
                                .authentication(jwtAuth(ME_PRINCIPAL))))
                .andExpect(status().isBadRequest());
    }

    // ──────────────────────────────────────────────────────────────
    // SPEC-CMS-SECURITY-CTRL-AUTHZ-COVERAGE-001 — 권한 거부 시나리오 (적용 불가)
    //
    // MeController는 메소드 레벨 @PreAuthorize 어노테이션이 없으며,
    // 운영 환경에서는 SecurityConfig의 HTTP 레벨 정책(/api/v1/me/** authenticated())로 차단된다.
    //
    // 본 슬라이스 테스트는 SecurityAutoConfiguration을 제외하므로 HTTP 레벨 정책이 미적용되며,
    // 익명 요청 시 @AuthenticationPrincipal JwtPrincipal이 null로 주입되어
    // controller 본체에서 NullPointerException(500)이 발생한다 — 401/403 응답 검증 불가.
    //
    // 401(미인증) / 403(권한 부족) 회귀는 SPEC-CMS-SECURITY-AUTHZ-MATRIX-001
    // (HTTP 매트릭스 IT 레이어, @SpringBootTest)에서 검증한다.
    // ──────────────────────────────────────────────────────────────

    // ──────────────────────────────────────────────────────────────
    // 헬퍼
    // ──────────────────────────────────────────────────────────────

    private UsernamePasswordAuthenticationToken jwtAuth(JwtPrincipal principal) {
        return new UsernamePasswordAuthenticationToken(
                principal, null,
                principal.roles().stream()
                        .map(r -> (org.springframework.security.core.GrantedAuthority)
                                () -> "ROLE_" + r)
                        .toList());
    }
}
