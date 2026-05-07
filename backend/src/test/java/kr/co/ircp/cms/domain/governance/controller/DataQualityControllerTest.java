package kr.co.ircp.cms.domain.governance.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.ircp.cms.config.GlobalExceptionHandler;
import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.governance.dto.QualityRuleRequest;
import kr.co.ircp.cms.domain.governance.entity.DataQualityReport;
import kr.co.ircp.cms.domain.governance.entity.DataQualityRule;
import kr.co.ircp.cms.domain.governance.service.DataQualityService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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
 * DataQualityController GREEN 단계 테스트.
 *
 * <p>SPEC-CMS-009 REQ-DATA-006~008: 품질 룰 CRUD + 즉시 실행 + 리포트 HTTP 계층 검증.
 */
@WebMvcTest(DataQualityController.class)
@ImportAutoConfiguration(exclude = {SecurityAutoConfiguration.class})
@Import({GlobalExceptionHandler.class, kr.co.ircp.cms.support.WebMvcTestInfraConfig.class})
@DisplayName("DataQualityController GREEN 테스트 (REQ-DATA-006~008)")
class DataQualityControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private DataQualityService service;

    private static DataQualityRule sampleRule(Long id, String type) {
        return DataQualityRule.builder()
                .id(id)
                .targetTable("post")
                .targetColumn("title")
                .ruleType(type)
                .threshold(new BigDecimal("0.05"))
                .severity("WARN")
                .status("ACTIVE")
                .scheduleCron("0 0 6 * * *")
                .description(type + " 룰")
                .build();
    }

    private static DataQualityReport sampleReport(Long id, Long ruleId, boolean violation) {
        return DataQualityReport.builder()
                .id(id)
                .ruleId(ruleId)
                .checkedAt(Instant.parse("2026-04-01T06:00:00Z"))
                .measuredValue(new BigDecimal("0.08"))
                .violation(violation)
                .detail("측정값=0.08 임계값=0.05")
                .notified(violation)
                .build();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("GET /quality-rules — 목록 조회 200 OK + 응답 배열")
    void listRules_returnsOk() throws Exception {
        // given
        when(service.findRulesFiltered(any(), any(), any()))
                .thenReturn(List.of(sampleRule(1L, "NULL_RATIO"), sampleRule(2L, "RANGE")));

        // when & then
        mockMvc.perform(get("/api/v1/governance/quality-rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].ruleType").value("NULL_RATIO"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("POST /quality-rules — 생성 201 Created + 응답 ID")
    void create_returnsCreated() throws Exception {
        // given
        QualityRuleRequest req = new QualityRuleRequest(
                "post", "title", "NULL_RATIO", new BigDecimal("0.05"),
                null, null, "WARN", "ACTIVE", "0 0 6 * * *", "post.title NULL 비율 5% 미만");
        when(service.createRule(any(DataQualityRule.class)))
                .thenReturn(sampleRule(11L, "NULL_RATIO"));

        // when & then
        mockMvc.perform(post("/api/v1/governance/quality-rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(11))
                .andExpect(jsonPath("$.ruleType").value("NULL_RATIO"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("PUT /quality-rules/{id} — 수정 200 OK")
    void update_returnsOk() throws Exception {
        // given
        QualityRuleRequest req = new QualityRuleRequest(
                "post", "title", "NULL_RATIO", new BigDecimal("0.10"),
                null, null, "WARN", "ACTIVE", "0 0 6 * * *", "임계값 변경");
        when(service.updateRule(any(DataQualityRule.class))).thenReturn(true);
        when(service.findRuleById(eq(5L))).thenReturn(Optional.of(sampleRule(5L, "NULL_RATIO")));

        // when & then
        mockMvc.perform(put("/api/v1/governance/quality-rules/5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("PUT /quality-rules/{id} — 미존재 시 404 Not Found")
    void update_missingId_returns404() throws Exception {
        // given
        QualityRuleRequest req = new QualityRuleRequest(
                "post", "title", "NULL_RATIO", new BigDecimal("0.10"),
                null, null, "WARN", "ACTIVE", "0 0 6 * * *", "변경");
        when(service.updateRule(any(DataQualityRule.class))).thenReturn(false);

        // when & then
        mockMvc.perform(put("/api/v1/governance/quality-rules/999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("DELETE /quality-rules/{id} — 정상 삭제 204 No Content")
    void delete_existing_returns204() throws Exception {
        // given
        when(service.findRuleById(eq(3L))).thenReturn(Optional.of(sampleRule(3L, "RANGE")));
        when(service.deleteRule(eq(3L))).thenReturn(true);

        // when & then
        mockMvc.perform(delete("/api/v1/governance/quality-rules/3"))
                .andExpect(status().isNoContent());

        verify(service).deleteRule(3L);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("DELETE /quality-rules/{id} — 미존재는 404")
    void delete_missing_returns404() throws Exception {
        when(service.findRuleById(eq(404L))).thenReturn(Optional.empty());

        mockMvc.perform(delete("/api/v1/governance/quality-rules/404"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("DELETE /quality-rules/{id} — 리포트 존재 시 409 Conflict")
    void delete_withReports_returns409() throws Exception {
        // given
        when(service.findRuleById(eq(8L))).thenReturn(Optional.of(sampleRule(8L, "NULL_RATIO")));
        when(service.deleteRule(eq(8L))).thenReturn(false);

        // when & then
        mockMvc.perform(delete("/api/v1/governance/quality-rules/8"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("QUALITY_RULE_HAS_REPORTS"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("POST /quality-rules/{id}/run — 즉시 실행 200 OK + 리포트 응답")
    void runRule_returnsOkWithReport() throws Exception {
        // given
        when(service.findRuleById(eq(2L))).thenReturn(Optional.of(sampleRule(2L, "NULL_RATIO")));
        when(service.runRule(any(DataQualityRule.class)))
                .thenReturn(sampleReport(50L, 2L, true));

        // when & then
        mockMvc.perform(post("/api/v1/governance/quality-rules/2/run"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(50))
                .andExpect(jsonPath("$.ruleId").value(2))
                .andExpect(jsonPath("$.violation").value(true));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("GET /quality-reports — 리포트 목록 조회 200 OK + 페이지 응답")
    void listReports_returnsOk() throws Exception {
        // given
        when(service.findReportsFiltered(any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(PageResponse.of(
                        List.of(sampleReport(1L, 1L, false), sampleReport(2L, 1L, true)),
                        0, 20, 2L));

        // when & then
        mockMvc.perform(get("/api/v1/governance/quality-reports")
                        .param("ruleId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[1].violation").value(true));
    }
}
