package kr.co.ircp.cms.domain.media.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * 매직넘버 불일치 또는 MIME 화이트리스트 외 파일.
 * REQ-MEDIA-001-D-5: HTTP 415 응답
 */
@ResponseStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
public class InvalidMimeTypeException extends RuntimeException {
    public InvalidMimeTypeException(String detectedMime) {
        super("허용되지 않는 MIME 타입입니다: " + detectedMime);
    }
}
