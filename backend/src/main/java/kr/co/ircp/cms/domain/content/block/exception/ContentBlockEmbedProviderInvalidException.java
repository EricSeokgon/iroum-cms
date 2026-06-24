package kr.co.ircp.cms.domain.content.block.exception;

/**
 * EMBED 블록 제공자 허용 목록 위반 예외.
 *
 * <p>SPEC-CMS-CONTENT-BLOCK-001 REQ-CB-015 — 허용 도메인(youtube.com, vimeo.com,
 * map.kakao.com) 외 제공자 URL 입력 → 422 Unprocessable Entity.
 */
public class ContentBlockEmbedProviderInvalidException extends RuntimeException {

    /** 응답 에러 코드. */
    public static final String CODE = "BLOCK_EMBED_PROVIDER_INVALID";

    public ContentBlockEmbedProviderInvalidException() {
        super("허용되지 않은 EMBED 제공자입니다. 허용 목록: youtube.com, vimeo.com, map.kakao.com");
    }
}
