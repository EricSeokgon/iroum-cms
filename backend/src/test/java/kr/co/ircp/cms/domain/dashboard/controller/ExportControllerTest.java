package kr.co.ircp.cms.domain.dashboard.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.ircp.cms.config.GlobalExceptionHandler;
import kr.co.ircp.cms.domain.dashboard.dto.ExportRequest;
import kr.co.ircp.cms.domain.dashboard.dto.ExportResponse;
import kr.co.ircp.cms.domain.dashboard.entity.ExportHistory;
import kr.co.ircp.cms.domain.dashboard.service.ExportService;
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
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ExportController GREEN 단계 테스트.
 *
 * <p>SPEC-CMS-008 REQ-DASHBOARD-006 (REQ-VIZ-006):
 * 내보내기 (Excel/CSV/PDF) 생성 / 상태조회 / 다운로드 / 이력 HTTP 계층 검증.
 */
@WebMvcTest(ExportController.class)
@ImportAutoConfiguration(exclude = {SecurityAutoConfiguration.class})
@Import({GlobalExceptionHandler.class, kr.co.ircp.cms.support.WebMvcTestInfraConfig.class})
@DisplayName("ExportController GREEN 테스트 (REQ-DASHBOARD-006)")
class ExportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ExportService service;

    private static ExportResponse sample(Long id, String type, String statusCode) {
        Instant now = Instant.now();
        return new ExportResponse(
                id, 1L, type, "{\"dashboard_id\":1}",
                "/exports/" + id + "." + type.toLowerCase(),
                1024L, 50, statusCode, 100, null,
                now, now, now.plus(7, ChronoUnit.DAYS),
                "https://example.com/dl/" + id + "?sig=abc"
        );
    }

    @Test
    @WithMockUser(authorities = {"DASHBOARD:EXPORT:WRITE"})
    @DisplayName("POST /dashboard/export — 동기 완료 시 200 OK + body")
    void create_completed_returnsOk() throws Exception {
        ExportRequest req = new ExportRequest("CSV", "{\"dashboard_id\":1}", false);
        when(service.createExport(any(), any(ExportRequest.class)))
                .thenReturn(sample(10L, "CSV", "COMPLETED"));

        mockMvc.perform(post("/api/v1/dashboard/export")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.exportType").value("CSV"));
    }

    @Test
    @WithMockUser(authorities = {"DASHBOARD:EXPORT:WRITE"})
    @DisplayName("POST /dashboard/export — 비동기 PROCESSING 시 202 Accepted")
    void create_processing_returnsAccepted() throws Exception {
        ExportRequest req = new ExportRequest("EXCEL", "{\"dashboard_id\":1}", true);
        when(service.createExport(any(), any(ExportRequest.class)))
                .thenReturn(sample(11L, "EXCEL", "PROCESSING"));

        mockMvc.perform(post("/api/v1/dashboard/export")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").value(11))
                .andExpect(jsonPath("$.status").value("PROCESSING"));
    }

    @Test
    @WithMockUser(authorities = {"DASHBOARD:EXPORT:WRITE"})
    @DisplayName("POST /dashboard/export — exportType 누락 시 400 Bad Request")
    void create_missingExportType_returns400() throws Exception {
        // exportType(@NotBlank) + scope(@NotNull) 누락
        String invalidJson = "{}";

        mockMvc.perform(post("/api/v1/dashboard/export")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = {"DASHBOARD:EXPORT:READ"})
    @DisplayName("GET /dashboard/export/{id}/status — 진행 상태 조회 200 OK")
    void status_returnsOkWithStatus() throws Exception {
        when(service.getStatus(eq(7L), any())).thenReturn(sample(7L, "EXCEL", "COMPLETED"));

        mockMvc.perform(get("/api/v1/dashboard/export/7/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.progressPct").value(100));
    }

    @Test
    @WithMockUser(authorities = {"DASHBOARD:EXPORT:READ"})
    @DisplayName("GET /dashboard/export — 본인 이력 목록 200 OK")
    void history_returnsOkWithHistory() throws Exception {
        when(service.listHistory(any(), eq("COMPLETED"))).thenReturn(List.of(
                sample(1L, "CSV", "COMPLETED"),
                sample(2L, "EXCEL", "COMPLETED")
        ));

        mockMvc.perform(get("/api/v1/dashboard/export").param("status", "COMPLETED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[1].exportType").value("EXCEL"));
    }

    @Test
    @WithMockUser(authorities = {"DASHBOARD:EXPORT:READ"})
    @DisplayName("GET /dashboard/export/{id}/download — CSV 다운로드 200 + UTF-8 BOM")
    void download_csv_returnsOkWithBom() throws Exception {
        ExportHistory history = ExportHistory.builder()
                .id(50L)
                .requestorId(1L)
                .exportType("CSV")
                .status("COMPLETED")
                .build();
        when(service.verifyDownload(eq(50L), any(), anyBoolean(), any())).thenReturn(history);

        mockMvc.perform(get("/api/v1/dashboard/export/50/download")
                        .param("sig", "abc123"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type",
                        org.hamcrest.Matchers.containsString("text/csv")))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("export-50.csv")))
                // UTF-8 BOM 3 bytes
                .andExpect(content().bytes(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF}));
    }

    @Test
    @WithMockUser(authorities = {"DASHBOARD:EXPORT:READ"})
    @DisplayName("GET /dashboard/export/{id}/download — EXCEL 다운로드 200 + 올바른 Content-Type")
    void download_excel_returnsOkWithExcelContentType() throws Exception {
        ExportHistory history = ExportHistory.builder()
                .id(51L)
                .requestorId(1L)
                .exportType("EXCEL")
                .status("COMPLETED")
                .build();
        when(service.verifyDownload(eq(51L), any(), anyBoolean(), any())).thenReturn(history);

        mockMvc.perform(get("/api/v1/dashboard/export/51/download")
                        .param("sig", "xyz"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("export-51.xlsx")));
    }
}
