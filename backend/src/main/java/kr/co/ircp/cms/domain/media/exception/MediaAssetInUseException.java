package kr.co.ircp.cms.domain.media.exception;

import kr.co.ircp.cms.domain.media.entity.MediaAssetUsage;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.List;

/**
 * 활성 사용처가 있는 자산 삭제 시도.
 * REQ-MEDIA-004-D-2: HTTP 409
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class MediaAssetInUseException extends RuntimeException {

    private final List<MediaAssetUsage> usages;

    public MediaAssetInUseException(List<MediaAssetUsage> usages) {
        super("사용 중인 미디어 자산은 삭제할 수 없습니다. 활성 사용처: " + usages.size() + "건");
        this.usages = usages;
    }

    public List<MediaAssetUsage> getUsages() {
        return usages;
    }
}
