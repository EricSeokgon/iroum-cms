package kr.co.ircp.cms.domain.board.exception;

/**
 * 게시글 예약/취소 상태 전이 충돌 예외.
 * SPEC-CMS-POST-SCHEDULE-001:
 * - REQ-POST-SCHEDULE-004-2: SCHEDULED 가 아닌 게시글 예약 취소 시도
 * - REQ-POST-SCHEDULE-007-2: DELETED 게시글 예약 시도
 * → HTTP 409 Conflict 로 매핑.
 */
public class PostScheduleConflictException extends RuntimeException {

    public PostScheduleConflictException(String message) {
        super(message);
    }
}
