package kr.co.ircp.cms.integration.governance;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.ircp.cms.domain.governance.dto.RecoveryDrillRequest;
import kr.co.ircp.cms.integration.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SPEC-CMS-009 Step 2 — RecoveryDrillController 통합 테스트.
 */
@AutoConfigureMockMvc
@WithMockUser(roles = "ADMIN")
@Transactional
class GovernanceRecoveryDrillIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void create_returns201_persistsDrillLog() throws Exception {
        RecoveryDrillRequest req = new RecoveryDrillRequest(
                LocalDate.now(),
                "BACKUP_RESTORE",
                "PASS",
                120, 30,
                240, 60,
                null,
                "{\"steps\":[\"snapshot\",\"restore\",\"verify\"]}",
                "IT 테스트 백업 복구");

        mockMvc.perform(post("/api/v1/governance/recovery-drills")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.drillType").value("BACKUP_RESTORE"))
                .andExpect(jsonPath("$.result").value("PASS"))
                .andExpect(jsonPath("$.id").exists());
    }

    @Test
    void list_filterByDrillType_returnsResults() throws Exception {
        // Seed an entry first
        RecoveryDrillRequest req = new RecoveryDrillRequest(
                LocalDate.now(),
                "FAILOVER",
                "PARTIAL",
                300, 90,
                240, 60,
                null,
                null,
                "IT 시드");
        mockMvc.perform(post("/api/v1/governance/recovery-drills")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/governance/recovery-drills")
                        .param("drillType", "FAILOVER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].drillType",
                        org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("FAILOVER"))));
    }
}
