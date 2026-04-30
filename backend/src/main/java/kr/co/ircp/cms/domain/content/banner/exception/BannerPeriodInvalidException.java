package kr.co.ircp.cms.domain.content.banner.exception;

/**
 * 배너 노출 기간이 유효하지 않을 때 발생하는 예외.
 * REQ-CONTENT-009-D-1: display_from < display_until 검증
 */
public class BannerPeriodInvalidException extends IllegalArgumentException {

    public BannerPeriodInvalidException() {
        super("배너 노출 기간이 유효하지 않습니다. display_from은 display_until보다 이전이어야 합니다.");
    }
}
