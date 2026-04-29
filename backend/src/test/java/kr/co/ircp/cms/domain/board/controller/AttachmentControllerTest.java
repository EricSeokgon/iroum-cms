package kr.co.ircp.cms.domain.board.controller;

import kr.co.ircp.cms.config.GlobalExceptionHandler;
import kr.co.ircp.cms.domain.board.dto.AttachmentDownloadUrl;
import kr.co.ircp.cms.domain.board.dto.AttachmentSummary;
import kr.co.ircp.cms.domain.board.service.AttachmentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AttachmentController GREEN 단계 테스트.
 * REQ-BOARD-004: 첨부파일 업로드 API HTTP 계층 검증.
 * REQ-BOARD-005: 서명 URL 발급 API HTTP 계층 검증.
 */
@WebMvcTest(AttachmentController.class)
@ImportAutoConfiguration(exclude = {SecurityAutoConfiguration.class})
@Import(GlobalExceptionHandler.class)
@DisplayName("AttachmentController GREEN 테스트 (REQ-BOARD-004, REQ-BOARD-005)")
class AttachmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AttachmentService attachmentService;

    @Test
    @DisplayName("GET /api/v1/boards/{bbsMasterId}/posts/{postId}/attachments — 200 OK, 목록 반환")
    void listAttachments_returns200WithList() throws Exception {
        AttachmentSummary summary = new AttachmentSummary(
                1L, 1L, "photo.jpg", "image/jpeg",
                1024L, "PENDING", 0L, Instant.now()
        );
        when(attachmentService.listAttachments(anyLong())).thenReturn(List.of(summary));

        mockMvc.perform(get("/api/v1/boards/1/posts/1/attachments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fileName").value("photo.jpg"));
    }

    @Test
    @DisplayName("POST /api/v1/boards/{bbsMasterId}/posts/{postId}/attachments — 200 OK, 업로드 결과 반환")
    void uploadAttachment_returns200WithSummary() throws Exception {
        AttachmentSummary uploaded = new AttachmentSummary(
                3L, 1L, "test.jpg", "image/jpeg",
                1024L, "PENDING", 0L, Instant.now()
        );
        when(attachmentService.uploadAttachment(anyLong(), any(), any())).thenReturn(uploaded);

        MockMultipartFile file = new MockMultipartFile(
                "file", "test.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[1024]
        );

        mockMvc.perform(multipart("/api/v1/boards/1/posts/1/attachments")
                        .file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.fileName").value("test.jpg"));
    }

    @Test
    @DisplayName("GET /{attachmentId}/download-url — 200 OK, 서명 URL 반환")
    void generateDownloadUrl_returns200WithUrl() throws Exception {
        AttachmentDownloadUrl urlDto = new AttachmentDownloadUrl(
                1L, "test.jpg",
                "/api/v1/board/attachments/1/download?expires=9999999999&sig=abc",
                Instant.now().plusSeconds(900)
        );
        when(attachmentService.generateDownloadUrl(anyLong(), any())).thenReturn(urlDto);

        mockMvc.perform(get("/api/v1/boards/1/posts/1/attachments/1/download-url"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attachmentId").value(1))
                .andExpect(jsonPath("$.fileName").value("test.jpg"));
    }
}
