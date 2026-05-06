package kr.co.ircp.cms.domain.board.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.ircp.cms.config.GlobalExceptionHandler;
import kr.co.ircp.cms.domain.board.dto.BbsMasterCreateRequest;
import kr.co.ircp.cms.domain.board.dto.BbsMasterDetail;
import kr.co.ircp.cms.domain.board.dto.BbsMasterSummary;
import kr.co.ircp.cms.domain.board.entity.BbsType;
import kr.co.ircp.cms.domain.board.service.BbsMasterService;
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
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BbsMasterController GREEN 단계 테스트.
 * REQ-BOARD-001: 게시판 마스터 CRUD API HTTP 계층 검증.
 */
@WebMvcTest(BbsMasterController.class)
@ImportAutoConfiguration(exclude = {SecurityAutoConfiguration.class})
@Import({GlobalExceptionHandler.class, kr.co.ircp.cms.support.WebMvcTestInfraConfig.class})
@DisplayName("BbsMasterController GREEN 테스트 (REQ-BOARD-001)")
class BbsMasterControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BbsMasterService bbsMasterService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("GET /api/v1/boards — 200 OK, 목록 반환")
    void listBoards_returns200WithList() throws Exception {
        BbsMasterSummary summary = new BbsMasterSummary(
                1L, "NOTICE", "공지사항", BbsType.NOTICE,
                false, false, "ACTIVE", Instant.now()
        );
        when(bbsMasterService.listBoards()).thenReturn(List.of(summary));

        mockMvc.perform(get("/api/v1/boards"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("NOTICE"));
    }

    @Test
    @DisplayName("GET /api/v1/boards/{id} — 200 OK, 단건 반환")
    void getBoard_returns200WithDetail() throws Exception {
        BbsMasterDetail detail = new BbsMasterDetail(
                1L, "NOTICE", "공지사항", null, BbsType.NOTICE,
                false, false, 0, 0L, false, false, 20, null, null,
                "ACTIVE", null, Instant.now(), Instant.now()
        );
        when(bbsMasterService.getBoard(1L)).thenReturn(detail);

        mockMvc.perform(get("/api/v1/boards/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    @DisplayName("POST /api/v1/boards — 201 Created, Location 헤더 포함")
    void createBoard_returns201WithBody() throws Exception {
        BbsMasterDetail created = new BbsMasterDetail(
                2L, "NOTICE", "공지사항", null, BbsType.NOTICE,
                true, false, 0, 0L, false, false, 20, null, null,
                "ACTIVE", null, Instant.now(), Instant.now()
        );
        when(bbsMasterService.createBoard(any())).thenReturn(created);

        BbsMasterCreateRequest request = new BbsMasterCreateRequest(
                "NOTICE", "공지사항", null, BbsType.NOTICE,
                true, false, 0, 0L, false, false, 20, null, null
        );

        mockMvc.perform(post("/api/v1/boards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(2));
    }
}
