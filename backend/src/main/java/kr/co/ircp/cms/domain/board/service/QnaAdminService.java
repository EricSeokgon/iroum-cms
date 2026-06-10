package kr.co.ircp.cms.domain.board.service;

import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.board.dto.QnaSummary;

/**
 * Q&A 어드민 모더레이션 서비스.
 * SPEC-CMS-QNA-MODERATE-001
 */
public interface QnaAdminService {

    /** REQ-QNA-ADM-001: HIDDEN 포함 전체 목록 조회 */
    PageResponse<QnaSummary> listAll(int page, int size, String status, String keyword);

    /** REQ-QNA-ADM-002: 상태 변경 */
    QnaSummary changeStatus(Long id, String status);

    /** REQ-QNA-ADM-003: 소프트 삭제 */
    void delete(Long id);
}
