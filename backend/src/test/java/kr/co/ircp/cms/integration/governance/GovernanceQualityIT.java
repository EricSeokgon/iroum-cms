package kr.co.ircp.cms.integration.governance;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.ircp.cms.domain.governance.dto.QualityRuleRequest;
import kr.co.ircp.cms.domain.governance.entity.DataQualityReport;
import kr.co.ircp.cms.domain.governance.entity.DataQualityRule;
import kr.co.ircp.cms.domain.governance.repository.DataQualityMapper;
import kr.co.ircp.cms.integration.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SPEC-CMS-009 Step 2 — DataQualityController 통합 테스트.
 *
 * <p>룰 CRUD + 즉시 실행 + 리포트 필터링 + 409 충돌 응답을 검증한다.
 */
@AutoConfigureMockMvc
@WithMockUser(roles = "ADMIN")
@Transactional
class GovernanceQualityIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private DataQualityMapper mapper;

    @Test
    void createRule_returns201_andPersists() throws Exception {
        QualityRuleRequest req = new QualityRuleRequest(
                "users", "email", "NULL_RATIO",
                new BigDecimal("0.0500"), null, null,
                "WARN", "ACTIVE", "0 0 6 * * *", "IT 테스트 룰");

        MvcResult res = mockMvc.perform(post("/api/v1/governance/quality-rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.targetTable").value("users"))
                .andExpect(jsonPath("$.ruleType").value("NULL_RATIO"))
                .andReturn();

        Long id = ((Number) objectMapper.readValue(
                res.getResponse().getContentAsString(), Map.class).get("id")).longValue();
        assertThat(mapper.findRuleById(id)).isPresent();
    }

    @Test
    void runRule_executesChecker_andCreatesReport() throws Exception {
        // Use a known seed rule (FRESHNESS on access_log, status=ACTIVE)
        DataQualityRule rule = mapper.findActiveRules().stream()
                .filter(r -> "FRESHNESS".equals(r.getRuleType()))
                .findFirst().orElseThrow();

        mockMvc.perform(post("/api/v1/governance/quality-rules/" + rule.getId() + "/run"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ruleId").value(rule.getId()));

        // The new report exists (seed rules may have prior reports — check at least one report exists for this rule)
        assertThat(mapper.findReportsByRule(rule.getId())).isNotEmpty();
    }

    @Test
    void listReports_filterByViolationTrue_onlyReturnsViolations() throws Exception {
        // Insert one violation report for an existing rule
        DataQualityRule rule = mapper.findActiveRules().get(0);
        mapper.insertReport(DataQualityReport.builder()
                .ruleId(rule.getId())
                .checkedAt(Instant.now())
                .measuredValue(new BigDecimal("0.99"))
                .violation(true)
                .detail("IT injected violation")
                .notified(false)
                .build());

        mockMvc.perform(get("/api/v1/governance/quality-reports")
                        .param("violation", "true")
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].violation",
                        org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is(true))));
    }

    @Test
    void deleteRule_withExistingReports_returns409() throws Exception {
        DataQualityRule rule = mapper.findActiveRules().get(0);
        // Ensure at least one report exists
        mapper.insertReport(DataQualityReport.builder()
                .ruleId(rule.getId())
                .checkedAt(Instant.now())
                .measuredValue(BigDecimal.ZERO)
                .violation(false)
                .detail("IT report for delete-conflict")
                .notified(false)
                .build());

        mockMvc.perform(delete("/api/v1/governance/quality-rules/" + rule.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("QUALITY_RULE_HAS_REPORTS"));
    }
}
