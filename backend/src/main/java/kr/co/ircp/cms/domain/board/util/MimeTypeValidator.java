package kr.co.ircp.cms.domain.board.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 매직 바이트 기반 MIME 타입 검증기 (HIGH-9 보안 보강).
 *
 * <p>SPEC-CMS-SECURITY-HIGH-9 — Content-Type 헤더는 클라이언트가 임의로 조작 가능하므로
 * 실제 파일 바이트 시그니처(매직 넘버)를 읽어 클라이언트가 주장하는 MIME 타입과
 * 일치하는지 검증한다.
 *
 * <p>지원 시그니처:
 * <ul>
 *   <li>PDF      — {@code %PDF-} (25 50 44 46 2D)</li>
 *   <li>PNG      — 89 50 4E 47 0D 0A 1A 0A</li>
 *   <li>JPEG     — FF D8 FF</li>
 *   <li>GIF87a/89a — 47 49 46 38 (37|39) 61</li>
 *   <li>ZIP / DOCX / XLSX / PPTX — 50 4B 03 04 또는 50 4B 05 06 / 50 4B 07 08</li>
 *   <li>HWP (구버전 cfbf) — D0 CF 11 E0 A1 B1 1A E1</li>
 *   <li>Plain text — 휴리스틱 (ASCII 비율 95% 이상)</li>
 * </ul>
 *
 * <p>매직 바이트 검출 결과와 claimedMimeType이 일치하지 않으면
 * {@link MimeTypeMismatchException}을 던진다. ZIP 컨테이너(DOCX/XLSX/PPTX)는
 * 클라이언트 claim 을 신뢰하되 ZIP 시그니처 자체는 확인한다.
 *
 * <p>본 클래스는 Apache Tika 의존성 없이 표준 라이브러리만으로 동작하므로
 * 추가 라이브러리 비용이 없으며, 미지원 MIME 은 호출 측 화이트리스트가
 * 거부하도록 위임한다.
 */
// @MX:ANCHOR: [AUTO] MimeTypeValidator — 첨부파일 업로드 매직바이트 게이트
// @MX:REASON: AttachmentServiceImpl + 향후 MediaServiceImpl 매직바이트 검증 통합 위치 (fan_in >= 2 잠재)
// @MX:SPEC: SPEC-CMS-SECURITY-HIGH-9
@Slf4j
@Component
public class MimeTypeValidator {

    /** 매직 바이트 비교용 prefix 길이 — 모든 시그니처 커버 가능한 최대값. */
    private static final int MAGIC_HEADER_BYTES = 16;

    /** ZIP 컨테이너 기반 MIME — DOCX/XLSX/PPTX 등 OOXML 묶음 검증에 활용. */
    private static final Set<String> ZIP_BASED_MIMES = Set.of(
            "application/zip",
            "application/x-zip-compressed",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation"
    );

    /** 텍스트 계열 — 매직 바이트 없이 통과(휴리스틱 검증). text/html 은 XSS 벡터이므로 의도적으로 제외. */
    private static final Set<String> TEXT_BASED_MIMES = Set.of(
            "text/plain",
            "text/csv",
            "application/json"
    );

    /** OOXML MIME — ZIP 시그니처 외 [Content_Types].xml 내부 구조 검증 대상. */
    private static final Set<String> OOXML_MIMES = Set.of(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation"
    );

    /** OOXML ZIP 내부 스캔 최대 엔트리 수 — 폭탄 ZIP 방어. */
    private static final int MAX_OOXML_ZIP_SCAN_ENTRIES = 10;

    /**
     * MultipartFile 첫 {@value #MAGIC_HEADER_BYTES} 바이트를 읽어 claimed MIME 과 일치하는지 검증.
     *
     * @param file             대상 파일
     * @param claimedMimeType  클라이언트가 제출한 Content-Type
     * @throws MimeTypeMismatchException 시그니처 불일치 / 읽기 실패
     */
    public void validate(MultipartFile file, String claimedMimeType) {
        if (file == null || file.isEmpty()) {
            throw new MimeTypeMismatchException(claimedMimeType, "empty");
        }
        if (claimedMimeType == null || claimedMimeType.isBlank()) {
            throw new MimeTypeMismatchException("unknown", "missing");
        }

        byte[] header = readHeader(file);
        if (header.length == 0) {
            throw new MimeTypeMismatchException(claimedMimeType, "empty");
        }

        String detected = detectMime(header);

        // claimedMimeType 정규화 — "image/jpeg; charset=binary" 같은 파라미터 제거
        String normalizedClaim = normalize(claimedMimeType);

        // ZIP 컨테이너 OOXML 등은 detected가 application/zip 이면 통과
        if (ZIP_BASED_MIMES.contains(normalizedClaim) && "application/zip".equals(detected)) {
            // OOXML(DOCX/XLSX/PPTX)은 ZIP 시그니처 외 [Content_Types].xml 내부 구조 추가 검증
            if (OOXML_MIMES.contains(normalizedClaim) && !hasOoxmlContentTypes(file)) {
                log.warn("OOXML structure check failed — fileName={}", file.getOriginalFilename());
                throw new MimeTypeMismatchException(normalizedClaim, "zip-without-ooxml-structure");
            }
            return;
        }

        // 텍스트 계열은 헤더 휴리스틱 통과 시 OK
        if (TEXT_BASED_MIMES.contains(normalizedClaim) && "text/plain".equals(detected)) {
            return;
        }

        if (!normalizedClaim.equalsIgnoreCase(detected)) {
            log.warn("MIME type mismatch — claimed={} detected={} fileName={}",
                    normalizedClaim, detected, file.getOriginalFilename());
            throw new MimeTypeMismatchException(normalizedClaim, detected);
        }
    }

    /**
     * 매직 바이트만으로 추론된 MIME 타입을 반환. (테스트·진단용 헬퍼)
     */
    public String detect(MultipartFile file) {
        return detectMime(readHeader(file));
    }

    // ─── 내부 헬퍼 ───────────────────────────────────────────────────────────

    /**
     * ZIP 내부에서 {@code [Content_Types].xml} 엔트리 존재 여부를 확인한다.
     * 유효한 OOXML 패키지는 반드시 이 파일을 포함한다(ECMA-376 §13.2.2).
     * 폭탄 ZIP 방어를 위해 {@value #MAX_OOXML_ZIP_SCAN_ENTRIES}개 엔트리까지만 스캔한다.
     */
    private boolean hasOoxmlContentTypes(MultipartFile file) {
        try (ZipInputStream zis = new ZipInputStream(file.getInputStream())) {
            ZipEntry entry;
            int scanned = 0;
            while ((entry = zis.getNextEntry()) != null && scanned < MAX_OOXML_ZIP_SCAN_ENTRIES) {
                if ("[Content_Types].xml".equals(entry.getName())) {
                    return true;
                }
                zis.closeEntry();
                scanned++;
            }
        } catch (IOException e) {
            log.debug("OOXML structure check IO error: {}", e.getMessage());
        }
        return false;
    }

    private byte[] readHeader(MultipartFile file) {
        byte[] header = new byte[MAGIC_HEADER_BYTES];
        try (InputStream is = file.getInputStream()) {
            int read = is.read(header);
            if (read <= 0) {
                return new byte[0];
            }
            if (read < MAGIC_HEADER_BYTES) {
                byte[] truncated = new byte[read];
                System.arraycopy(header, 0, truncated, 0, read);
                return truncated;
            }
            return header;
        } catch (IOException e) {
            throw new MimeTypeMismatchException("unknown", "io-error");
        }
    }

    /**
     * 매직 바이트 시그니처 매칭 — 첫 일치 항목 반환.
     *
     * @return 검출 MIME (예: "image/jpeg") 또는 "application/octet-stream"
     */
    private String detectMime(byte[] h) {
        if (startsWith(h, 0x25, 0x50, 0x44, 0x46, 0x2D)) {
            return "application/pdf";
        }
        if (startsWith(h, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) {
            return "image/png";
        }
        if (startsWith(h, 0xFF, 0xD8, 0xFF)) {
            return "image/jpeg";
        }
        if (startsWith(h, 0x47, 0x49, 0x46, 0x38)
                && h.length >= 6
                && (h[4] == 0x37 || h[4] == 0x39) // '7' or '9'
                && h[5] == 0x61) {                // 'a'
            return "image/gif";
        }
        if (startsWith(h, 0x52, 0x49, 0x46, 0x46)
                && h.length >= 12
                && h[8] == 0x57 && h[9] == 0x45 && h[10] == 0x42 && h[11] == 0x50) { // WEBP
            return "image/webp";
        }
        if (startsWith(h, 0x50, 0x4B, 0x03, 0x04)
                || startsWith(h, 0x50, 0x4B, 0x05, 0x06)
                || startsWith(h, 0x50, 0x4B, 0x07, 0x08)) {
            return "application/zip";
        }
        if (startsWith(h, 0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1)) {
            // Compound File Binary Format — HWP 구버전, MS Office 97~2003
            return "application/x-cfb";
        }
        if (isLikelyText(h)) {
            return "text/plain";
        }
        return "application/octet-stream";
    }

    private boolean startsWith(byte[] data, int... expected) {
        if (data.length < expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if ((data[i] & 0xFF) != (expected[i] & 0xFF)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Plain text 휴리스틱 — 헤더 바이트의 95% 이상이 인쇄가능 ASCII 이면 텍스트.
     */
    private boolean isLikelyText(byte[] h) {
        if (h.length == 0) return false;
        int printable = 0;
        for (byte b : h) {
            int v = b & 0xFF;
            if (v == 0x09 || v == 0x0A || v == 0x0D || (v >= 0x20 && v <= 0x7E)) {
                printable++;
            }
        }
        return printable * 100 / h.length >= 95;
    }

    private String normalize(String mime) {
        int sep = mime.indexOf(';');
        return (sep < 0 ? mime : mime.substring(0, sep)).trim().toLowerCase();
    }

    // ─── 예외 ────────────────────────────────────────────────────────────────

    /**
     * 매직 바이트 검증 실패 예외 — 호출 측이 422/400 으로 매핑한다.
     */
    public static class MimeTypeMismatchException extends RuntimeException {
        private final String claimedMime;
        private final String detectedMime;

        public MimeTypeMismatchException(String claimedMime, String detectedMime) {
            super("파일 시그니처 불일치: claimed=" + claimedMime + " detected=" + detectedMime);
            this.claimedMime = claimedMime;
            this.detectedMime = detectedMime;
        }

        public String claimedMime() {
            return claimedMime;
        }

        public String detectedMime() {
            return detectedMime;
        }

        public Map<String, String> details() {
            Map<String, String> m = new HashMap<>();
            m.put("claimedMime", claimedMime);
            m.put("detectedMime", detectedMime);
            return m;
        }
    }
}
