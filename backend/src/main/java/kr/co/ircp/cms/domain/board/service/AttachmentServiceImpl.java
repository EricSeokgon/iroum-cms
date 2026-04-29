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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 첨부파일 서비스 구현체.
 * REQ-BOARD-004: 첨부파일 업로드
 * REQ-BOARD-005: HMAC-SHA256 서명 URL 보안 다운로드
 *
 * // @MX:NOTE: [AUTO] 파일 크기 및 MIME 타입 검증 후 DB insert. 실제 파일 저장은 생략(테스트 환경).
 * // @MX:WARN: [AUTO] HMAC-SHA256 서명 키 관리 — 환경변수에서 로드해야 함. 절대 하드코딩 금지.
 * // @MX:REASON: 서명 키 노출 시 모든 첨부파일 무단 다운로드 가능
 * // @MX:SPEC: REQ-BOARD-004, REQ-BOARD-005
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AttachmentServiceImpl implements AttachmentService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final BbsMasterMapper bbsMasterMapper;
    private final BbsPostMapper bbsPostMapper;
    private final BbsAttachmentMapper bbsAttachmentMapper;
    private final BoardAttachmentProperties attachmentProperties;

    @Override
    public List<AttachmentSummary> listAttachments(Long postId) {
        return bbsAttachmentMapper.findByPostId(postId).stream()
                .map(this::toSummary)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public AttachmentSummary uploadAttachment(Long postId, MultipartFile file, Long uploaderId) {
        // 크기 검증
        long sizeBytes = file.getSize();
        long maxSizeBytes = attachmentProperties.globalMaxSizeKb() * 1024L;
        if (sizeBytes > maxSizeBytes) {
            throw new AttachmentTooLargeException(sizeBytes / 1024, attachmentProperties.globalMaxSizeKb());
        }

        // MIME 타입 검증
        String mimeType = file.getContentType();
        if (mimeType == null || !attachmentProperties.allowedMimeTypes().contains(mimeType)) {
            throw new InvalidAttachmentTypeException(mimeType != null ? mimeType : "unknown");
        }

        // 체크섬 계산 (실제 바이트 읽기)
        String checksum;
        try {
            byte[] bytes = file.getBytes();
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            checksum = HexFormat.of().formatHex(digest.digest(bytes));
        } catch (Exception e) {
            checksum = "unknown";
        }

        // 저장 경로 생성 (실제 파일 저장은 생략 — 테스트 환경)
        String storedPath = attachmentProperties.storagePath() + "/" + System.currentTimeMillis()
                + "_" + sanitizeFilename(file.getOriginalFilename());

        BbsAttachment attachment = BbsAttachment.builder()
                .postId(postId)
                .fileName(file.getOriginalFilename())
                .storedPath(storedPath)
                .mimeType(mimeType)
                .sizeBytes(sizeBytes)
                .checksumSha256(checksum)
                .scanStatus("PENDING")
                .uploadedBy(uploaderId)
                .build();
        bbsAttachmentMapper.insert(attachment);

        return toSummary(attachment);
    }

    @Override
    public AttachmentDownloadUrl generateDownloadUrl(Long attachmentId, Long requesterId) {
        BbsAttachment attachment = bbsAttachmentMapper.findById(attachmentId)
                .orElseThrow(() -> new AttachmentNotFoundException(attachmentId));

        long expiresAt = Instant.now().plusSeconds(attachmentProperties.downloadTtlSeconds()).getEpochSecond();
        String payload = attachmentId + "|" + requesterId + "|" + expiresAt;
        String signature = hmacSha256(payload, attachmentProperties.hmacSecret());

        String downloadUrl = "/api/v1/board/attachments/" + attachmentId
                + "/download?expires=" + expiresAt + "&sig=" + signature;

        return new AttachmentDownloadUrl(
                attachmentId,
                attachment.getFileName(),
                downloadUrl,
                Instant.ofEpochSecond(expiresAt)
        );
    }

    @Override
    @Transactional
    public AttachmentSummary verifyAndDownload(String token) {
        // token 형식: "attachmentId|requesterId|expiresAt|signature"
        if (token == null || token.isBlank()) {
            throw new AttachmentDownloadDeniedException("토큰이 없습니다");
        }

        String[] parts = token.split("\\|");
        if (parts.length < 4) {
            throw new AttachmentDownloadDeniedException("토큰 형식 오류");
        }

        long attachmentId;
        long requesterId;
        long expiresAt;
        try {
            attachmentId = Long.parseLong(parts[0]);
            requesterId = Long.parseLong(parts[1]);
            expiresAt = Long.parseLong(parts[2]);
        } catch (NumberFormatException e) {
            throw new AttachmentDownloadDeniedException("토큰 파싱 오류");
        }
        String signature = parts[3];

        // 만료 확인
        if (Instant.now().getEpochSecond() > expiresAt) {
            throw new AttachmentDownloadDeniedException("만료된 토큰");
        }

        // HMAC 검증 (constant-time comparison)
        String payload = attachmentId + "|" + requesterId + "|" + expiresAt;
        String expectedSig = hmacSha256(payload, attachmentProperties.hmacSecret());
        if (!MessageDigest.isEqual(
                signature.getBytes(StandardCharsets.UTF_8),
                expectedSig.getBytes(StandardCharsets.UTF_8))) {
            throw new AttachmentDownloadDeniedException("서명 검증 실패");
        }

        BbsAttachment attachment = bbsAttachmentMapper.findById(attachmentId)
                .orElseThrow(() -> new AttachmentNotFoundException(attachmentId));

        bbsAttachmentMapper.incrementDownloadCount(attachmentId);

        return toSummary(attachment);
    }

    @Override
    @Transactional
    public void deleteAttachment(Long attachmentId, Long requesterId) {
        bbsAttachmentMapper.findById(attachmentId)
                .orElseThrow(() -> new AttachmentNotFoundException(attachmentId));
        bbsAttachmentMapper.deleteById(attachmentId);
    }

    // ─── 헬퍼 ────────────────────────────────────────────────────────────────

    private AttachmentSummary toSummary(BbsAttachment a) {
        return new AttachmentSummary(
                a.getId(), a.getPostId(), a.getFileName(),
                a.getMimeType(), a.getSizeBytes(),
                a.getScanStatus(), a.getDownloadCount(), a.getUploadedAt()
        );
    }

    /** HMAC-SHA256 서명 생성. Base64URL 인코딩. */
    private String hmacSha256(String data, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
            mac.init(keySpec);
            byte[] hmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hmac);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC-SHA256 서명 생성 실패", e);
        }
    }

    /** 파일명 특수문자 제거 (경로 트래버설 방지). */
    private String sanitizeFilename(String filename) {
        if (filename == null) return "unknown";
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
