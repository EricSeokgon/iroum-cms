package kr.co.ircp.cms.common.exception;

/**
 * 콘텐츠 리비전 낙관적 잠금 충돌 예외.
 *
 * <p>SPEC-CMS-CONTENT-REVISION-001 REQ-REV-005 — 게시물·페이지 수정 시 클라이언트가 보낸
 * expectedVersion 이 서버의 현재 버전과 불일치(이미 다른 사용자가 수정)하면 발생한다.
 * 전역 예외 핸들러에서 HTTP 409 Conflict + RFC 9457 ProblemDetail
 * (code=REVISION_CONFLICT, currentVersion) 로 매핑된다.
 *
 * <p>게시물·페이지 도메인이 공유하는 유일한 교차 예외이므로 common.exception 에 둔다.
 */
public class RevisionConflictException extends RuntimeException {

    /** ProblemDetail code 속성 값 (응답 계약 고정). */
    public static final String CODE = "REVISION_CONFLICT";

    /** 충돌 시점의 서버 현재 버전. 클라이언트가 최신 상태로 재시도하도록 안내. */
    private final long currentVersion;

    public RevisionConflictException(long currentVersion) {
        super("다른 사용자가 먼저 수정했습니다. 최신 내용을 확인 후 다시 시도해 주세요. currentVersion=" + currentVersion);
        this.currentVersion = currentVersion;
    }

    public long getCurrentVersion() {
        return currentVersion;
    }
}
