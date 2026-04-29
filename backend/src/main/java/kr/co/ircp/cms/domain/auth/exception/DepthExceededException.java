package kr.co.ircp.cms.domain.auth.exception;

/**
 * 조직 트리 최대 깊이(5) 초과 시 발생하는 예외.
 *
 * <p>SPEC-CMS-002 REQ-AUTH-014 — HTTP 400 Bad Request 매핑.
 * DB CHECK 제약 (depth BETWEEN 0 AND 5)과 동기화.
 */
public class DepthExceededException extends RuntimeException {

    public DepthExceededException(int requestedDepth) {
        super("조직 트리 최대 깊이(5)를 초과합니다: 요청 깊이=" + requestedDepth);
    }
}
