package kr.co.ircp.cms.domain.board.controller;

import kr.co.ircp.cms.domain.board.dto.AttachmentDownloadUrl;
import kr.co.ircp.cms.domain.board.dto.AttachmentSummary;
import kr.co.ircp.cms.domain.board.service.AttachmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 첨부파일 REST 컨트롤러.
 * REQ-BOARD-004: 첨부파일 업로드 API
 * REQ-BOARD-005: 첨부파일 보안 다운로드 API (HMAC-SHA256 서명 URL)
 */
@RestController
@RequestMapping("/api/v1/boards/{bbsMasterId}/posts/{postId}/attachments")
@RequiredArgsConstructor
public class AttachmentController {

    private final AttachmentService attachmentService;

    /** GET — 첨부파일 목록 조회 */
    @GetMapping
    public ResponseEntity<List<AttachmentSummary>> listAttachments(
            @PathVariable Long bbsMasterId,
            @PathVariable Long postId
    ) {
        return ResponseEntity.ok(attachmentService.listAttachments(postId));
    }

    /** POST — 첨부파일 업로드 */
    @PostMapping
    public ResponseEntity<AttachmentSummary> uploadAttachment(
            @PathVariable Long bbsMasterId,
            @PathVariable Long postId,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal Long uploaderId
    ) {
        return ResponseEntity.ok(attachmentService.uploadAttachment(postId, file, uploaderId));
    }

    /**
     * GET /{attachmentId}/download-url — HMAC-SHA256 서명 다운로드 URL 발급.
     * REQ-BOARD-005: TTL 15분, 서명 검증 실패 시 403
     */
    @GetMapping("/{attachmentId}/download-url")
    public ResponseEntity<AttachmentDownloadUrl> generateDownloadUrl(
            @PathVariable Long bbsMasterId,
            @PathVariable Long postId,
            @PathVariable Long attachmentId,
            @AuthenticationPrincipal Long requesterId
    ) {
        return ResponseEntity.ok(attachmentService.generateDownloadUrl(attachmentId, requesterId));
    }

    /** DELETE /{attachmentId} — 첨부파일 삭제 */
    @DeleteMapping("/{attachmentId}")
    public ResponseEntity<Void> deleteAttachment(
            @PathVariable Long bbsMasterId,
            @PathVariable Long postId,
            @PathVariable Long attachmentId,
            @AuthenticationPrincipal Long requesterId
    ) {
        attachmentService.deleteAttachment(attachmentId, requesterId);
        return ResponseEntity.noContent().build();
    }
}
