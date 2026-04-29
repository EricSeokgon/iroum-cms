package kr.co.ircp.cms.domain.media.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.UUID;

/** 미디어 자산을 찾을 수 없는 경우 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class MediaNotFoundException extends RuntimeException {
    public MediaNotFoundException(UUID uuid) {
        super("미디어 자산을 찾을 수 없습니다: " + uuid);
    }
    public MediaNotFoundException(Long id) {
        super("미디어 자산을 찾을 수 없습니다: id=" + id);
    }
}
