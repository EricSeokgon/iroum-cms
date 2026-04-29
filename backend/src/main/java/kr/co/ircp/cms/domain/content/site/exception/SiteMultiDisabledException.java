package kr.co.ircp.cms.domain.content.site.exception;

/**
 * 멀티사이트 옵션이 비활성화된 상태에서 추가 사이트 생성 시도 시 발생하는 예외.
 * REQ-CONTENT-003-D-3: 멀티사이트 활성화 가드
 */
public class SiteMultiDisabledException extends RuntimeException {

    public SiteMultiDisabledException() {
        super("멀티사이트 기능이 비활성화 상태입니다. 추가 사이트를 생성할 수 없습니다.");
    }
}
