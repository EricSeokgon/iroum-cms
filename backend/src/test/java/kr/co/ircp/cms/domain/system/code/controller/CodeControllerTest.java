package kr.co.ircp.cms.domain.system.code.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.ircp.cms.config.GlobalExceptionHandler;
import kr.co.ircp.cms.domain.system.code.dto.BulkCodesResponse;
import kr.co.ircp.cms.domain.system.code.dto.CodeRequest;
import kr.co.ircp.cms.domain.system.code.dto.CodeResponse;
import kr.co.ircp.cms.domain.system.code.service.CodeService;
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
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
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
 * CodeController GREEN 단계 테스트.
 *
 * <p>SPEC-CMS-005 REQ-SYSTEM-004-D: 공통코드 CRUD + 그룹별 조회 + 벌크 조회 HTTP 계층 검증.
 */
@WebMvcTest(CodeController.class)
@ImportAutoConfiguration(exclude = {SecurityAutoConfiguration.class})
@Import({GlobalExceptionHandler.class, kr.co.ircp.cms.support.WebMvcTestInfraConfig.class})
@DisplayName("CodeController GREEN 테스트 (REQ-SYSTEM-004-D)")
class CodeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CodeService codeService;

    private static CodeResponse sample(Long id, String groupCode, String code, String name) {
        return CodeResponse.builder()
                .id(id)
                .groupCode(groupCode)
                .code(code)
                .name(name)
                .description("설명")
                .sortOrder(1)
                .status("ACTIVE")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    @WithMockUser(authorities = {"SYSTEM:CODE:READ"})
    @DisplayName("GET /codes?groupCode=GENDER — 그룹별 코드 목록 200 OK")
    void listByGroup_returnsOkWithCodes() throws Exception {
        when(codeService.listByGroup(eq("GENDER"))).thenReturn(List.of(
                sample(1L, "GENDER", "M", "남성"),
                sample(2L, "GENDER", "F", "여성")
        ));

        mockMvc.perform(get("/api/v1/system/codes").param("groupCode", "GENDER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].code").value("M"))
                .andExpect(jsonPath("$[1].name").value("여성"));
    }

    @Test
    @WithMockUser(authorities = {"SYSTEM:CODE:READ"})
    @DisplayName("GET /codes/bulk?groups=A,B — 다중 그룹 벌크 조회 200 OK")
    void bulk_returnsOkWithGroupedCodes() throws Exception {
        BulkCodesResponse bulk = new BulkCodesResponse(Map.of(
                "GENDER", List.of(sample(1L, "GENDER", "M", "남성")),
                "STATUS", List.of(sample(2L, "STATUS", "A", "활성"))
        ));
        when(codeService.bulkByGroups(anyList())).thenReturn(bulk);

        mockMvc.perform(get("/api/v1/system/codes/bulk").param("groups", "GENDER", "STATUS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codes.GENDER[0].code").value("M"))
                .andExpect(jsonPath("$.codes.STATUS[0].code").value("A"));
    }

    @Test
    @WithMockUser(authorities = {"SYSTEM:CODE:READ"})
    @DisplayName("GET /codes/{id} — 단건 조회 200 OK")
    void get_returnsOkWithCode() throws Exception {
        when(codeService.getById(eq(7L))).thenReturn(sample(7L, "GENDER", "M", "남성"));

        mockMvc.perform(get("/api/v1/system/codes/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.groupCode").value("GENDER"));
    }

    @Test
    @WithMockUser(authorities = {"SYSTEM:CODE:WRITE"})
    @DisplayName("POST /codes — 코드 생성 201 Created")
    void create_returnsCreatedWithBody() throws Exception {
        CodeRequest req = new CodeRequest("GENDER", "M", "남성", "남성 코드", 1, null);
        when(codeService.create(any(CodeRequest.class))).thenReturn(sample(10L, "GENDER", "M", "남성"));

        mockMvc.perform(post("/api/v1/system/codes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.code").value("M"));
    }

    @Test
    @WithMockUser(authorities = {"SYSTEM:CODE:WRITE"})
    @DisplayName("POST /codes — 필수 필드 누락 시 400 Bad Request")
    void create_invalidRequest_returns400() throws Exception {
        // groupCode, code, name 모두 누락 → @NotBlank 위반
        String invalidJson = "{}";

        mockMvc.perform(post("/api/v1/system/codes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = {"SYSTEM:CODE:WRITE"})
    @DisplayName("PUT /codes/{id} — 코드 수정 200 OK")
    void update_returnsOkWithUpdatedCode() throws Exception {
        CodeRequest req = new CodeRequest("GENDER", "M", "Male", "남성 (영문)", 1, null);
        when(codeService.update(eq(5L), any(CodeRequest.class)))
                .thenReturn(sample(5L, "GENDER", "M", "Male"));

        mockMvc.perform(put("/api/v1/system/codes/5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.name").value("Male"));
    }

    @Test
    @WithMockUser(authorities = {"SYSTEM:CODE:WRITE"})
    @DisplayName("DELETE /codes/{id} — 코드 삭제 204 No Content")
    void delete_returnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/v1/system/codes/3"))
                .andExpect(status().isNoContent());

        verify(codeService).delete(3L);
    }

    @Test
    @DisplayName("POST /codes — 인증 없이 접근 시 403 Forbidden")
    void createCode_unauthenticated_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/system/codes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"groupCode\":\"G1\",\"code\":\"C1\",\"name\":\"코드\",\"sortOrder\":1}"))
                .andExpect(status().isForbidden());
    }
}
