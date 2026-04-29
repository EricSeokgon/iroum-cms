package kr.co.ircp.cms.domain.media.dto;

import java.time.Instant;

/**
 * 미디어 자산 사용처 항목 DTO.
 * REQ-MEDIA-003-D-5: 사용처 조회 응답
 */
public record MediaUsageEntry(
        Long id,
        String usedIn,
        Long referenceId,
        String referenceTable,
        Instant usedAt
) {
}
