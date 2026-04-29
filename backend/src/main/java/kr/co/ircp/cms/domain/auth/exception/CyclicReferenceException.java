package kr.co.ircp.cms.domain.auth.exception;

/**
 * 조직 트리 순환 참조 시 발생하는 예외.
 *
 * <p>SPEC-CMS-002 REQ-AUTH-014 — HTTP 400 Bad Request 매핑.
 * 자신의 자손을 부모로 이동하려 할 때 발생.
 */
public class CyclicReferenceException extends RuntimeException {

    public CyclicReferenceException(long orgId, long targetParentId) {
        super("순환 참조가 발생합니다: 조직 " + orgId + "은 " + targetParentId + "의 조상입니다");
    }
}
