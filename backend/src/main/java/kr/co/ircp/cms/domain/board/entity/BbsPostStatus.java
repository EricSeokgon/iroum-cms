package kr.co.ircp.cms.domain.board.entity;

/**
 * 게시글 상태 enum.
 * REQ-BOARD-002-D: DRAFT, PUBLISHED, HIDDEN, DELETED
 * SPEC-CMS-POST-SCHEDULE-001: SCHEDULED 추가 (예약 발행)
 */
public enum BbsPostStatus {
    DRAFT,
    SCHEDULED,
    PUBLISHED,
    HIDDEN,
    DELETED
}
