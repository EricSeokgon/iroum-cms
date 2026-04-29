package kr.co.ircp.cms.domain.board.service;

import kr.co.ircp.cms.domain.board.config.BoardAttachmentProperties;
import kr.co.ircp.cms.domain.board.dto.AttachmentDownloadUrl;
import kr.co.ircp.cms.domain.board.dto.AttachmentSummary;
import kr.co.ircp.cms.domain.board.repository.BbsAttachmentMapper;
import kr.co.ircp.cms.domain.board.repository.BbsMasterMapper;
import kr.co.ircp.cms.domain.board.repository.BbsPostMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 첨부파일 서비스 구현체.
 * REQ-BOARD-004: 첨부파일 업로드
 * REQ-BOARD-005: HMAC-SHA256 서명 URL 보안 다운로드
 *
 * // @MX:TODO: [AUTO] Step 2 GREEN — HMAC 서명 생성·검증 구현 필요. 현재 스텁 상태.
 * // @MX:WARN: [AUTO] HMAC-SHA256 서명 키 관리 — 환경변수에서 로드해야 함. 절대 하드코딩 금지.
 * // @MX:REASON: 서명 키 노출 시 모든 첨부파일 무단 다운로드 가능
 * // @MX:SPEC: REQ-BOARD-004, REQ-BOARD-005
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AttachmentServiceImpl implements AttachmentService {

    private final BbsMasterMapper bbsMasterMapper;
    private final BbsPostMapper bbsPostMapper;
    private final BbsAttachmentMapper bbsAttachmentMapper;
    private final BoardAttachmentProperties attachmentProperties;

    @Override
    public List<AttachmentSummary> listAttachments(Long postId) {
        throw new UnsupportedOperationException("Step 2 GREEN 대기");
    }

    @Override
    @Transactional
    public AttachmentSummary uploadAttachment(Long postId, MultipartFile file, Long uploaderId) {
        throw new UnsupportedOperationException("Step 2 GREEN 대기");
    }

    @Override
    public AttachmentDownloadUrl generateDownloadUrl(Long attachmentId, Long requesterId) {
        throw new UnsupportedOperationException("Step 2 GREEN 대기");
    }

    @Override
    @Transactional
    public AttachmentSummary verifyAndDownload(String token) {
        throw new UnsupportedOperationException("Step 2 GREEN 대기");
    }

    @Override
    @Transactional
    public void deleteAttachment(Long attachmentId, Long requesterId) {
        throw new UnsupportedOperationException("Step 2 GREEN 대기");
    }
}
