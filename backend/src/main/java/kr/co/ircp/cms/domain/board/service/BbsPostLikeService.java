package kr.co.ircp.cms.domain.board.service;

/**
 * 게시글 좋아요 서비스.
 *
 * <p>SPEC-CMS-POINTS-001 REQ-PNT-004 — 1인 1게시글 좋아요 + 최초 1회 포인트 적립.
 */
public interface BbsPostLikeService {

    /**
     * 좋아요 등록. 최초 등록 시에만 포인트를 적립한다.
     *
     * @return 신규 좋아요면 true, 이미 좋아요한 상태(중복)면 false
     */
    boolean like(Long postId, Long userId);

    /** 좋아요 취소. 포인트 회수 없음(REQ-PNT-004). */
    void unlike(Long postId, Long userId);

    /** 좋아요 보유 여부. */
    boolean getLikeStatus(Long postId, Long userId);
}
