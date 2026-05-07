package kr.co.ircp.cms.domain.board.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.ircp.cms.config.GlobalExceptionHandler;
import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.board.dto.PublicationCategoryDto;
import kr.co.ircp.cms.domain.board.dto.PublicationCreateRequest;
import kr.co.ircp.cms.domain.board.dto.PublicationDetail;
import kr.co.ircp.cms.domain.board.dto.PublicationSummary;
import kr.co.ircp.cms.domain.board.dto.PublicationUpdateRequest;
import kr.co.ircp.cms.domain.board.dto.ZipDownloadRequest;
import kr.co.ircp.cms.domain.board.dto.ZipDownloadResponse;
import kr.co.ircp.cms.domain.board.exception.PublicationNotFoundException;
import kr.co.ircp.cms.domain.board.service.PublicationService;
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
import java.util.UUID;

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
 * PublicationController GREEN 단계 테스트.
 * REQ-BOARD-012: 발간자료 카테고리·메타·다운로드 통계·ZIP 아카이브 HTTP 계층 검증.
 */
@WebMvcTest(PublicationController.class)
@ImportAutoConfiguration(exclude = {SecurityAutoConfiguration.class})
@Import({GlobalExceptionHandler.class, kr.co.ircp.cms.support.WebMvcTestInfraConfig.class})
@DisplayName("PublicationController GREEN 테스트 (REQ-BOARD-012)")
class PublicationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PublicationService publicationService;

    @Autowired
    private ObjectMapper objectMapper;

    private PublicationDetail sampleDetail(Long id) {
        return new PublicationDetail(
                id, "2026년 정책 백서",
                "<p>본문 HTML</p>",
                2026, 4, "REPORT", 10L, "정책백서",
                3, "979-11-1234567-89-0", "이로움정책연구원",
                250L,
                Instant.now(), Instant.now(), Instant.now()
        );
    }

    @Test
    @DisplayName("GET /api/v1/publications — 200 OK, 페이징 응답 반환 (공개)")
    void list_returns200WithPage() throws Exception {
        // given
        PublicationSummary summary = new PublicationSummary(
                1L, "2026년 정책 백서", 2026, 4, "REPORT", "정책백서",
                3, "979-11-1234567-89-0", "이로움정책연구원",
                250L, Instant.now()
        );
        PageResponse<PublicationSummary> page = PageResponse.of(List.of(summary), 0, 20, 1L);
        when(publicationService.listPublications(
                any(), any(), any(), any(), any(), anyInt(), anyInt())).thenReturn(page);

        // when & then
        mockMvc.perform(get("/api/v1/publications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].postId").value(1))
                .andExpect(jsonPath("$.content[0].documentType").value("REPORT"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/publications/categories — 200 OK, 카테고리 트리 반환 (공개)")
    void getCategories_returns200WithTree() throws Exception {
        // given
        PublicationCategoryDto child = new PublicationCategoryDto(
                11L, "POLICY_PAPER", "정책백서", 10L, 2, 1, "ACTIVE", List.of()
        );
        PublicationCategoryDto root = new PublicationCategoryDto(
                10L, "RESEARCH", "연구자료", null, 1, 1, "ACTIVE", List.of(child)
        );
        when(publicationService.getCategories()).thenReturn(List.of(root));

        // when & then
        mockMvc.perform(get("/api/v1/publications/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(10))
                .andExpect(jsonPath("$[0].code").value("RESEARCH"))
                .andExpect(jsonPath("$[0].children[0].id").value(11))
                .andExpect(jsonPath("$[0].children[0].code").value("POLICY_PAPER"));
    }

    @Test
    @DisplayName("GET /api/v1/publications/{id} — 200 OK, 단건 상세 반환 (공개)")
    void getDetail_existing_returns200() throws Exception {
        // given
        when(publicationService.getPublication(1L)).thenReturn(sampleDetail(1L));

        // when & then
        mockMvc.perform(get("/api/v1/publications/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.postId").value(1))
                .andExpect(jsonPath("$.documentType").value("REPORT"));
    }

    @Test
    @DisplayName("GET /api/v1/publications/{id} — 미존재 시 404 + PUBLICATION_NOT_FOUND")
    void getDetail_nonExistent_returns404() throws Exception {
        // given
        when(publicationService.getPublication(999L))
                .thenThrow(new PublicationNotFoundException(999L));

        // when & then
        mockMvc.perform(get("/api/v1/publications/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PUBLICATION_NOT_FOUND"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("POST /api/v1/publications — ADMIN 인증 시 201 Created + 등록된 발간자료 반환")
    void create_validRequest_returns201_whenAdmin() throws Exception {
        // given
        when(publicationService.createPublication(any(), any())).thenReturn(sampleDetail(99L));
        PublicationCreateRequest req = new PublicationCreateRequest(
                "2026년 정책 백서", "<p>본문</p>", "본문",
                2026, 4, "REPORT", 10L,
                "979-11-1234567-89-0", "이로움정책연구원", "{}"
        );

        // when & then
        mockMvc.perform(post("/api/v1/publications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.postId").value(99))
                .andExpect(jsonPath("$.documentType").value("REPORT"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("PUT /api/v1/publications/{id} — ADMIN 인증 시 200 OK + 수정된 발간자료 반환")
    void update_existing_returns200_whenAdmin() throws Exception {
        // given
        when(publicationService.updatePublication(eq(1L), any())).thenReturn(sampleDetail(1L));
        PublicationUpdateRequest req = new PublicationUpdateRequest(
                "수정된 제목", "<p>수정 본문</p>", "수정 본문",
                2026, 5, "REPORT", 10L,
                "979-11-1234567-89-0", "이로움정책연구원", null
        );

        // when & then
        mockMvc.perform(put("/api/v1/publications/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.postId").value(1));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("DELETE /api/v1/publications/{id} — ADMIN 인증 시 204 No Content")
    void delete_existing_returns204_whenAdmin() throws Exception {
        // given
        doNothing().when(publicationService).deletePublication(anyLong());

        // when & then
        mockMvc.perform(delete("/api/v1/publications/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("POST /api/v1/publications/{id}/download-zip — 200 OK + ZipDownloadResponse 반환 (공개)")
    void downloadZip_returns200WithModeAndId() throws Exception {
        // given
        UUID downloadId = UUID.randomUUID();
        ZipDownloadResponse resp = new ZipDownloadResponse(
                downloadId, "SYNC", "다운로드 준비 완료", 12_345_678L
        );
        when(publicationService.requestZipDownload(eq(1L), any(), any())).thenReturn(resp);
        ZipDownloadRequest req = new ZipDownloadRequest(List.of(UUID.randomUUID(), UUID.randomUUID()));

        // when & then
        mockMvc.perform(post("/api/v1/publications/1/download-zip")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("SYNC"))
                .andExpect(jsonPath("$.downloadId").value(downloadId.toString()))
                .andExpect(jsonPath("$.sizeBytes").value(12_345_678L));
    }
}
