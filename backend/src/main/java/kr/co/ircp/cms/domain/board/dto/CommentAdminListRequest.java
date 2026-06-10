package kr.co.ircp.cms.domain.board.dto;

/**
 * 관리자 댓글 목록 조회 요청 파라미터.
 * SPEC-CMS-COMMENT-MODERATE-001 REQ-CMTM-002 — 게시판/상태/키워드 필터.
 *
 * @param boardId 게시판 ID 필터 (null 이면 전체 게시판)
 * @param status  상태 필터 (ALL/VISIBLE/HIDDEN/DELETED, 기본값 ALL)
 * @param keyword content 부분일치 검색어 (null/빈문자 이면 미적용)
 * @param page    0-based 페이지 번호 (기본값 0)
 * @param size    페이지 크기 (기본값 20)
 */
public record CommentAdminListRequest(
        Long boardId,
        String status,
        String keyword,
        int page,
        int size
) {

    /** 기본값 보정 — status 미지정 시 ALL, size 0 이하면 20 으로 정규화. */
    public CommentAdminListRequest {
        if (status == null || status.isBlank()) {
            status = "ALL";
        }
        if (size <= 0) {
            size = 20;
        }
        if (page < 0) {
            page = 0;
        }
    }

    /** SQL OFFSET 값 (page * size). */
    public int offset() {
        return page * size;
    }
}
