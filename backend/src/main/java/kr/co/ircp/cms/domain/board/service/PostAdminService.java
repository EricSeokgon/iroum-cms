package kr.co.ircp.cms.domain.board.service;

import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.board.dto.PostAdminSummary;

/**
 * 게시글 관리자 모더레이션 서비스.
 * SPEC-CMS-POST-MODERATE-001
 */
public interface PostAdminService {

    /** REQ-PA-001: 전체 게시글 목록 (교차 게시판, 필터 지원). */
    PageResponse<PostAdminSummary> listAll(Long bbsId, int page, int size, String status, String keyword);

    /** REQ-PA-002: 게시글 상태 변경 (PUBLISHED / HIDDEN / DRAFT). */
    PostAdminSummary changeStatus(Long id, String status);

    /** REQ-PA-003: 게시글 강제 삭제 (소프트 삭제). */
    void delete(Long id);
}
