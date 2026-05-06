package kr.co.ircp.cms.integration.governance;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.ircp.cms.domain.governance.dto.DictionaryRequest;
import kr.co.ircp.cms.domain.governance.entity.DataDictionary;
import kr.co.ircp.cms.domain.governance.entity.DataDictionaryHistory;
import kr.co.ircp.cms.domain.governance.repository.DataDictionaryMapper;
import kr.co.ircp.cms.integration.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SPEC-CMS-009 Step 2 — DictionaryController 통합 테스트.
 *
 * <p>POST/PUT/DELETE/Export 엔드포인트가 PostgreSQL 컨테이너에서
 * data_dictionary + history 테이블에 정상 동작하는지 검증한다.
 */
@AutoConfigureMockMvc
@WithMockUser(roles = "ADMIN")
@Transactional
class GovernanceDictionaryIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private DataDictionaryMapper mapper;

    @Test
    void createDictionary_returns201_andPersists() throws Exception {
        DictionaryRequest req = new DictionaryRequest(
                "it_test_table_create",
                "test_col",
                "테스트컬럼",
                "TestColumn",
                "MASTER",
                "VARCHAR(50)",
                "통합 테스트용",
                false, false, "ACTIVE");

        MvcResult res = mockMvc.perform(post("/api/v1/governance/dictionary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tableName").value("it_test_table_create"))
                .andExpect(jsonPath("$.id").exists())
                .andReturn();

        Long id = ((Number) objectMapper.readValue(
                res.getResponse().getContentAsString(), Map.class).get("id")).longValue();
        DataDictionary saved = mapper.findById(id).orElseThrow();
        assertThat(saved.getLogicalNameKo()).isEqualTo("테스트컬럼");
    }

    @Test
    void updateDictionary_changesField_recordsHistory() throws Exception {
        // Insert directly via mapper for isolation
        DataDictionary entity = DataDictionary.builder()
                .tableName("it_test_table_update")
                .columnName("col_update")
                .logicalNameKo("원래이름")
                .dataDomain("MASTER")
                .dataType("VARCHAR(50)")
                .status("ACTIVE")
                .build();
        mapper.insert(entity);

        DictionaryRequest req = new DictionaryRequest(
                "it_test_table_update", "col_update",
                "변경된이름", null, "MASTER", "VARCHAR(50)",
                "수정", false, false, "ACTIVE");

        mockMvc.perform(put("/api/v1/governance/dictionary/" + entity.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.logicalNameKo").value("변경된이름"));

        List<DataDictionaryHistory> hist = mapper.findHistory(entity.getId());
        assertThat(hist).isNotEmpty();
        assertThat(hist.stream().anyMatch(h -> "logical_name_ko".equals(h.getFieldChanged())
                && "원래이름".equals(h.getOldValue()))).isTrue();
    }

    @Test
    void exportCsv_returnsAttachmentHeader_andCsvBody() throws Exception {
        mockMvc.perform(get("/api/v1/governance/dictionary/export")
                        .param("format", "csv"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("data_dictionary.csv")))
                .andExpect(content().contentTypeCompatibleWith("text/csv"));
    }

    @Test
    void deleteDictionary_softDeletesToRemoved() throws Exception {
        DataDictionary entity = DataDictionary.builder()
                .tableName("it_test_table_delete")
                .columnName("col_del")
                .logicalNameKo("삭제대상")
                .dataDomain("MASTER")
                .dataType("VARCHAR(50)")
                .status("ACTIVE")
                .build();
        mapper.insert(entity);

        mockMvc.perform(delete("/api/v1/governance/dictionary/" + entity.getId()))
                .andExpect(status().isNoContent());

        DataDictionary after = mapper.findById(entity.getId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo("REMOVED");
    }
}
