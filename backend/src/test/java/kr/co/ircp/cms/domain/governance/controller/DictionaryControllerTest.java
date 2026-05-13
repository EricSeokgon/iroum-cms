package kr.co.ircp.cms.domain.governance.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.ircp.cms.config.GlobalExceptionHandler;
import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.governance.dto.DictionaryRequest;
import kr.co.ircp.cms.domain.governance.entity.DataDictionary;
import kr.co.ircp.cms.domain.governance.service.DataDictionaryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * DictionaryController GREEN 단계 테스트.
 *
 * <p>SPEC-CMS-009 REQ-GOV-001~005: 데이터 표준 사전 CRUD + Export + Freshness HTTP 계층 검증.
 */
@WebMvcTest(DictionaryController.class)
@ImportAutoConfiguration(exclude = {SecurityAutoConfiguration.class})
@Import({GlobalExceptionHandler.class, kr.co.ircp.cms.support.WebMvcTestInfraConfig.class})
@DisplayName("DictionaryController GREEN 테스트 (REQ-GOV-001~005)")
class DictionaryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private DataDictionaryService service;

    private static DataDictionary sampleEntry(Long id, String table, String col) {
        return DataDictionary.builder()
                .id(id)
                .tableName(table)
                .columnName(col)
                .logicalNameKo("회원 식별자")
                .logicalNameEn("user id")
                .dataDomain("MASTER")
                .dataType("BIGINT")
                .description("회원 PK")
                .isPii(false)
                .isRequired(true)
                .status("ACTIVE")
                .build();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("GET /dictionary — 목록 조회 200 OK + 페이지 응답")
    void list_returnsOk() throws Exception {
        // given
        when(service.findFiltered(any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(PageResponse.of(
                        List.of(sampleEntry(1L, "user", "id"), sampleEntry(2L, "user", "email")),
                        0, 20, 2L));

        // when & then
        mockMvc.perform(get("/api/v1/governance/dictionary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].tableName").value("user"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("GET /dictionary/{id} — 단건 조회 200 OK + history 포함")
    void get_existingId_returnsOk() throws Exception {
        // given
        when(service.findById(eq(7L))).thenReturn(Optional.of(sampleEntry(7L, "user", "id")));
        when(service.findHistory(eq(7L))).thenReturn(List.of());

        // when & then
        mockMvc.perform(get("/api/v1/governance/dictionary/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.tableName").value("user"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("GET /dictionary/{id} — 미존재 시 404 Not Found")
    void get_missingId_returns404() throws Exception {
        when(service.findById(eq(999L))).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/governance/dictionary/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("POST /dictionary — 생성 201 Created + Location 헤더")
    void create_returnsCreated() throws Exception {
        // given
        DictionaryRequest req = new DictionaryRequest(
                "user", "email", "이메일", "email", "MASTER", "VARCHAR(255)",
                "회원 이메일", true, true, "ACTIVE");
        when(service.create(any(DataDictionary.class)))
                .thenReturn(sampleEntry(101L, "user", "email"));

        // when & then
        mockMvc.perform(post("/api/v1/governance/dictionary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(101));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("PUT /dictionary/{id} — 수정 200 OK")
    void update_returnsOk() throws Exception {
        // given
        DictionaryRequest req = new DictionaryRequest(
                "user", "email", "회원 이메일(수정)", "email", "MASTER", "VARCHAR(320)",
                "회원 이메일", true, true, "ACTIVE");
        when(service.update(any(DataDictionary.class), any()))
                .thenReturn(sampleEntry(50L, "user", "email"));

        // when & then
        mockMvc.perform(put("/api/v1/governance/dictionary/50")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(50));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("DELETE /dictionary/{id} — 소프트 삭제 204 No Content")
    void softDelete_existing_returns204() throws Exception {
        when(service.softDelete(eq(20L))).thenReturn(true);

        mockMvc.perform(delete("/api/v1/governance/dictionary/20"))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("DELETE /dictionary/{id} — 미존재 시 404 Not Found")
    void softDelete_missing_returns404() throws Exception {
        when(service.softDelete(eq(404L))).thenReturn(false);

        mockMvc.perform(delete("/api/v1/governance/dictionary/404"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("GET /dictionary/export — CSV 다운로드 200 OK + Content-Type=text/csv")
    void export_csv_returnsOkWithCsvContentType() throws Exception {
        // given
        byte[] csv = "테이블명,컬럼명\nuser,id\n".getBytes();
        when(service.exportDictionary(eq("csv"))).thenReturn(csv);

        // when & then
        mockMvc.perform(get("/api/v1/governance/dictionary/export")
                        .param("format", "csv"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        org.hamcrest.Matchers.containsString("data_dictionary.csv")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("GET /dictionary/export — XLSX 다운로드 200 OK + xlsx Content-Type")
    void export_xlsx_returnsOkWithXlsxContentType() throws Exception {
        // given
        byte[] xlsx = new byte[]{0x50, 0x4B, 0x03, 0x04};   // ZIP magic
        when(service.exportDictionary(eq("xlsx"))).thenReturn(xlsx);

        // when & then
        mockMvc.perform(get("/api/v1/governance/dictionary/export")
                        .param("format", "xlsx"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        org.hamcrest.Matchers.containsString("data_dictionary.xlsx")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("GET /dictionary/freshness — 스키마 비교 결과 200 OK")
    void freshness_returnsOkWithDiff() throws Exception {
        // given
        Map<String, Object> diff = Map.of(
                "registeredCount", 120,
                "actualCount", 130,
                "missingInDictionary", 10,
                "missingSamples", List.of("user.deleted_at", "post.scheduled_at")
        );
        when(service.compareWithSchema()).thenReturn(diff);

        // when & then
        mockMvc.perform(get("/api/v1/governance/dictionary/freshness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registeredCount").value(120))
                .andExpect(jsonPath("$.missingInDictionary").value(10))
                .andExpect(jsonPath("$.missingSamples.length()").value(2));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("POST /dictionary — 필수값 누락 시 400 Bad Request")
    void create_missingRequired_returns400() throws Exception {
        // given — tableName, columnName 등 필수값 누락
        String invalidJson = "{\"description\":\"필수값 없음\"}";

        // when & then
        mockMvc.perform(post("/api/v1/governance/dictionary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());
    }

    // ──────────────────────────────────────────────────────────────
    // SPEC-CMS-SECURITY-CTRL-AUTHZ-COVERAGE-001 — 권한 거부 시나리오
    // 클래스 레벨 @PreAuthorize("hasRole('ADMIN')") 정책 검증
    // ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("AC-COV-001-1 — GET /dictionary 인증 없이 접근 시 403 Forbidden (@WebMvcTest 한계)")
    void list_returns403_withoutAuthentication() throws Exception {
        // @WebMvcTest + SecurityAutoConfiguration 제외 → SecurityFilterChain 없음 → @PreAuthorize 거부 → 403
        // 401 검증은 SecurityConfig 통합 테스트에서 별도 (REQ-IRR-003).
        mockMvc.perform(get("/api/v1/governance/dictionary"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = {"WRONG_AUTHORITY"})
    @DisplayName("AC-COV-001-2 — GET /dictionary 권한 부족 시 403 Forbidden")
    void list_returns403_withInsufficientAuthority() throws Exception {
        // given: WRONG_AUTHORITY는 ROLE_ADMIN 정책 미충족
        // when & then: @PreAuthorize 거부 → AccessDeniedHandler → 403
        mockMvc.perform(get("/api/v1/governance/dictionary"))
                .andExpect(status().isForbidden());
    }
}
