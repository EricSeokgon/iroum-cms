package kr.co.ircp.cms.domain.content.menu.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.ircp.cms.config.GlobalExceptionHandler;
import kr.co.ircp.cms.domain.content.menu.dto.MenuMoveRequest;
import kr.co.ircp.cms.domain.content.menu.dto.MenuOrderRequest;
import kr.co.ircp.cms.domain.content.menu.dto.MenuPermissionRequest;
import kr.co.ircp.cms.domain.content.menu.dto.MenuRequest;
import kr.co.ircp.cms.domain.content.menu.dto.MenuResponse;
import kr.co.ircp.cms.domain.content.menu.dto.MenuTreeNode;
import kr.co.ircp.cms.domain.content.menu.service.MenuService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MenuController GREEN 단계 테스트.
 *
 * <p>SPEC-CMS-004 REQ-CONTENT-001-D / REQ-CONTENT-002-D: 메뉴 트리 CRUD + 권한 매핑 HTTP 계층 검증.
 */
@WebMvcTest(MenuController.class)
@ImportAutoConfiguration(exclude = {SecurityAutoConfiguration.class})
@Import({GlobalExceptionHandler.class, kr.co.ircp.cms.support.WebMvcTestInfraConfig.class})
@DisplayName("MenuController GREEN 테스트 (REQ-CONTENT-001-D, REQ-CONTENT-002-D)")
class MenuControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private MenuService menuService;

    private static MenuResponse sampleMenu(Long id, String code) {
        return new MenuResponse(
                id, 1L, null, code, "메뉴-" + code, "/menus/" + code, "_self",
                "icon", 1, (short) 1, "/" + id, true, "ACTIVE"
        );
    }

    @Test
    @WithMockUser(authorities = {"MENU:WRITE"})
    @DisplayName("POST /menus — 생성 201 Created + Location")
    void createMenu_returnsCreated() throws Exception {
        MenuRequest req = new MenuRequest(
                1L, null, "M001", "메뉴1", "/about", "_self",
                "icon", 1, true, null
        );
        when(menuService.createMenu(any(MenuRequest.class))).thenReturn(sampleMenu(10L, "M001"));

        mockMvc.perform(post("/api/v1/content/menus")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.code").value("M001"));
    }

    @Test
    @WithMockUser(authorities = {"MENU:WRITE"})
    @DisplayName("POST /menus — target 패턴 위반 시 400 Bad Request")
    void createMenu_invalidTarget_returns400() throws Exception {
        // target은 _self|_blank만 허용
        String invalidJson = "{\"siteId\":1,\"code\":\"M001\",\"name\":\"메뉴\",\"target\":\"_invalid\",\"sortOrder\":1,\"isVisible\":true}";

        mockMvc.perform(post("/api/v1/content/menus")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /menus/tree — 메뉴 트리 조회 200 OK")
    void getMenuTree_returnsOk() throws Exception {
        MenuTreeNode child = new MenuTreeNode(
                2L, 1L, "M002", "자식", "/c", "_self", null,
                1, (short) 2, "/1/2", true, true, List.of()
        );
        MenuTreeNode root = new MenuTreeNode(
                1L, null, "M001", "루트", "/r", "_self", null,
                1, (short) 1, "/1", true, true, List.of(child)
        );
        when(menuService.getMenuTree(eq(1L))).thenReturn(List.of(root));

        mockMvc.perform(get("/api/v1/content/menus/tree").param("siteId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].children[0].id").value(2));
    }

    @Test
    @WithMockUser(authorities = {"MENU:WRITE"})
    @DisplayName("PATCH /menus/{id}/order — 순서 변경 200 OK")
    void changeOrder_returnsOk() throws Exception {
        MenuOrderRequest req = new MenuOrderRequest(5);
        when(menuService.changeOrder(eq(3L), any(MenuOrderRequest.class)))
                .thenReturn(sampleMenu(3L, "M003"));

        mockMvc.perform(patch("/api/v1/content/menus/3/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(3));
    }

    @Test
    @WithMockUser(authorities = {"MENU:WRITE"})
    @DisplayName("PATCH /menus/{id}/move — 메뉴 이동 200 OK")
    void moveMenu_returnsOk() throws Exception {
        MenuMoveRequest req = new MenuMoveRequest(2L);
        when(menuService.moveMenu(eq(3L), any(MenuMoveRequest.class)))
                .thenReturn(sampleMenu(3L, "M003"));

        mockMvc.perform(patch("/api/v1/content/menus/3/move")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(3));
    }

    @Test
    @WithMockUser(authorities = {"MENU:WRITE"})
    @DisplayName("PATCH /menus/{id}/visibility — 가시성 토글 200 OK")
    void toggleVisibility_returnsOk() throws Exception {
        when(menuService.toggleVisibility(eq(3L), anyBoolean()))
                .thenReturn(sampleMenu(3L, "M003"));

        mockMvc.perform(patch("/api/v1/content/menus/3/visibility")
                        .param("isVisible", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(3));
    }

    @Test
    @WithMockUser(authorities = {"MENU:WRITE"})
    @DisplayName("DELETE /menus/{id} — 삭제 204 No Content")
    void deleteMenu_returnsNoContent() throws Exception {
        doNothing().when(menuService).deleteMenu(eq(3L));

        mockMvc.perform(delete("/api/v1/content/menus/3"))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(authorities = {"MENU:PERMISSION:WRITE"})
    @DisplayName("POST /menus/{id}/permissions — 권한 일괄 저장 204 No Content")
    void replacePermissions_returnsNoContent() throws Exception {
        MenuPermissionRequest req = new MenuPermissionRequest(List.of("ROLE_ADMIN", "ROLE_USER"));
        doNothing().when(menuService).replaceMenuPermissions(eq(3L), any(MenuPermissionRequest.class));

        mockMvc.perform(post("/api/v1/content/menus/3/permissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("DELETE /menus/{id} — 인증 없이 접근 시 403 Forbidden")
    void deleteMenu_unauthenticated_returns403() throws Exception {
        mockMvc.perform(delete("/api/v1/content/menus/3"))
                .andExpect(status().isForbidden());
    }
}
