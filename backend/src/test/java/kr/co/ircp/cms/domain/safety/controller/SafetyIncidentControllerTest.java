package kr.co.ircp.cms.domain.safety.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.ircp.cms.config.GlobalExceptionHandler;
import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.safety.dto.IncidentCreateRequest;
import kr.co.ircp.cms.domain.safety.dto.IncidentDetail;
import kr.co.ircp.cms.domain.safety.dto.IncidentSummary;
import kr.co.ircp.cms.domain.safety.dto.SyncResult;
import kr.co.ircp.cms.domain.safety.exception.SafetyIncidentNotFoundException;
import kr.co.ircp.cms.domain.safety.service.SafetyIncidentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SafetyIncidentController @WebMvcTest.
 * REQ-SAFETY-001
 */
@WebMvcTest(SafetyIncidentController.class)
@ImportAutoConfiguration(exclude = {SecurityAutoConfiguration.class})
@Import({GlobalExceptionHandler.class, kr.co.ircp.cms.support.WebMvcTestInfraConfig.class})
@DisplayName("SafetyIncidentController — REQ-SAFETY-001")
class SafetyIncidentControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private SafetyIncidentService incidentService;
    @Autowired private ObjectMapper objectMapper;

    @Test
    @DisplayName("GET /api/v1/safety/incidents — 200 OK")
    void list_returns200() throws Exception {
        IncidentSummary item = new IncidentSummary(
                1L, "MANUAL", "F4521", "FALL", "FATAL",
                Instant.now(), 1, "현장A", "요약", "PUBLISHED"
        );
        PageResponse<IncidentSummary> page = PageResponse.of(List.of(item), 0, 20, 1L);
        when(incidentService.listIncidents(any(), any(), any(), anyInt(), anyInt())).thenReturn(page);

        mockMvc.perform(get("/api/v1/safety/incidents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/safety/incidents/{id} — 미존재 시 404")
    void get_notFound_returns404() throws Exception {
        when(incidentService.getIncident(eq(99L)))
                .thenThrow(new SafetyIncidentNotFoundException(99L));

        mockMvc.perform(get("/api/v1/safety/incidents/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /api/v1/safety/admin/incidents — 201 Created")
    void create_returns201() throws Exception {
        IncidentDetail created = new IncidentDetail(
                10L, "MANUAL", "F4521", null, null, "FALL",
                Instant.now(), "FATAL", 1, "현장A", "요약",
                null, null, null, "PUBLISHED", Instant.now(), Instant.now()
        );
        when(incidentService.createIncident(any())).thenReturn(created);

        IncidentCreateRequest request = new IncidentCreateRequest(
                "MANUAL", "F4521", null, null, "FALL",
                Instant.now(), "FATAL", 1, "현장A", "요약", null, null, null
        );

        mockMvc.perform(post("/api/v1/safety/admin/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10));
    }

    @Test
    @DisplayName("POST /api/v1/safety/admin/incidents/sync — 200 OK SyncResult")
    void sync_returns200() throws Exception {
        when(incidentService.triggerExternalSync(anyString()))
                .thenReturn(new SyncResult(0, 0, 0, "mock"));

        mockMvc.perform(post("/api/v1/safety/admin/incidents/sync"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("mock"));
    }

    @Test
    @DisplayName("DELETE /api/v1/safety/admin/incidents/{id} — 204")
    void archive_returns204() throws Exception {
        mockMvc.perform(delete("/api/v1/safety/admin/incidents/1"))
                .andExpect(status().isNoContent());
    }
}
