package kr.co.ircp.cms.domain.board.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.ircp.cms.config.GlobalExceptionHandler;
import kr.co.ircp.cms.domain.board.dto.BbsMasterCreateRequest;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BbsMasterController RED 단계 테스트.
 * REQ-BOARD-001: 게시판 마스터 CRUD API HTTP 계층 검증.
 *
 * <p>Step 2 GREEN 전까지 서비스가 UnsupportedOperationException을 던지므로
 * 모든 요청은 500 또는 해당 상태로 응답한다.
 */
@WebMvcTest(BbsMasterController.class)
@ImportAutoConfiguration(exclude = {SecurityAutoConfiguration.class})
@Import(GlobalExceptionHandler.class)
@DisplayName("BbsMasterController RED 테스트 (REQ-BOARD-001)")
class BbsMasterControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BbsMasterService bbsMasterService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("GET /api/v1/boards — 서비스 스텁 상태 500 반환 (RED)")
    void listBoards_serviceStub_returns500() throws Exception {
        when(bbsMasterService.listBoards())
                .thenThrow(new UnsupportedOperationException("Step 2 GREEN 대기"));

        mockMvc.perform(get("/api/v1/boards"))
                .andExpect(status().is5xxServerError());
    }

    @Test
    @DisplayName("GET /api/v1/boards/{id} — 서비스 스텁 상태 500 반환 (RED)")
    void getBoard_serviceStub_returns500() throws Exception {
        when(bbsMasterService.getBoard(1L))
                .thenThrow(new UnsupportedOperationException("Step 2 GREEN 대기"));

        mockMvc.perform(get("/api/v1/boards/1"))
                .andExpect(status().is5xxServerError());
    }

    @Test
    @DisplayName("POST /api/v1/boards — 서비스 스텁 상태 500 반환 (RED)")
    void createBoard_serviceStub_returns500() throws Exception {
        when(bbsMasterService.createBoard(any()))
                .thenThrow(new UnsupportedOperationException("Step 2 GREEN 대기"));

        BbsMasterCreateRequest request = new BbsMasterCreateRequest(
                "NOTICE", "공지사항", null, BbsType.NOTICE,
                true, false, 0, 0L, false, false, 20, null, null
        );

        mockMvc.perform(post("/api/v1/boards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().is5xxServerError());
    }
}
