package kr.co.ircp.cms.domain.board.exception;

/**
 * 요청한 (postId, version) 조합의 게시글 이력 스냅샷이 없을 때 발생하는 예외.
 *
 * <p>SPEC-CMS-POST-HISTORY-001 REQ-PH-005 — HTTP 404로 매핑되며,
 * 빈 본문이나 다른 게시글 데이터를 절대 반환하지 않는다.
 */
public class PostHistoryVersionNotFoundException extends RuntimeException {

    public PostHistoryVersionNotFoundException(Long postId, int version) {
        super("해당 버전의 게시글 이력이 없습니다. postId=" + postId + ", version=" + version);
    }
}
