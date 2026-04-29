package kr.co.ircp.cms.domain.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.ircp.cms.config.GlobalExceptionHandler;
import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.auth.dto.UserCreateRequest;
import kr.co.ircp.cms.domain.auth.dto.UserDetail;
import kr.co.ircp.cms.domain.auth.dto.UserSummary;
import kr.co.ircp.cms.domain.auth.dto.UserUpdateRequest;
import kr.co.ircp.cms.domain.auth.exception.DuplicateUserException;
import kr.co.ircp.cms.domain.auth.exception.UserNotFoundException;
import kr.co.ircp.cms.domain.auth.security.JwtPrincipal;
import kr.co.ircp.cms.domain.auth.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * UserController @WebMvcTest (GREEN 단계).
 *
 * <p>SPEC-CMS-002 REQ-AUTH-006 §6.3 — HTTP 계층(상태 코드, JSON 구조) 검증.
 * Security는 비활성화 후 JwtPrincipal을 직접 주입.
 */
@WebMvcTest(UserController.class)
@ImportAutoConfiguration(exclude = {SecurityAutoConfiguration.class})
@Import(GlobalExceptionHandler.class)
@DisplayName("UserController GREEN 단계 테스트")
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @Autowired
    private ObjectMapper objectMapper;

    private static final JwtPrincipal ADMIN_PRINCIPAL =
            new JwtPrincipal(1L, "admin", Set.of("SUPER_ADMIN"));

    // ──────────────────────────────────────────────────────────────
    // GET /api/v1/users
    // ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/v1/users — 200 OK + 페이징 응답")
    void list_returns200WithPagedResponse() throws Exception {
        List<UserSummary> rows = List.of(
                new UserSummary(1L, "uuid-1", "admin", "admin@test.com",
                        "관리자", "ACTIVE", null, Instant.now())
        );
        when(userService.findPage(anyInt(), anyInt(), anyString(), isNull(), isNull()))
                .thenReturn(PageResponse.of(rows, 0, 20, 1L));

        mockMvc.perform(get("/api/v1/users")
                        .with(SecurityMockMvcRequestPostProcessors.anonymous()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.page").value(0));
    }

    @Test
    @DisplayName("GET /api/v1/users — search 파라미터 전달 시 200")
    void list_withSearch_returns200() throws Exception {
        when(userService.findPage(anyInt(), anyInt(), anyString(), anyString(), isNull()))
                .thenReturn(PageResponse.of(List.of(), 0, 20, 0L));

        mockMvc.perform(get("/api/v1/users")
                        .param("search", "admin")
                        .with(SecurityMockMvcRequestPostProcessors.anonymous()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
    }

    // ──────────────────────────────────────────────────────────────
    // POST /api/v1/users
    // ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/v1/users — 201 Created")
    void create_returns201() throws Exception {
        UserCreateRequest req = new UserCreateRequest(
                "newuser", "new@test.com", "ValidP@ss123!",
                "새사용자", "ACTIVE", Set.of("VIEWER"));
        UserDetail detail = sampleDetail(10L, "newuser");
        when(userService.create(any(UserCreateRequest.class), anyLong())).thenReturn(detail);

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .with(SecurityMockMvcRequestPostProcessors
                                .authentication(jwtAuth(ADMIN_PRINCIPAL))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("newuser"));
    }

    @Test
    @DisplayName("POST /api/v1/users — 409 Conflict on duplicate username")
    void create_returns409_onDuplicate() throws Exception {
        UserCreateRequest req = new UserCreateRequest(
                "admin", "other@test.com", "ValidP@ss123!", "이름", null, Set.of());
        when(userService.create(any(), anyLong()))
                .thenThrow(new DuplicateUserException("username", "admin"));

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .with(SecurityMockMvcRequestPostProcessors
                                .authentication(jwtAuth(ADMIN_PRINCIPAL))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").exists());
    }

    @Test
    @DisplayName("POST /api/v1/users — 400 Bad Request on validation failure (blank username)")
    void create_returns400_onValidationFailure() throws Exception {
        UserCreateRequest req = new UserCreateRequest(
                "", "email@test.com", "pass", "이름", null, Set.of());

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .with(SecurityMockMvcRequestPostProcessors.anonymous()))
                .andExpect(status().isBadRequest());
    }

    // ──────────────────────────────────────────────────────────────
    // PUT /api/v1/users/{id}
    // ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("PUT /api/v1/users/{id} — 200 OK")
    void update_returns200() throws Exception {
        UserUpdateRequest req = new UserUpdateRequest("updated@test.com", "수정된이름", null, null);
        UserDetail detail = sampleDetail(1L, "admin");
        when(userService.update(eq(1L), any(UserUpdateRequest.class), anyLong()))
                .thenReturn(detail);

        mockMvc.perform(put("/api/v1/users/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .with(SecurityMockMvcRequestPostProcessors
                                .authentication(jwtAuth(ADMIN_PRINCIPAL))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    @DisplayName("PUT /api/v1/users/{id} — 404 Not Found")
    void update_returns404_whenNotFound() throws Exception {
        UserUpdateRequest req = new UserUpdateRequest(null, null, null, null);
        when(userService.update(eq(999L), any(), anyLong()))
                .thenThrow(new UserNotFoundException(999L));

        mockMvc.perform(put("/api/v1/users/999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .with(SecurityMockMvcRequestPostProcessors
                                .authentication(jwtAuth(ADMIN_PRINCIPAL))))
                .andExpect(status().isNotFound());
    }

    // ──────────────────────────────────────────────────────────────
    // DELETE /api/v1/users/{id}
    // ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("DELETE /api/v1/users/{id} — 204 No Content")
    void delete_returns204() throws Exception {
        mockMvc.perform(delete("/api/v1/users/1")
                        .with(SecurityMockMvcRequestPostProcessors
                                .authentication(jwtAuth(ADMIN_PRINCIPAL))))
                .andExpect(status().isNoContent());
    }

    // ──────────────────────────────────────────────────────────────
    // POST /{id}/unlock, /{id}/force-logout
    // ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/v1/users/{id}/unlock — 200 OK")
    void unlock_returns200() throws Exception {
        mockMvc.perform(post("/api/v1/users/1/unlock")
                        .with(SecurityMockMvcRequestPostProcessors
                                .authentication(jwtAuth(ADMIN_PRINCIPAL))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /api/v1/users/{id}/force-logout — 200 OK")
    void forceLogout_returns200() throws Exception {
        mockMvc.perform(post("/api/v1/users/1/force-logout")
                        .with(SecurityMockMvcRequestPostProcessors
                                .authentication(jwtAuth(ADMIN_PRINCIPAL))))
                .andExpect(status().isOk());
    }

    // ──────────────────────────────────────────────────────────────
    // 헬퍼
    // ──────────────────────────────────────────────────────────────

    private static long eq(long val) {
        return org.mockito.ArgumentMatchers.eq(val);
    }

    private org.springframework.security.authentication.UsernamePasswordAuthenticationToken
    jwtAuth(JwtPrincipal principal) {
        return new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                principal, null,
                principal.roles().stream()
                        .map(r -> (org.springframework.security.core.GrantedAuthority)
                                () -> "ROLE_" + r)
                        .toList());
    }

    private UserDetail sampleDetail(long id, String username) {
        return new UserDetail(id, "uuid-" + id, username, username + "@test.com",
                "테스트", "ACTIVE", 0, null, null, Instant.now(),
                Instant.now(), Instant.now(), Set.of("SUPER_ADMIN"));
    }
}
