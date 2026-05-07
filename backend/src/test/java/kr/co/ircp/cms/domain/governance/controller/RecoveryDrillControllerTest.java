package kr.co.ircp.cms.domain.governance.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.ircp.cms.config.GlobalExceptionHandler;
import kr.co.ircp.cms.domain.governance.dto.RecoveryDrillRequest;
import kr.co.ircp.cms.domain.governance.entity.RecoveryDrillLog;
import kr.co.ircp.cms.domain.governance.service.RecoveryDrillService;
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

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RecoveryDrillController GREEN 단계 테스트.
 *
 * <p>SPEC-CMS-009 REQ-GOV-011~012: 복구 시험 이력 조회·등록 HTTP 계층 검증.
 */
@WebMvcTest(RecoveryDrillController.class)
@ImportAutoConfiguration(exclude = {SecurityAutoConfiguration.class})
@Import({GlobalExceptionHandler.class, kr.co.ircp.cms.support.WebMvcTestInfraConfig.class})
@DisplayName("RecoveryDrillController GREEN 테스트 (REQ-GOV-011~012)")
class RecoveryDrillControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private RecoveryDrillService service;

    private static RecoveryDrillLog sampleLog(Long id, String drillType, String result) {
        return RecoveryDrillLog.builder()
                .id(id)
                .drillDate(LocalDate.parse("2026-04-15"))
                .drillType(drillType)
                .result(result)
                .rtoActualMin(120)
                .rpoActualMin(30)
                .rtoTargetMin(240)
                .rpoTargetMin(60)
                .performedBy(1L)
                .notes("정기 분기 시험")
                .build();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("GET /recovery-drills — 목록 조회 200 OK + 응답 배열")
    void list_returnsOk() throws Exception {
        // given
        when(service.findFiltered(any(), any(), any())).thenReturn(List.of(
                sampleLog(1L, "BACKUP_RESTORE", "PASS"),
                sampleLog(2L, "FAILOVER", "PARTIAL")
        ));

        // when & then
        mockMvc.perform(get("/api/v1/governance/recovery-drills"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].drillType").value("BACKUP_RESTORE"))
                .andExpect(jsonPath("$[0].result").value("PASS"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("GET /recovery-drills — drillType·result·year 필터 적용 시 200 OK")
    void list_withFilter_returnsOk() throws Exception {
        // given
        when(service.findFiltered(eq("FAILOVER"), eq("PASS"), eq(2026)))
                .thenReturn(List.of(sampleLog(10L, "FAILOVER", "PASS")));

        // when & then
        mockMvc.perform(get("/api/v1/governance/recovery-drills")
                        .param("drillType", "FAILOVER")
                        .param("result", "PASS")
                        .param("year", "2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].drillType").value("FAILOVER"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("POST /recovery-drills — 생성 201 Created + 응답 ID")
    void create_returnsCreated() throws Exception {
        // given
        RecoveryDrillRequest req = new RecoveryDrillRequest(
                LocalDate.parse("2026-04-15"), "BACKUP_RESTORE", "PASS",
                120, 30, 240, 60, 1L, null, "정기 시험");
        when(service.create(any(RecoveryDrillLog.class)))
                .thenReturn(sampleLog(33L, "BACKUP_RESTORE", "PASS"));

        // when & then
        mockMvc.perform(post("/api/v1/governance/recovery-drills")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(33))
                .andExpect(jsonPath("$.drillType").value("BACKUP_RESTORE"))
                .andExpect(jsonPath("$.result").value("PASS"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("POST /recovery-drills — drillDate 누락 시 400 Bad Request")
    void create_missingRequired_returns400() throws Exception {
        // given — drillDate, drillType, result 누락
        String invalid = "{\"notes\":\"필수값 없음\"}";

        // when & then
        mockMvc.perform(post("/api/v1/governance/recovery-drills")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalid))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("GET /recovery-drills — 빈 결과는 빈 배열")
    void list_empty_returnsEmptyArray() throws Exception {
        when(service.findFiltered(any(), any(), any())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/governance/recovery-drills"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
