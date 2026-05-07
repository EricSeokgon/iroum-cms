package kr.co.ircp.cms.domain.governance.controller;

import kr.co.ircp.cms.config.GlobalExceptionHandler;
import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.governance.entity.BatchExecutionLog;
import kr.co.ircp.cms.domain.governance.service.BatchExecutionLogService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BatchExecutionLogController GREEN 단계 테스트.
 *
 * <p>SPEC-CMS-009 REQ-DATA-005, REQ-GOV-010: 배치 실행 이력 조회 HTTP 계층 검증.
 */
@WebMvcTest(BatchExecutionLogController.class)
@ImportAutoConfiguration(exclude = {SecurityAutoConfiguration.class})
@Import({GlobalExceptionHandler.class, kr.co.ircp.cms.support.WebMvcTestInfraConfig.class})
@DisplayName("BatchExecutionLogController GREEN 테스트 (REQ-DATA-005, REQ-GOV-010)")
class BatchExecutionLogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BatchExecutionLogService service;

    private static BatchExecutionLog sampleLog(Long id, String jobName, String status) {
        return BatchExecutionLog.builder()
                .id(id)
                .jobName(jobName)
                .jobGroup("STATS")
                .startedAt(Instant.parse("2026-04-01T03:00:00Z"))
                .finishedAt(Instant.parse("2026-04-01T03:00:42Z"))
                .durationMs(42000)
                .status(status)
                .recordsProcessed(100)
                .recordsFailed(0)
                .retryCount(0)
                .triggeredBy("SCHEDULE")
                .build();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("GET /batch-logs — 목록 조회 200 OK + 페이지 응답")
    void list_returnsOkWithPagedLogs() throws Exception {
        // given
        List<BatchExecutionLog> content = List.of(
                sampleLog(1L, "BoardStatsDailyJob", "SUCCESS"),
                sampleLog(2L, "AuditLogArchiveJob", "SUCCESS")
        );
        when(service.findFiltered(any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(PageResponse.of(content, 0, 20, 2L));

        // when & then
        mockMvc.perform(get("/api/v1/governance/batch-logs")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].jobName").value("BoardStatsDailyJob"))
                .andExpect(jsonPath("$.content[0].status").value("SUCCESS"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("GET /batch-logs — jobGroup·status 필터 적용 시 200 OK")
    void list_withFilter_returnsOk() throws Exception {
        // given
        when(service.findFiltered(eq("RETENTION"), eq("FAILURE"), any(), any(), anyInt(), anyInt()))
                .thenReturn(PageResponse.of(
                        List.of(sampleLog(99L, "AuditLogArchiveJob", "FAILURE")),
                        0, 20, 1L));

        // when & then
        mockMvc.perform(get("/api/v1/governance/batch-logs")
                        .param("jobGroup", "RETENTION")
                        .param("status", "FAILURE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].status").value("FAILURE"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("GET /batch-logs/{id} — 단건 조회 200 OK")
    void get_existingId_returnsOk() throws Exception {
        // given
        when(service.findById(eq(7L)))
                .thenReturn(Optional.of(sampleLog(7L, "BoardStatsMonthlyJob", "SUCCESS")));

        // when & then
        mockMvc.perform(get("/api/v1/governance/batch-logs/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.jobName").value("BoardStatsMonthlyJob"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("GET /batch-logs/{id} — 미존재 ID는 404 Not Found")
    void get_missingId_returns404() throws Exception {
        // given
        when(service.findById(eq(999L))).thenReturn(Optional.empty());

        // when & then
        mockMvc.perform(get("/api/v1/governance/batch-logs/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("GET /batch-logs — 빈 결과는 totalElements=0")
    void list_empty_returnsEmptyPage() throws Exception {
        // given
        when(service.findFiltered(any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(PageResponse.of(List.of(), 0, 20, 0L));

        // when & then
        mockMvc.perform(get("/api/v1/governance/batch-logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.content.length()").value(0));
    }
}
