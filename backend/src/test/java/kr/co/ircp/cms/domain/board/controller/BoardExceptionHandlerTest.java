package kr.co.ircp.cms.domain.board.controller;

import kr.co.ircp.cms.config.GlobalExceptionHandler;
import kr.co.ircp.cms.domain.board.exception.AttachmentDownloadDeniedException;
import kr.co.ircp.cms.domain.board.exception.AttachmentNotFoundException;
import kr.co.ircp.cms.domain.board.exception.AttachmentTooLargeException;
import kr.co.ircp.cms.domain.board.exception.BbsMasterNotFoundException;
import kr.co.ircp.cms.domain.board.exception.BoardAttachmentDisabledException;
import kr.co.ircp.cms.domain.board.exception.BoardCommentDisabledException;
import kr.co.ircp.cms.domain.board.exception.CommentNotFoundException;
import kr.co.ircp.cms.domain.board.exception.DuplicateBbsCodeException;
import kr.co.ircp.cms.domain.board.exception.InvalidAttachmentTypeException;
import kr.co.ircp.cms.domain.board.exception.PostNotFoundException;
import kr.co.ircp.cms.domain.board.service.BbsMasterService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GlobalExceptionHandler — 게시판 도메인 예외 HTTP 상태 매핑 검증.
 * REQ-BOARD-001~005: 각 board 예외가 정확한 HTTP 상태 코드를 반환하는지 확인.
 */
@WebMvcTest(BbsMasterController.class)
@ImportAutoConfiguration(exclude = {SecurityAutoConfiguration.class})
@Import({GlobalExceptionHandler.class, kr.co.ircp.cms.support.WebMvcTestInfraConfig.class})
@DisplayName("GlobalExceptionHandler — 게시판 도메인 예외 매핑 (REQ-BOARD-001~005)")
class BoardExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BbsMasterService bbsMasterService;

    @Test
    @DisplayName("BbsMasterNotFoundException → HTTP 404")
    void bbsMasterNotFound_returns404() throws Exception {
        when(bbsMasterService.getBoard(999L)).thenThrow(new BbsMasterNotFoundException(999L));

        mockMvc.perform(get("/api/v1/boards/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Board Not Found"))
                .andExpect(jsonPath("$.properties.code").value("BOARD_NOT_FOUND"));
    }

    @Test
    @DisplayName("DuplicateBbsCodeException → HTTP 409")
    void duplicateBbsCode_returns409() throws Exception {
        when(bbsMasterService.getBoard(1L)).thenThrow(new DuplicateBbsCodeException("NOTICE"));

        mockMvc.perform(get("/api/v1/boards/1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.properties.code").value("BOARD_CODE_DUPLICATE"));
    }

    @Test
    @DisplayName("PostNotFoundException → HTTP 404")
    void postNotFound_returns404() throws Exception {
        when(bbsMasterService.getBoard(1L)).thenThrow(new PostNotFoundException(42L));

        mockMvc.perform(get("/api/v1/boards/1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.properties.code").value("POST_NOT_FOUND"));
    }

    @Test
    @DisplayName("CommentNotFoundException → HTTP 404")
    void commentNotFound_returns404() throws Exception {
        when(bbsMasterService.getBoard(1L)).thenThrow(new CommentNotFoundException(10L));

        mockMvc.perform(get("/api/v1/boards/1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.properties.code").value("COMMENT_NOT_FOUND"));
    }

    @Test
    @DisplayName("AttachmentNotFoundException → HTTP 404")
    void attachmentNotFound_returns404() throws Exception {
        when(bbsMasterService.getBoard(1L)).thenThrow(new AttachmentNotFoundException(5L));

        mockMvc.perform(get("/api/v1/boards/1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.properties.code").value("ATTACHMENT_NOT_FOUND"));
    }

    @Test
    @DisplayName("AttachmentTooLargeException → HTTP 413")
    void attachmentTooLarge_returns413() throws Exception {
        when(bbsMasterService.getBoard(1L)).thenThrow(new AttachmentTooLargeException(52000L, 51200L));

        mockMvc.perform(get("/api/v1/boards/1"))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.properties.code").value("ATTACHMENT_TOO_LARGE"));
    }

    @Test
    @DisplayName("InvalidAttachmentTypeException → HTTP 400")
    void invalidAttachmentType_returns400() throws Exception {
        when(bbsMasterService.getBoard(1L)).thenThrow(new InvalidAttachmentTypeException("application/x-msdownload"));

        mockMvc.perform(get("/api/v1/boards/1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.properties.code").value("ATTACHMENT_TYPE_INVALID"));
    }

    @Test
    @DisplayName("AttachmentDownloadDeniedException → HTTP 403")
    void attachmentDownloadDenied_returns403() throws Exception {
        when(bbsMasterService.getBoard(1L)).thenThrow(new AttachmentDownloadDeniedException("서명 검증 실패"));

        mockMvc.perform(get("/api/v1/boards/1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.properties.code").value("ATTACHMENT_DOWNLOAD_DENIED"));
    }

    @Test
    @DisplayName("BoardCommentDisabledException → HTTP 400")
    void boardCommentDisabled_returns400() throws Exception {
        when(bbsMasterService.getBoard(1L)).thenThrow(new BoardCommentDisabledException("NOTICE"));

        mockMvc.perform(get("/api/v1/boards/1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.properties.code").value("BOARD_COMMENT_DISABLED"));
    }

    @Test
    @DisplayName("BoardAttachmentDisabledException → HTTP 400")
    void boardAttachmentDisabled_returns400() throws Exception {
        when(bbsMasterService.getBoard(1L)).thenThrow(new BoardAttachmentDisabledException("FAQ"));

        mockMvc.perform(get("/api/v1/boards/1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.properties.code").value("BOARD_ATTACHMENT_DISABLED"));
    }
}
