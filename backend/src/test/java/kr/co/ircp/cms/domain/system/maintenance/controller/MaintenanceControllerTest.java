package kr.co.ircp.cms.domain.system.maintenance.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.ircp.cms.config.GlobalExceptionHandler;
import kr.co.ircp.cms.domain.system.maintenance.dto.MaintenanceRequest;
import kr.co.ircp.cms.domain.system.maintenance.dto.MaintenanceResponse;
import kr.co.ircp.cms.domain.system.maintenance.service.MaintenanceService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MaintenanceController GREEN 단계 테스트.
 *
 * <p>SPEC-CMS-005 REQ-SYSTEM-005-D: 점검 모드 CRUD + 즉시 활성화 HTTP 계층 검증.
 */
@WebMvcTest(MaintenanceController.class)
@ImportAutoConfiguration(exclude = {SecurityAutoConfiguration.class})
@Import({GlobalExceptionHandler.class, kr.co.ircp.cms.support.WebMvcTestInfraConfig.class})
@DisplayName("MaintenanceController GREEN 테스트 (REQ-SYSTEM-005-D)")
class MaintenanceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // MaintenanceService는 WebMvcTestInfraConfig에서 @MockBean으로 등록됨 — 동일 빈 주입받아 stubbing
    @Autowired
    private MaintenanceService maintenanceServiceForController;

    private static MaintenanceResponse sample(Long id, String status) {
        return MaintenanceResponse.builder()
                .id(id)
                .title("정기 점검")
                .messageKo("점검 중입니다")
                .messageEn("Maintenance in progress")
                .startAt(Instant.now())
                .endAt(Instant.now().plus(2, ChronoUnit.HOURS))
                .status(status)
                .allowAdminAccess(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    @WithMockUser(authorities = {"SYSTEM:MAINT:READ"})
    @DisplayName("GET /maintenance — 목록 조회 200 OK + 응답 배열")
    void list_returnsOkWithMaintenances() throws Exception {
        when(maintenanceServiceForController.listAll()).thenReturn(List.of(
                sample(1L, "SCHEDULED"),
                sample(2L, "ACTIVE")
        ));

        mockMvc.perform(get("/api/v1/system/maintenance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].status").value("SCHEDULED"))
                .andExpect(jsonPath("$[1].status").value("ACTIVE"));
    }

    @Test
    @WithMockUser(authorities = {"SYSTEM:MAINT:READ"})
    @DisplayName("GET /maintenance/{id} — 단건 조회 200 OK")
    void get_returnsOkWithMaintenance() throws Exception {
        when(maintenanceServiceForController.getById(eq(7L))).thenReturn(sample(7L, "SCHEDULED"));

        mockMvc.perform(get("/api/v1/system/maintenance/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.status").value("SCHEDULED"));
    }

    @Test
    @WithMockUser(authorities = {"SYSTEM:MAINT:WRITE"})
    @DisplayName("POST /maintenance — 점검 등록 201 Created")
    void create_returnsCreatedWithBody() throws Exception {
        Instant start = Instant.now();
        Instant end = start.plus(1, ChronoUnit.HOURS);
        MaintenanceRequest req = new MaintenanceRequest(
                "긴급 점검", "점검 중", "Maintenance", start, end, true);
        when(maintenanceServiceForController.create(any(MaintenanceRequest.class)))
                .thenReturn(sample(10L, "SCHEDULED"));

        mockMvc.perform(post("/api/v1/system/maintenance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.status").value("SCHEDULED"));
    }

    @Test
    @WithMockUser(authorities = {"SYSTEM:MAINT:WRITE"})
    @DisplayName("POST /maintenance — title 누락 시 400 Bad Request")
    void create_invalidRequest_returns400() throws Exception {
        // title, startAt, endAt 모두 누락 → @NotBlank/@NotNull 위반
        String invalidJson = "{}";

        mockMvc.perform(post("/api/v1/system/maintenance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = {"SYSTEM:MAINT:WRITE"})
    @DisplayName("POST /maintenance/{id}/activate — 즉시 활성화 200 OK")
    void activate_returnsOkWithActiveStatus() throws Exception {
        when(maintenanceServiceForController.activate(eq(5L))).thenReturn(sample(5L, "ACTIVE"));

        mockMvc.perform(post("/api/v1/system/maintenance/5/activate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("POST /maintenance — 인증 없이 접근 시 403 Forbidden")
    void create_unauthenticated_returns403() throws Exception {
        Instant start = Instant.now();
        Instant end = start.plus(1, ChronoUnit.HOURS);
        MaintenanceRequest req = new MaintenanceRequest(
                "긴급 점검", "점검 중", "Maintenance", start, end, true);

        mockMvc.perform(post("/api/v1/system/maintenance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }
}
