package kr.co.ircp.cms.domain.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.ircp.cms.config.GlobalExceptionHandler;
import kr.co.ircp.cms.domain.auth.dto.AssignOrganizationRequest;
import kr.co.ircp.cms.domain.auth.dto.OrganizationCreateRequest;
import kr.co.ircp.cms.domain.auth.dto.OrganizationDetail;
import kr.co.ircp.cms.domain.auth.dto.OrganizationSummary;
import kr.co.ircp.cms.domain.auth.dto.OrganizationTreeNode;
import kr.co.ircp.cms.domain.auth.exception.DuplicateOrganizationCodeException;
import kr.co.ircp.cms.domain.auth.exception.OrganizationHasChildrenException;
import kr.co.ircp.cms.domain.auth.security.JwtPrincipal;
import kr.co.ircp.cms.domain.auth.service.OrganizationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * OrganizationController @WebMvcTest (GREEN 단계).
 *
 * <p>SPEC-CMS-002 REQ-AUTH-014 — HTTP 계층(상태 코드, JSON 구조) 검증. 6개 테스트.
 * Security는 비활성화 후 JwtPrincipal을 직접 주입.
 */
@WebMvcTest(OrganizationController.class)
@ImportAutoConfiguration(exclude = {SecurityAutoConfiguration.class})
@Import({GlobalExceptionHandler.class, kr.co.ircp.cms.support.WebMvcTestInfraConfig.class})
@DisplayName("OrganizationController GREEN 단계 테스트")
class OrganizationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrganizationService orgService;

    @Autowired
    private ObjectMapper objectMapper;

    private static final JwtPrincipal ADMIN_PRINCIPAL =
            new JwtPrincipal(1L, "1", Set.of("SUPER_ADMIN"));

    @Test
    @DisplayName("GET /api/v1/organizations/tree — 200 OK 트리 반환")
    void getTree_returns200() throws Exception {
        OrganizationTreeNode root = new OrganizationTreeNode(1L, "ROOT", "본부", 0, 0, "ACTIVE", List.of());
        when(orgService.getTree()).thenReturn(List.of(root));

        mockMvc.perform(get("/api/v1/organizations/tree")
                        .with(SecurityMockMvcRequestPostProcessors.user(ADMIN_PRINCIPAL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("ROOT"))
                .andExpect(jsonPath("$[0].depth").value(0));
    }

    @Test
    @DisplayName("GET /api/v1/organizations/tree — 관리자 역할 없으면 403")
    void getTree_returns403_whenNoAdminRole() throws Exception {
        // EnableMethodSecurity 활성화 후 @PreAuthorize 검증이 동작하므로
        // VIEWER 역할만 가진 사용자는 SUPER_ADMIN/DEPT_ADMIN 요구를 만족하지 못해 403 반환
        JwtPrincipal viewer = new JwtPrincipal(2L, "2", Set.of("VIEWER"));

        mockMvc.perform(get("/api/v1/organizations/tree")
                        .with(SecurityMockMvcRequestPostProcessors.user(viewer)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/v1/organizations — 생성 성공 시 201 Created")
    void create_returns201() throws Exception {
        OrganizationCreateRequest req = new OrganizationCreateRequest("DEPT_A", "개발팀", null, null, 0);
        OrganizationDetail detail = new OrganizationDetail(
                10L, "DEPT_A", "개발팀", null, null, 0, "/10/", 0, "ACTIVE",
                Instant.now(), Instant.now());

        when(orgService.create(any(OrganizationCreateRequest.class), anyLong())).thenReturn(detail);

        mockMvc.perform(post("/api/v1/organizations")
                        .with(SecurityMockMvcRequestPostProcessors.user(ADMIN_PRINCIPAL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("DEPT_A"))
                .andExpect(jsonPath("$.id").value(10));
    }

    @Test
    @DisplayName("POST /api/v1/organizations — 코드 중복 시 409 Conflict")
    void create_returns409_onDuplicateCode() throws Exception {
        OrganizationCreateRequest req = new OrganizationCreateRequest("ROOT", "루트", null, null, 0);
        when(orgService.create(any(), anyLong()))
                .thenThrow(new DuplicateOrganizationCodeException("ROOT"));

        mockMvc.perform(post("/api/v1/organizations")
                        .with(SecurityMockMvcRequestPostProcessors.user(ADMIN_PRINCIPAL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORG_CODE_DUPLICATE"));
    }

    @Test
    @DisplayName("DELETE /api/v1/organizations/{id} — 자식 존재 시 409 Conflict")
    void delete_returns409_whenHasChildren() throws Exception {
        doThrow(new OrganizationHasChildrenException(1L))
                .when(orgService).delete(anyLong(), anyLong());

        mockMvc.perform(delete("/api/v1/organizations/1")
                        .with(SecurityMockMvcRequestPostProcessors.user(ADMIN_PRINCIPAL)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORG_HAS_CHILDREN"));
    }

    @Test
    @DisplayName("POST /api/v1/organizations/users/{userId}/organization — 200 OK")
    void assignUser_returns200() throws Exception {
        AssignOrganizationRequest req = new AssignOrganizationRequest(1L);

        mockMvc.perform(post("/api/v1/organizations/users/42/organization")
                        .with(SecurityMockMvcRequestPostProcessors.user(ADMIN_PRINCIPAL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }
}
