package kr.co.ircp.cms.domain.board.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * MimeTypeValidator 단위 테스트.
 *
 * <p>SPEC-CMS-SECURITY-HIGH-9 — 매직 바이트 기반 MIME 타입 위변조 검증.
 * 클라이언트가 Content-Type 헤더만 조작한 위장 파일을 거부할 수 있어야 한다.
 */
@DisplayName("MimeTypeValidator — 매직바이트 기반 MIME 검증")
class MimeTypeValidatorTest {

    private final MimeTypeValidator validator = new MimeTypeValidator();

    // ─── 정상 매직바이트 통과 ────────────────────────────────────────────

    @Test
    @DisplayName("valid PDF 시그니처(%PDF-)와 claim=application/pdf — 통과")
    void validate_pdfBytes_matchesPdfClaim() {
        byte[] pdfHeader = {0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x35}; // %PDF-1.5
        MockMultipartFile file = new MockMultipartFile(
                "file", "doc.pdf", "application/pdf", pdfHeader);

        assertThatNoException()
                .isThrownBy(() -> validator.validate(file, "application/pdf"));
    }

    @Test
    @DisplayName("valid JPEG 시그니처(FF D8 FF)와 claim=image/jpeg — 통과")
    void validate_jpegBytes_matchesJpegClaim() {
        byte[] jpegHeader = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0,
                0x00, 0x10, 0x4A, 0x46}; // JPEG/JFIF
        MockMultipartFile file = new MockMultipartFile(
                "file", "img.jpg", "image/jpeg", jpegHeader);

        assertThatNoException()
                .isThrownBy(() -> validator.validate(file, "image/jpeg"));
    }

    @Test
    @DisplayName("valid PNG 시그니처와 claim=image/png — 통과")
    void validate_pngBytes_matchesPngClaim() {
        byte[] pngHeader = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
                0x00, 0x00, 0x00, 0x0D};
        MockMultipartFile file = new MockMultipartFile(
                "file", "img.png", "image/png", pngHeader);

        assertThatNoException()
                .isThrownBy(() -> validator.validate(file, "image/png"));
    }

    @Test
    @DisplayName("valid GIF89a 시그니처와 claim=image/gif — 통과")
    void validate_gifBytes_matchesGifClaim() {
        byte[] gifHeader = {0x47, 0x49, 0x46, 0x38, 0x39, 0x61, 0x01, 0x00};
        MockMultipartFile file = new MockMultipartFile(
                "file", "img.gif", "image/gif", gifHeader);

        assertThatNoException()
                .isThrownBy(() -> validator.validate(file, "image/gif"));
    }

    @Test
    @DisplayName("valid ZIP 시그니처(PK..)와 OOXML claim(DOCX) — 통과")
    void validate_zipBytes_matchesOoxmlClaim() {
        byte[] zipHeader = {0x50, 0x4B, 0x03, 0x04, 0x14, 0x00, 0x00, 0x00};
        MockMultipartFile file = new MockMultipartFile(
                "file", "doc.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                zipHeader);

        assertThatNoException().isThrownBy(() -> validator.validate(file,
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
    }

    @Test
    @DisplayName("plain text claim=text/plain — ASCII 텍스트 휴리스틱 통과")
    void validate_plainText_matchesTextClaim() {
        // 충분히 긴 ASCII 인쇄가능 문자
        byte[] textHeader = "Hello, world!\nLine2".getBytes();
        MockMultipartFile file = new MockMultipartFile(
                "file", "note.txt", "text/plain", textHeader);

        assertThatNoException()
                .isThrownBy(() -> validator.validate(file, "text/plain"));
    }

    // ─── 위변조 거부 ──────────────────────────────────────────────────────

    @Test
    @DisplayName("JPEG 바이트인데 claim=application/pdf — 거부 (MimeTypeMismatchException)")
    void validate_jpegBytesWithPdfClaim_throws() {
        byte[] jpegHeader = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0,
                0x00, 0x10, 0x4A, 0x46};
        MockMultipartFile file = new MockMultipartFile(
                "file", "fake.pdf", "application/pdf", jpegHeader);

        assertThatThrownBy(() -> validator.validate(file, "application/pdf"))
                .isInstanceOf(MimeTypeValidator.MimeTypeMismatchException.class)
                .satisfies(e -> {
                    MimeTypeValidator.MimeTypeMismatchException ex =
                            (MimeTypeValidator.MimeTypeMismatchException) e;
                    assertThat(ex.claimedMime()).isEqualTo("application/pdf");
                    assertThat(ex.detectedMime()).isEqualTo("image/jpeg");
                });
    }

    @Test
    @DisplayName("PDF 바이트인데 claim=image/jpeg — 거부")
    void validate_pdfBytesWithJpegClaim_throws() {
        byte[] pdfHeader = {0x25, 0x50, 0x44, 0x46, 0x2D, 0x31};
        MockMultipartFile file = new MockMultipartFile(
                "file", "fake.jpg", "image/jpeg", pdfHeader);

        assertThatThrownBy(() -> validator.validate(file, "image/jpeg"))
                .isInstanceOf(MimeTypeValidator.MimeTypeMismatchException.class);
    }

    @Test
    @DisplayName("미지원 매직바이트(application/octet-stream) + claim=application/pdf — 거부")
    void validate_unknownBytes_throws() {
        byte[] unknown = {0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08};
        MockMultipartFile file = new MockMultipartFile(
                "file", "weird.pdf", "application/pdf", unknown);

        assertThatThrownBy(() -> validator.validate(file, "application/pdf"))
                .isInstanceOf(MimeTypeValidator.MimeTypeMismatchException.class)
                .satisfies(e -> {
                    MimeTypeValidator.MimeTypeMismatchException ex =
                            (MimeTypeValidator.MimeTypeMismatchException) e;
                    assertThat(ex.detectedMime()).isEqualTo("application/octet-stream");
                });
    }

    @Test
    @DisplayName("empty 파일 — 거부 (detected=empty)")
    void validate_emptyFile_throws() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "empty.pdf", "application/pdf", new byte[0]);

        assertThatThrownBy(() -> validator.validate(file, "application/pdf"))
                .isInstanceOf(MimeTypeValidator.MimeTypeMismatchException.class);
    }

    @Test
    @DisplayName("null 파일 — 거부")
    void validate_nullFile_throws() {
        assertThatThrownBy(() -> validator.validate(null, "application/pdf"))
                .isInstanceOf(MimeTypeValidator.MimeTypeMismatchException.class);
    }

    @Test
    @DisplayName("blank claim — 거부 (detected=missing)")
    void validate_blankClaim_throws() {
        byte[] pdfHeader = {0x25, 0x50, 0x44, 0x46, 0x2D};
        MockMultipartFile file = new MockMultipartFile(
                "file", "doc.pdf", "", pdfHeader);

        assertThatThrownBy(() -> validator.validate(file, ""))
                .isInstanceOf(MimeTypeValidator.MimeTypeMismatchException.class)
                .satisfies(e -> {
                    MimeTypeValidator.MimeTypeMismatchException ex =
                            (MimeTypeValidator.MimeTypeMismatchException) e;
                    assertThat(ex.detectedMime()).isEqualTo("missing");
                });
    }

    @Test
    @DisplayName("claim에 charset 파라미터가 포함되어도 정규화 후 비교")
    void validate_claimWithCharsetParameter_normalizesAndPasses() {
        byte[] pdfHeader = {0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x35};
        MockMultipartFile file = new MockMultipartFile(
                "file", "doc.pdf", "application/pdf; charset=binary", pdfHeader);

        assertThatNoException()
                .isThrownBy(() -> validator.validate(file, "application/pdf; charset=binary"));
    }

    // ─── detect 헬퍼 ──────────────────────────────────────────────────────

    @Test
    @DisplayName("detect — PDF 매직바이트 → application/pdf 반환")
    void detect_pdfBytes_returnsPdfMime() {
        byte[] pdfHeader = {0x25, 0x50, 0x44, 0x46, 0x2D};
        MockMultipartFile file = new MockMultipartFile(
                "file", "doc.pdf", "application/pdf", pdfHeader);

        assertThat(validator.detect(file)).isEqualTo("application/pdf");
    }

    @Test
    @DisplayName("detect — 알 수 없는 바이트 → application/octet-stream 반환")
    void detect_unknownBytes_returnsOctetStream() {
        byte[] unknown = {0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08};
        MockMultipartFile file = new MockMultipartFile(
                "file", "weird", "application/octet-stream", unknown);

        assertThat(validator.detect(file)).isEqualTo("application/octet-stream");
    }
}
