package kr.co.ircp.cms.domain.board.service;

import kr.co.ircp.cms.domain.board.config.BoardAttachmentProperties;
import kr.co.ircp.cms.domain.board.repository.BbsAttachmentMapper;
import kr.co.ircp.cms.domain.board.repository.BbsMasterMapper;
import kr.co.ircp.cms.domain.board.repository.BbsPostMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AttachmentService RED 단계 테스트.
 * REQ-BOARD-004: 첨부파일 업로드
 * REQ-BOARD-005: HMAC-SHA256 서명 URL 보안 다운로드
 *
 * <p>모든 테스트는 Step 2 GREEN 전까지 UnsupportedOperationException으로 실패해야 한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AttachmentService RED 테스트 (REQ-BOARD-004, REQ-BOARD-005)")
class AttachmentServiceTest {

    @Mock private BbsMasterMapper bbsMasterMapper;
    @Mock private BbsPostMapper bbsPostMapper;
    @Mock private BbsAttachmentMapper bbsAttachmentMapper;

    private AttachmentService attachmentService;

    /** 테스트용 최소 설정 */
    private static final BoardAttachmentProperties TEST_PROPS = new BoardAttachmentProperties(
            "/tmp/test-attachments",
            List.of("image/jpeg", "image/png", "application/pdf"),
            51200L,
            "test-hmac-secret-for-unit-tests-only",
            900
    );

    @BeforeEach
    void setUp() {
        attachmentService = new AttachmentServiceImpl(
                bbsMasterMapper, bbsPostMapper, bbsAttachmentMapper, TEST_PROPS
        );
    }

    // ──────────────────────────────────────────────
    // REQ-BOARD-004-Q: 첨부파일 목록 조회
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("첨부파일 목록 조회 — 게시글 ID로 반환")
    void listAttachments_byPostId_returnsAttachments() {
        assertThatThrownBy(() -> attachmentService.listAttachments(1L))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("Step 2 GREEN 대기");
    }

    // ──────────────────────────────────────────────
    // REQ-BOARD-004-C: 첨부파일 업로드
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("첨부파일 업로드 — 허용 MIME 타입 JPEG 성공")
    void uploadAttachment_allowedMimeType_success() {
        MultipartFile file = new MockMultipartFile(
                "file", "test.jpg", "image/jpeg", new byte[1024]
        );
        assertThatThrownBy(() -> attachmentService.uploadAttachment(1L, file, 1L))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("첨부파일 업로드 — 허용되지 않는 MIME 타입 InvalidAttachmentTypeException")
    void uploadAttachment_disallowedMimeType_throwsInvalidTypeException() {
        // GREEN에서: mimeType 검증 후 InvalidAttachmentTypeException 발생 검증
        MultipartFile file = new MockMultipartFile(
                "file", "malware.exe", "application/x-msdownload", new byte[1024]
        );
        assertThatThrownBy(() -> attachmentService.uploadAttachment(1L, file, 1L))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("첨부파일 업로드 — 크기 초과 AttachmentTooLargeException")
    void uploadAttachment_exceedsMaxSize_throwsTooLargeException() {
        // GREEN에서: sizeBytes > maxAttachmentSizeKb * 1024 시 AttachmentTooLargeException 발생 검증
        byte[] largeContent = new byte[100 * 1024 * 1024]; // 100MB
        MultipartFile file = new MockMultipartFile(
                "file", "large.pdf", "application/pdf", largeContent
        );
        assertThatThrownBy(() -> attachmentService.uploadAttachment(1L, file, 1L))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    // ──────────────────────────────────────────────
    // REQ-BOARD-005: HMAC-SHA256 서명 URL
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("다운로드 URL 생성 — HMAC-SHA256 서명 URL TTL 15분 포함")
    void generateDownloadUrl_validAttachment_returnsSignedUrl() {
        assertThatThrownBy(() -> attachmentService.generateDownloadUrl(1L, 1L))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("다운로드 URL 검증 — 만료된 토큰은 AttachmentDownloadDeniedException")
    void verifyAndDownload_expiredToken_throwsDownloadDeniedException() {
        // GREEN에서: 만료된 토큰 검증 → AttachmentDownloadDeniedException 발생 검증
        assertThatThrownBy(() -> attachmentService.verifyAndDownload("expired-token"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("다운로드 URL 검증 — 유효한 토큰 다운로드 횟수 증가")
    void verifyAndDownload_validToken_incrementsDownloadCount() {
        // GREEN에서: 유효 토큰 → bbsAttachmentMapper.incrementDownloadCount 호출 검증
        assertThatThrownBy(() -> attachmentService.verifyAndDownload("valid-token"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
