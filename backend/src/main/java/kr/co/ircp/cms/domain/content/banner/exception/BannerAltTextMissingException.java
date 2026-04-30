package kr.co.ircp.cms.domain.content.banner.exception;

/**
 * 배너 alt_text가 비어있을 때 발생하는 예외.
 * REQ-CONTENT-009-D-1: KWCAG 2.2 AA 1.1.1 대체텍스트 검증
 */
public class BannerAltTextMissingException extends IllegalArgumentException {

    public BannerAltTextMissingException() {
        super("배너 alt_text는 KWCAG 2.2 AA 1.1.1 준수를 위해 필수입니다.");
    }
}
