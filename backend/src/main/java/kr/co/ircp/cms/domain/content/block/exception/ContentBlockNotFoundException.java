package kr.co.ircp.cms.domain.content.block.exception;

/**
 * 공유 콘텐츠 블록 미존재 예외.
 *
 * <p>SPEC-CMS-CONTENT-BLOCK-001 REQ-CB-012 — id에 해당하는 블록이 없는 경우 → 404.
 */
public class ContentBlockNotFoundException extends RuntimeException {

    /** 응답 에러 코드. */
    public static final String CODE = "BLOCK_NOT_FOUND";

    public ContentBlockNotFoundException(Long id) {
        super("콘텐츠 블록을 찾을 수 없습니다: " + id);
    }
}
