package kr.co.ircp.cms.domain.board.service;

import kr.co.ircp.cms.domain.board.dto.AttachmentDownloadUrl;
import kr.co.ircp.cms.domain.board.dto.AttachmentSummary;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 첨부파일 서비스 인터페이스.
 * REQ-BOARD-004: 첨부파일 업로드
 * REQ-BOARD-005: 첨부파일 보안 다운로드
 *
 * // @MX:ANCHOR: [AUTO] AttachmentService — 첨부파일 비즈니스 계약
 * // @MX:REASON: AttachmentController, PostDetail 조립, 다운로드 URL 서명 생성 (fan_in >= 3)
 * // @MX:SPEC: REQ-BOARD-004, REQ-BOARD-005
 */
public interface AttachmentService {

    /** 게시글 첨부파일 목록 조회 */
    List<AttachmentSummary> listAttachments(Long postId);

    /**
     * 첨부파일 업로드.
     * 게시판의 maxAttachmentCount, maxAttachmentSizeKb, 허용 MIME 타입 검증 포함.
     *
     * @param postId     게시글 ID
     * @param file       업로드 파일
     * @param uploaderId 업로더 사용자 ID
     */
    AttachmentSummary uploadAttachment(Long postId, MultipartFile file, Long uploaderId);

    /**
     * HMAC-SHA256 서명된 다운로드 URL 생성.
     * TTL 15분, 서명 검증 실패 또는 만료 시 AttachmentDownloadDeniedException 발생.
     */
    AttachmentDownloadUrl generateDownloadUrl(Long attachmentId, Long requesterId);

    /**
     * 서명 URL 검증 후 다운로드 처리.
     * download_count 증가 포함.
     *
     * @param token HMAC-SHA256 서명 토큰
     */
    AttachmentSummary verifyAndDownload(String token);

    /** 첨부파일 삭제 */
    void deleteAttachment(Long attachmentId, Long requesterId);
}
