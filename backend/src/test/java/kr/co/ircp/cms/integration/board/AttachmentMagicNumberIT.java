package kr.co.ircp.cms.integration.board;

import kr.co.ircp.cms.domain.board.dto.AttachmentDownloadUrl;
import kr.co.ircp.cms.domain.board.dto.AttachmentSummary;
import kr.co.ircp.cms.domain.board.exception.AttachmentDownloadDeniedException;
import kr.co.ircp.cms.domain.board.exception.AttachmentTooLargeException;
import kr.co.ircp.cms.domain.board.exception.InvalidAttachmentTypeException;
import kr.co.ircp.cms.domain.board.service.AttachmentService;
import kr.co.ircp.cms.integration.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 첨부파일 MIME 타입 검증 및 보안 다운로드 통합 테스트.
 *
 * // @MX:ANCHOR: [AUTO] AttachmentMagicNumberIT — 파일 타입 검증 및 HMAC 서명 URL 보안 통합 검증
 * // @MX:REASON: AttachmentServiceImpl의 MIME 검증·크기 제한·HMAC 서명 경로가 3개 이상의 비즈니스 룰을 커버 (fan_in >= 3)
 * // @MX:SPEC: REQ-BOARD-004, REQ-BOARD-005
 *
 * <p>REQ-BOARD-004: 파일 MIME 타입 화이트리스트 검증, 크기 제한.
 * REQ-BOARD-005: HMAC-SHA256 서명 URL TTL 만료 및 서명 위변조 거부.
 *
 * <p>업로드 성공 케이스는 post_id=NULL 허용(bbs_attachment FK nullable) 조건을 활용.
 * 파일 사이즈 초과 테스트는 스트림 크기만 조작하여 메모리 효율적으로 검증.
 */
@Transactional
@DisplayName("AttachmentMagicNumberIT — MIME 검증 및 HMAC 서명 URL 보안 (REQ-BOARD-004, REQ-BOARD-005)")
class AttachmentMagicNumberIT extends AbstractIntegrationTest {

    @Autowired
    private AttachmentService attachmentService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // bbs_attachment.post_id는 nullable — 독립 업로드 테스트에서 null 사용
    private static final Long TEST_POST_ID = null;
    private static final long TEST_UPLOADER_ID = 1L;

    @BeforeEach
    void ensureAdminUserExists() {
        // V4__seed_admin_user.sql에서 id=1 users 레코드가 생성되므로 별도 삽입 불필요.
        // 단, 다른 IT 클래스의 @Transactional rollback으로 삭제될 수 있으니 존재 여부 확인
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE id = 1", Integer.class);
        // 없으면 이 테스트는 uploaded_by FK를 null로 처리 (SET NULL on delete)
        // — 실제 uploadedBy는 null로 넘겨도 무방
    }

    // ─── REQ-BOARD-004: MIME 타입 화이트리스트 검증 ────────────────────────────

    @Test
    @DisplayName("REQ-BOARD-004: 허용된 MIME(image/jpeg) 업로드 → 정상 저장")
    void upload_acceptsAllowedJpeg() {
        byte[] jpegBytes = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0,
                0x00, 0x10, 0x4A, 0x46, 0x49, 0x46};
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.jpg", "image/jpeg", jpegBytes
        );

        // post_id=null 허용(bbs_attachment.post_id nullable), uploadedBy=null(FK SET NULL)
        AttachmentSummary result = attachmentService.uploadAttachment(TEST_POST_ID, file, null);

        assertThat(result).isNotNull();
        assertThat(result.mimeType()).isEqualTo("image/jpeg");
        assertThat(result.fileName()).isEqualTo("photo.jpg");
    }

    @Test
    @DisplayName("REQ-BOARD-004: 허용되지 않은 MIME(application/x-msdownload) — EXE를 .jpg로 위장 → InvalidAttachmentTypeException")
    void upload_rejectsExecutableMimeType() {
        // EXE 파일을 .jpg로 위장. contentType이 화이트리스트 외
        byte[] exeBytes = new byte[]{'M', 'Z', 0x00, 0x00};
        MockMultipartFile file = new MockMultipartFile(
                "file", "trojan.jpg", "application/x-msdownload", exeBytes
        );

        assertThatThrownBy(() ->
                attachmentService.uploadAttachment(TEST_POST_ID, file, null)
        )
                .isInstanceOf(InvalidAttachmentTypeException.class)
                .hasMessageContaining("허용되지 않는 파일 형식");
    }

    @Test
    @DisplayName("REQ-BOARD-004: null contentType 파일 → InvalidAttachmentTypeException (unknown)")
    void upload_rejectsNullContentType() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "unknown.bin", (String) null, new byte[]{0x00, 0x01}
        );

        assertThatThrownBy(() ->
                attachmentService.uploadAttachment(TEST_POST_ID, file, null)
        )
                .isInstanceOf(InvalidAttachmentTypeException.class)
                .hasMessageContaining("unknown");
    }

    @Test
    @DisplayName("REQ-BOARD-004: 파일 크기 52KB (globalMaxSizeKb=51200 기본값 초과 아님, 소규모 제한 설정 필요) — 크기 초과 경계 검증")
    void upload_rejectsTooLargeFile_whenSizeExceedsGlobalMax() {
        // globalMaxSizeKb=51200 (50MB). 이 테스트는 서비스 레벨 크기 검증을 확인.
        // 50MB+1byte 배열은 메모리 과다 → 작은 globalMaxSizeKb=1(1KB)로 설정 불가이므로
        // 대신 사이즈 필드를 조작한 MockMultipartFile 서브클래스 사용
        byte[] tinyBytes = new byte[10];
        MockMultipartFile oversizedFile = new MockMultipartFile("file", "large.jpg", "image/jpeg", tinyBytes) {
            @Override
            public long getSize() {
                // globalMaxSizeKb=51200 → 51200*1024 = 52,428,800 bytes 한도 초과값 반환
                return 52_428_801L;
            }
        };

        assertThatThrownBy(() ->
                attachmentService.uploadAttachment(TEST_POST_ID, oversizedFile, null)
        )
                .isInstanceOf(AttachmentTooLargeException.class)
                .hasMessageContaining("첨부파일 크기 초과");
    }

    // ─── REQ-BOARD-005: HMAC 서명 URL 보안 검증 ────────────────────────────────

    @Test
    @DisplayName("REQ-BOARD-005: 위변조된 서명 토큰 → AttachmentDownloadDeniedException (서명 검증 실패)")
    void verifyAndDownload_rejectsTamperedSignature() {
        // 정상 업로드로 attachment ID 획득
        byte[] jpegBytes = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
        MockMultipartFile file = new MockMultipartFile(
                "file", "sig-test.jpg", "image/jpeg", jpegBytes
        );
        AttachmentSummary uploaded = attachmentService.uploadAttachment(TEST_POST_ID, file, null);
        long attachmentId = uploaded.id();

        // 서명 URL 생성 후 서명 부분을 위변조한 토큰 직접 조립
        AttachmentDownloadUrl urlDto = attachmentService.generateDownloadUrl(attachmentId, TEST_UPLOADER_ID);
        long validExpiry = urlDto.expiresAt().getEpochSecond();
        String tamperedToken = attachmentId + "|" + TEST_UPLOADER_ID + "|" + validExpiry + "|fakesignaturexxx";

        assertThatThrownBy(() ->
                attachmentService.verifyAndDownload(tamperedToken)
        )
                .isInstanceOf(AttachmentDownloadDeniedException.class)
                .hasMessageContaining("서명 검증 실패");
    }

    @Test
    @DisplayName("REQ-BOARD-005: 만료된 토큰 (expiresAt=1970년대 과거) → AttachmentDownloadDeniedException (만료된 토큰)")
    void verifyAndDownload_rejectsExpiredToken() {
        long attachmentId = 99999L;
        long requesterId = 1L;
        long expiredAt = 1_000_000L; // Unix epoch 1970년 초 = 확실히 만료
        String expiredToken = attachmentId + "|" + requesterId + "|" + expiredAt + "|anysignature";

        // 만료 확인은 HMAC 검증 전에 수행되므로 attachment 존재 여부와 무관
        assertThatThrownBy(() ->
                attachmentService.verifyAndDownload(expiredToken)
        )
                .isInstanceOf(AttachmentDownloadDeniedException.class)
                .hasMessageContaining("만료된 토큰");
    }
}
