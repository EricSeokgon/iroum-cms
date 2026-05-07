package kr.co.ircp.cms.domain.system.code.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.ircp.cms.config.GlobalExceptionHandler;
import kr.co.ircp.cms.domain.system.code.dto.CodeGroupRequest;
import kr.co.ircp.cms.domain.system.code.dto.CodeGroupResponse;
import kr.co.ircp.cms.domain.system.code.service.CodeGroupService;
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

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CodeGroupController GREEN 단계 테스트.
 *
 * <p>SPEC-CMS-005 REQ-SYSTEM-004-D: 공통코드 그룹 CRUD HTTP 계층 검증.
 */
@WebMvcTest(CodeGroupController.class)
@ImportAutoConfiguration(exclude = {SecurityAutoConfiguration.class})
@Import({GlobalExceptionHandler.class, kr.co.ircp.cms.support.WebMvcTestInfraConfig.class})
@DisplayName("CodeGroupController GREEN 테스트 (REQ-SYSTEM-004-D)")
class CodeGroupControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CodeGroupService codeGroupService;

    private static CodeGroupResponse sample(Long id, String groupCode, String name) {
        return CodeGroupResponse.builder()
                .id(id)
                .groupCode(groupCode)
                .name(name)
                .description("그룹 설명")
                .status("ACTIVE")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    @WithMockUser(authorities = {"SYSTEM:CODE:READ"})
    @DisplayName("GET /code-groups — 목록 조회 200 OK")
    void list_returnsOkWithGroups() throws Exception {
        when(codeGroupService.listAll()).thenReturn(List.of(
                sample(1L, "GENDER", "성별"),
                sample(2L, "STATUS", "상태")
        ));

        mockMvc.perform(get("/api/v1/system/code-groups"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].groupCode").value("GENDER"))
                .andExpect(jsonPath("$[1].name").value("상태"));
    }

    @Test
    @WithMockUser(authorities = {"SYSTEM:CODE:READ"})
    @DisplayName("GET /code-groups/{id} — 단건 조회 200 OK")
    void get_returnsOkWithGroup() throws Exception {
        when(codeGroupService.getById(eq(7L))).thenReturn(sample(7L, "GENDER", "성별"));

        mockMvc.perform(get("/api/v1/system/code-groups/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.groupCode").value("GENDER"));
    }

    @Test
    @WithMockUser(authorities = {"SYSTEM:CODE:WRITE"})
    @DisplayName("POST /code-groups — 그룹 생성 201 Created")
    void create_returnsCreatedWithBody() throws Exception {
        CodeGroupRequest req = new CodeGroupRequest("GENDER", "성별", "성별 코드 그룹");
        when(codeGroupService.create(any(CodeGroupRequest.class)))
                .thenReturn(sample(10L, "GENDER", "성별"));

        mockMvc.perform(post("/api/v1/system/code-groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.groupCode").value("GENDER"));
    }

    @Test
    @WithMockUser(authorities = {"SYSTEM:CODE:WRITE"})
    @DisplayName("POST /code-groups — groupCode 누락 시 400 Bad Request")
    void create_invalidRequest_returns400() throws Exception {
        // groupCode, name 모두 누락 → @NotBlank 위반
        String invalidJson = "{}";

        mockMvc.perform(post("/api/v1/system/code-groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = {"SYSTEM:CODE:WRITE"})
    @DisplayName("PUT /code-groups/{id} — 그룹 수정 200 OK")
    void update_returnsOkWithUpdatedGroup() throws Exception {
        CodeGroupRequest req = new CodeGroupRequest("GENDER", "성별 (변경)", "변경된 설명");
        when(codeGroupService.update(eq(5L), any(CodeGroupRequest.class)))
                .thenReturn(sample(5L, "GENDER", "성별 (변경)"));

        mockMvc.perform(put("/api/v1/system/code-groups/5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.name").value("성별 (변경)"));
    }

    @Test
    @WithMockUser(authorities = {"SYSTEM:CODE:WRITE"})
    @DisplayName("DELETE /code-groups/{id} — 그룹 삭제 204 No Content")
    void delete_returnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/v1/system/code-groups/3"))
                .andExpect(status().isNoContent());

        verify(codeGroupService).delete(3L);
    }

    @Test
    @DisplayName("POST /code-groups — 인증 없이 접근 시 403 Forbidden")
    void createCodeGroup_unauthenticated_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/system/code-groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"groupCode\":\"G1\",\"name\":\"그룹\"}"))
                .andExpect(status().isForbidden());
    }
}
