package kr.co.ircp.cms.domain.media.dto;

import kr.co.ircp.cms.domain.media.entity.MediaStatus;
import kr.co.ircp.cms.domain.media.entity.MediaType;

import java.time.Instant;
import java.util.List;

/**
 * 미디어 자산 검색 파라미터 DTO.
 * REQ-MEDIA-003-D-1~4
 */
public record MediaSearchRequest(
        MediaType type,
        MediaStatus status,
        Long uploadedBy,
        List<String> tags,
        Instant from,
        Instant to,
        int page,
        int size
) {
    public MediaSearchRequest {
        if (page < 0) page = 0;
        if (size <= 0 || size > 100) size = 20;
        if (to == null) to = Instant.now();
    }
}
