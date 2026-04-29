package kr.co.ircp.cms.domain.board.service;

import kr.co.ircp.cms.domain.board.config.BoardAttachmentProperties;
import kr.co.ircp.cms.domain.board.dto.AttachmentDownloadUrl;
import kr.co.ircp.cms.domain.board.dto.AttachmentSummary;
import kr.co.ircp.cms.domain.board.entity.BbsAttachment;
import kr.co.ircp.cms.domain.board.exception.AttachmentDownloadDeniedException;
import kr.co.ircp.cms.domain.board.exception.AttachmentNotFoundException;
import kr.co.ircp.cms.domain.board.exception.AttachmentTooLargeException;
import kr.co.ircp.cms.domain.board.exception.InvalidAttachmentTypeException;
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

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AttachmentService GREEN 단계 테스트.
 * REQ-BOARD-004: 첨부파일 업로드
 * REQ-BOARD-005: HMAC-SHA256 서명 URL 보안 다운로드
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AttachmentService GREEN 테스트 (REQ-BOARD-004, REQ-BOARD-005)")
class AttachmentServiceTest {

    @Mock private BbsMasterMapper bbsMasterMapper;
    @Mock private BbsPostMapper bbsPostMapper;
    @Mock private BbsAttachmentMapper bbsAttachmentMapper;

    private AttachmentService attachmentService;

    /** 테스트용 최소 설정 */
    private static final String HMAC_SECRET = "test-hmac-secret-for-unit-tests-only";
    private static final BoardAttachmentProperties TEST_PROPS = new BoardAttachmentProperties(
            "/tmp/test-attachments",
            List.of("image/jpeg", "image/png", "application/pdf"),
            51200L,
            HMAC_SECRET,
            900
    );

    @BeforeEach
    void setUp() {
        attachmentService = new AttachmentServiceImpl(
                bbsMasterMapper, bbsPostMapper, bbsAttachmentMapper, TEST_PROPS
        );
    }

    private BbsAttachment stubAttachment(long id) {
        return BbsAttachment.builder()
                .id(id).postId(1L)
                .fileName("test.jpg").mimeType("image/jpeg")
                .sizeBytes(1024L).scanStatus("PENDING")
                .downloadCount(0L).uploadedBy(1L)
                .storedPath("/tmp/test-attachments/test.jpg")
                .build();
    }

    // ──────────────────────────────────────────────
    // REQ-BOARD-004-Q: 첨부파일 목록 조회
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("첨부파일 목록 조회 — 게시글 ID로 반환")
    void listAttachments_byPostId_returnsAttachments() {
        when(bbsAttachmentMapper.findByPostId(1L)).thenReturn(List.of(stubAttachment(1L)));

        List<AttachmentSummary> result = attachmentService.listAttachments(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).fileName()).isEqualTo("test.jpg");
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

        AttachmentSummary result = attachmentService.uploadAttachment(1L, file, 1L);

        assertThat(result).isNotNull();
        assertThat(result.fileName()).isEqualTo("test.jpg");
        assertThat(result.mimeType()).isEqualTo("image/jpeg");
        verify(bbsAttachmentMapper).insert(any());
    }

    @Test
    @DisplayName("첨부파일 업로드 — 허용되지 않는 MIME 타입 InvalidAttachmentTypeException")
    void uploadAttachment_disallowedMimeType_throwsInvalidTypeException() {
        MultipartFile file = new MockMultipartFile(
                "file", "malware.exe", "application/x-msdownload", new byte[1024]
        );

        assertThatThrownBy(() -> attachmentService.uploadAttachment(1L, file, 1L))
                .isInstanceOf(InvalidAttachmentTypeException.class);
    }

    @Test
    @DisplayName("첨부파일 업로드 — 크기 초과 AttachmentTooLargeException")
    void uploadAttachment_exceedsMaxSize_throwsTooLargeException() {
        // TEST_PROPS.globalMaxSizeKb() = 51200 (50MB)
        // 100MB 파일은 크기 초과
        byte[] largeContent = new byte[(int) (51200L * 1024 + 1)];
        MultipartFile file = new MockMultipartFile(
                "file", "large.pdf", "application/pdf", largeContent
        );

        assertThatThrownBy(() -> attachmentService.uploadAttachment(1L, file, 1L))
                .isInstanceOf(AttachmentTooLargeException.class);
    }

    // ──────────────────────────────────────────────
    // REQ-BOARD-005: HMAC-SHA256 서명 URL
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("다운로드 URL 생성 — HMAC-SHA256 서명 URL TTL 포함")
    void generateDownloadUrl_validAttachment_returnsSignedUrl() {
        when(bbsAttachmentMapper.findById(1L)).thenReturn(Optional.of(stubAttachment(1L)));

        AttachmentDownloadUrl result = attachmentService.generateDownloadUrl(1L, 1L);

        assertThat(result).isNotNull();
        assertThat(result.downloadUrl()).contains("/api/v1/board/attachments/1/download");
        assertThat(result.downloadUrl()).contains("sig=");
        assertThat(result.expiresAt()).isAfter(Instant.now());
    }

    @Test
    @DisplayName("다운로드 URL 검증 — 만료된 토큰은 AttachmentDownloadDeniedException")
    void verifyAndDownload_expiredToken_throwsDownloadDeniedException() {
        // 과거 시각(expiresAt=1)으로 토큰 생성
        long attachmentId = 1L;
        long requesterId = 1L;
        long expiredAt = 1L; // 1970-01-01 — 만료됨
        String payload = attachmentId + "|" + requesterId + "|" + expiredAt;
        String sig = hmacSha256(payload, HMAC_SECRET);
        String expiredToken = attachmentId + "|" + requesterId + "|" + expiredAt + "|" + sig;

        assertThatThrownBy(() -> attachmentService.verifyAndDownload(expiredToken))
                .isInstanceOf(AttachmentDownloadDeniedException.class);
    }

    @Test
    @DisplayName("다운로드 URL 검증 — 유효한 토큰 다운로드 횟수 증가")
    void verifyAndDownload_validToken_incrementsDownloadCount() {
        long attachmentId = 1L;
        long requesterId = 1L;
        long expiresAt = Instant.now().plusSeconds(900).getEpochSecond();
        String payload = attachmentId + "|" + requesterId + "|" + expiresAt;
        String sig = hmacSha256(payload, HMAC_SECRET);
        String validToken = attachmentId + "|" + requesterId + "|" + expiresAt + "|" + sig;

        when(bbsAttachmentMapper.findById(attachmentId)).thenReturn(Optional.of(stubAttachment(attachmentId)));

        AttachmentSummary result = attachmentService.verifyAndDownload(validToken);

        assertThat(result).isNotNull();
        verify(bbsAttachmentMapper).incrementDownloadCount(eq(attachmentId));
    }

    // ─── 테스트용 HMAC-SHA256 헬퍼 ───────────────────────────────────────────

    /** 서비스와 동일한 방식으로 서명 생성 (테스트 검증용). */
    private String hmacSha256(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            byte[] hmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hmac);
        } catch (Exception e) {
            throw new RuntimeException("테스트 HMAC 생성 실패", e);
        }
    }
}
