package kr.co.ircp.cms.domain.board.controller;

import kr.co.ircp.cms.config.GlobalExceptionHandler;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AttachmentController RED 단계 테스트.
 * REQ-BOARD-004: 첨부파일 업로드 API HTTP 계층 검증.
 * REQ-BOARD-005: 서명 URL 발급 API HTTP 계층 검증.
 *
 * <p>Step 2 GREEN 전까지 서비스가 UnsupportedOperationException을 던지므로
 * 모든 요청은 500 상태로 응답한다.
 */
@WebMvcTest(AttachmentController.class)
@ImportAutoConfiguration(exclude = {SecurityAutoConfiguration.class})
@Import(GlobalExceptionHandler.class)
@DisplayName("AttachmentController RED 테스트 (REQ-BOARD-004, REQ-BOARD-005)")
class AttachmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AttachmentService attachmentService;

    @Test
    @DisplayName("GET /api/v1/boards/{bbsMasterId}/posts/{postId}/attachments — 서비스 스텁 500 (RED)")
    void listAttachments_serviceStub_returns500() throws Exception {
        when(attachmentService.listAttachments(anyLong()))
                .thenThrow(new UnsupportedOperationException("Step 2 GREEN 대기"));

        mockMvc.perform(get("/api/v1/boards/1/posts/1/attachments"))
                .andExpect(status().is5xxServerError());
    }

    @Test
    @DisplayName("POST /api/v1/boards/{bbsMasterId}/posts/{postId}/attachments — 서비스 스텁 500 (RED)")
    void uploadAttachment_serviceStub_returns500() throws Exception {
        when(attachmentService.uploadAttachment(anyLong(), any(), any()))
                .thenThrow(new UnsupportedOperationException("Step 2 GREEN 대기"));

        MockMultipartFile file = new MockMultipartFile(
                "file", "test.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[1024]
        );

        mockMvc.perform(multipart("/api/v1/boards/1/posts/1/attachments")
                        .file(file))
                .andExpect(status().is5xxServerError());
    }

    @Test
    @DisplayName("GET /api/v1/boards/{bbsMasterId}/posts/{postId}/attachments/{attachmentId}/download-url — 서비스 스텁 500 (RED)")
    void generateDownloadUrl_serviceStub_returns500() throws Exception {
        when(attachmentService.generateDownloadUrl(anyLong(), any()))
                .thenThrow(new UnsupportedOperationException("Step 2 GREEN 대기"));

        mockMvc.perform(get("/api/v1/boards/1/posts/1/attachments/1/download-url"))
                .andExpect(status().is5xxServerError());
    }
}
