package kr.co.ircp.cms.integration.governance;

import kr.co.ircp.cms.domain.governance.service.BatchExecutionLogService;
import kr.co.ircp.cms.integration.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SPEC-CMS-009 Step 2 — BatchExecutionLogController 통합 테스트.
 */
@AutoConfigureMockMvc
@WithMockUser(roles = "ADMIN")
@Transactional
class GovernanceBatchLogsIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private BatchExecutionLogService logService;

    @Test
    void list_filterByJobGroup_returnsPaginatedResponse() throws Exception {
        // 시드: STATS 그룹 1건 insert
        Long id = logService.start("BoardStatsDailyJob_IT", "STATS");
        logService.success(id, 5);

        mockMvc.perform(get("/api/v1/governance/batch-logs")
                        .param("jobGroup", "STATS")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.page").value(0));
    }

    @Test
    void getById_returnsErrorSummary() throws Exception {
        Long id = logService.start("FailingJob_IT", "QUALITY");
        logService.failure(id, "IT-injected failure summary");

        mockMvc.perform(get("/api/v1/governance/batch-logs/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.status").value("FAILURE"))
                .andExpect(jsonPath("$.errorSummary",
                        org.hamcrest.Matchers.containsString("IT-injected failure summary")));
    }
}
