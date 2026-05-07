package kr.co.ircp.cms.domain.board.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.ircp.cms.config.GlobalExceptionHandler;
import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.board.dto.FaqCategoryCount;
import kr.co.ircp.cms.domain.board.dto.FaqCreateRequest;
import kr.co.ircp.cms.domain.board.dto.FaqDetail;
import kr.co.ircp.cms.domain.board.dto.FaqReorderItem;
import kr.co.ircp.cms.domain.board.dto.FaqReorderRequest;
import kr.co.ircp.cms.domain.board.dto.FaqSummary;
import kr.co.ircp.cms.domain.board.dto.FaqUpdateRequest;
import kr.co.ircp.cms.domain.board.exception.FaqNotFoundException;
import kr.co.ircp.cms.domain.board.service.FaqService;
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

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * FaqController GREEN 단계 테스트.
 * REQ-BOARD-007: FAQ 카테고리·정렬·검색 + 일괄 정렬 변경 HTTP 계층 검증.
 */
@WebMvcTest(FaqController.class)
@ImportAutoConfiguration(exclude = {SecurityAutoConfiguration.class})
@Import({GlobalExceptionHandler.class, kr.co.ircp.cms.support.WebMvcTestInfraConfig.class})
@DisplayName("FaqController GREEN 테스트 (REQ-BOARD-007)")
class FaqControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FaqService faqService;

    @Autowired
    private ObjectMapper objectMapper;

    private FaqDetail sampleDetail(Long id) {
        return new FaqDetail(
                id, "GENERAL", "회원 가입은 어떻게 하나요?",
                "<p>홈페이지 우측 상단의 회원가입 버튼을 클릭하세요.</p>",
                "회원가입 절차 설명",
                1, 100L, "ACTIVE", Instant.now(), Instant.now()
        );
    }

    @Test
    @DisplayName("GET /api/v1/faqs — 200 OK, 페이징 응답 반환 (공개)")
    void list_returns200WithPage() throws Exception {
        // given
        FaqSummary summary = new FaqSummary(
                1L, "GENERAL", "회원 가입은 어떻게 하나요?", 1, 100L, "ACTIVE", Instant.now()
        );
        PageResponse<FaqSummary> page = PageResponse.of(List.of(summary), 0, 20, 1L);
        when(faqService.listFaqs(any(), any(), anyInt(), anyInt())).thenReturn(page);

        // when & then
        mockMvc.perform(get("/api/v1/faqs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].categoryCode").value("GENERAL"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/faqs/categories — 200 OK, 카테고리별 개수 반환 (공개)")
    void getCategories_returns200() throws Exception {
        // given
        FaqCategoryCount count = new FaqCategoryCount("GENERAL", 25L);
        when(faqService.getCategories()).thenReturn(List.of(count));

        // when & then
        mockMvc.perform(get("/api/v1/faqs/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].categoryCode").value("GENERAL"))
                .andExpect(jsonPath("$[0].count").value(25));
    }

    @Test
    @DisplayName("GET /api/v1/faqs/{id} — 200 OK, 단건 상세 반환 (공개)")
    void getDetail_existing_returns200() throws Exception {
        // given
        when(faqService.getFaq(1L)).thenReturn(sampleDetail(1L));

        // when & then
        mockMvc.perform(get("/api/v1/faqs/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.categoryCode").value("GENERAL"));
    }

    @Test
    @DisplayName("GET /api/v1/faqs/{id} — 미존재 시 404 + FAQ_NOT_FOUND")
    void getDetail_nonExistent_returns404() throws Exception {
        // given
        when(faqService.getFaq(999L)).thenThrow(new FaqNotFoundException(999L));

        // when & then
        mockMvc.perform(get("/api/v1/faqs/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FAQ_NOT_FOUND"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("POST /api/v1/faqs — ADMIN 인증 시 201 Created + 등록된 FAQ 반환")
    void create_validRequest_returns201_whenAdmin() throws Exception {
        // given
        when(faqService.createFaq(any(), any())).thenReturn(sampleDetail(7L));
        FaqCreateRequest req = new FaqCreateRequest(
                "GENERAL", "회원 가입은 어떻게 하나요?",
                "<p>회원가입 페이지 안내</p>", 1
        );

        // when & then
        mockMvc.perform(post("/api/v1/faqs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(7));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("PUT /api/v1/faqs/{id} — ADMIN 인증 시 200 OK + 수정된 FAQ 반환")
    void update_existing_returns200_whenAdmin() throws Exception {
        // given
        when(faqService.updateFaq(eq(1L), any())).thenReturn(sampleDetail(1L));
        FaqUpdateRequest req = new FaqUpdateRequest(
                "GENERAL", "수정된 질문", "<p>수정된 답변</p>", 1, "ACTIVE"
        );

        // when & then
        mockMvc.perform(put("/api/v1/faqs/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("DELETE /api/v1/faqs/{id} — ADMIN 인증 시 204 No Content")
    void delete_existing_returns204_whenAdmin() throws Exception {
        // given
        doNothing().when(faqService).deleteFaq(anyLong());

        // when & then
        mockMvc.perform(delete("/api/v1/faqs/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("PUT /api/v1/faqs/reorder — ADMIN 인증 시 204 No Content, 정렬 일괄 변경 성공")
    void reorder_validRequest_returns204_whenAdmin() throws Exception {
        // given
        doNothing().when(faqService).reorderFaqs(any());
        FaqReorderRequest req = new FaqReorderRequest(List.of(
                new FaqReorderItem(1L, 1),
                new FaqReorderItem(2L, 2)
        ));

        // when & then
        mockMvc.perform(put("/api/v1/faqs/reorder")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNoContent());
    }
}
