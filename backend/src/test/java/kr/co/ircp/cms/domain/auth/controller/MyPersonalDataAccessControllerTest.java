package kr.co.ircp.cms.domain.auth.controller;

import kr.co.ircp.cms.config.GlobalExceptionHandler;
import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.auth.dto.PersonalDataAccessEntry;
import kr.co.ircp.cms.domain.auth.security.JwtPrincipal;
import kr.co.ircp.cms.domain.auth.service.PersonalDataAccessLogService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MyPersonalDataAccessController @WebMvcTest (GREEN 단계).
 *
 * <p>REQ-AUTH-018-D-4 — 본인 개인정보 접근 이력 조회 HTTP 계층 검증.
 * principal.userId()를 직접 사용하여 타인 이력 조회 가능성을 차단하는 동작을 검증한다.
 */
@WebMvcTest(MyPersonalDataAccessController.class)
@ImportAutoConfiguration(exclude = {SecurityAutoConfiguration.class})
@Import({GlobalExceptionHandler.class, kr.co.ircp.cms.support.WebMvcTestInfraConfig.class})
@DisplayName("MyPersonalDataAccessController GREEN 테스트 (REQ-AUTH-018-D-4)")
class MyPersonalDataAccessControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PersonalDataAccessLogService service;

    private static final long VIEWER_USER_ID = 99L;

    private static final JwtPrincipal MY_PRINCIPAL =
            new JwtPrincipal(VIEWER_USER_ID, "myUser", Set.of("USER"), Set.of());

    private static PersonalDataAccessEntry sampleEntry(long id) {
        return new PersonalDataAccessEntry(
                id, 10L, "admin", "SUPER_ADMIN",
                VIEWER_USER_ID, "myUser",
                List.of("email", "name"),
                "USER_INQUIRY",
                "127.0.0.1", "Mozilla/5.0",
                Instant.now()
        );
    }

    @Test
    @DisplayName("GET /api/v1/me/personal-data-access — 본인 이력 200 OK + 페이지 응답")
    void myAccess_returnsOkWithPagedResponse() throws Exception {
        PageResponse<PersonalDataAccessEntry> page = PageResponse.of(
                List.of(sampleEntry(1L), sampleEntry(2L)), 0, 20, 2L);
        when(service.findByTarget(eq(VIEWER_USER_ID), eq(0), eq(20))).thenReturn(page);

        mockMvc.perform(get("/api/v1/me/personal-data-access")
                        .with(SecurityMockMvcRequestPostProcessors
                                .authentication(jwtAuth(MY_PRINCIPAL))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].targetUserId").value(VIEWER_USER_ID));
    }

    @Test
    @DisplayName("GET /api/v1/me/personal-data-access — page/size 파라미터 전달 확인")
    void myAccess_passesPaginationParameters() throws Exception {
        PageResponse<PersonalDataAccessEntry> page = PageResponse.of(
                List.of(sampleEntry(3L)), 1, 10, 11L);
        when(service.findByTarget(eq(VIEWER_USER_ID), eq(1), eq(10))).thenReturn(page);

        mockMvc.perform(get("/api/v1/me/personal-data-access")
                        .param("page", "1")
                        .param("size", "10")
                        .with(SecurityMockMvcRequestPostProcessors
                                .authentication(jwtAuth(MY_PRINCIPAL))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(11));

        verify(service).findByTarget(VIEWER_USER_ID, 1, 10);
    }

    @Test
    @DisplayName("GET /api/v1/me/personal-data-access — 빈 이력 200 OK")
    void myAccess_returnsOkWithEmptyContent() throws Exception {
        PageResponse<PersonalDataAccessEntry> empty = PageResponse.of(List.of(), 0, 20, 0L);
        when(service.findByTarget(eq(VIEWER_USER_ID), eq(0), eq(20))).thenReturn(empty);

        mockMvc.perform(get("/api/v1/me/personal-data-access")
                        .with(SecurityMockMvcRequestPostProcessors
                                .authentication(jwtAuth(MY_PRINCIPAL))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("GET /api/v1/me/personal-data-access — principal.userId만 서비스로 전달")
    void myAccess_usesPrincipalUserIdNotPathParameter() throws Exception {
        long otherUserId = 50L;
        JwtPrincipal otherPrincipal = new JwtPrincipal(otherUserId, "otherUser", Set.of("USER"), Set.of());

        PageResponse<PersonalDataAccessEntry> page = PageResponse.of(
                List.of(), 0, 20, 0L);
        when(service.findByTarget(eq(otherUserId), eq(0), eq(20))).thenReturn(page);

        mockMvc.perform(get("/api/v1/me/personal-data-access")
                        .with(SecurityMockMvcRequestPostProcessors
                                .authentication(jwtAuth(otherPrincipal))))
                .andExpect(status().isOk());

        verify(service).findByTarget(otherUserId, 0, 20);
    }

    // ─── 헬퍼 ───────────────────────────────────────────────────

    private org.springframework.security.authentication.UsernamePasswordAuthenticationToken jwtAuth(
            JwtPrincipal principal) {
        return new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                principal, null,
                principal.roles().stream()
                        .map(r -> new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + r))
                        .toList()
        );
    }
}
