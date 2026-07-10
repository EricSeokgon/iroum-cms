package kr.co.ircp.cms.domain.content.block.exception;

/**
 * 공유 콘텐츠 블록 slug 중복 예외.
 *
 * <p>SPEC-CMS-CONTENT-BLOCK-001 REQ-CB-011 — 이미 사용 중인 slug 생성 시도 → 409.
 */
public class ContentBlockSlugDuplicateException extends RuntimeException {

    /** 응답 에러 코드. */
    public static final String CODE = "BLOCK_SLUG_DUPLICATE";

    public ContentBlockSlugDuplicateException(String slug) {
        super("이미 사용 중인 슬러그입니다: " + slug);
    }
}
