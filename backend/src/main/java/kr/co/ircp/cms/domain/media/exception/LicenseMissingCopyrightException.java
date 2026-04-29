package kr.co.ircp.cms.domain.media.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * CC_BY·CC_BY_NC 라이선스인데 copyright_holder 누락.
 * REQ-MEDIA-004-D-4: HTTP 400
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class LicenseMissingCopyrightException extends RuntimeException {
    public LicenseMissingCopyrightException(String licenseType) {
        super("라이선스 " + licenseType + "은(는) 저작권자(copyright_holder) 입력이 필수입니다.");
    }
}
