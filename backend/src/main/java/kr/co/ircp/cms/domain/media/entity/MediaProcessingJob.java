package kr.co.ircp.cms.domain.media.entity;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * 미디어 비동기 후처리 작업 큐 엔티티.
 * REQ-MEDIA-002-D: EXIF_STRIP → WEBP_CONVERT → THUMBNAIL
 *
 * // @MX:WARN: [AUTO] AV_SCAN job_type 미도입 (Q-3 v0.2+ 후속 검토)
 * // @MX:REASON: ClamAV 데몬 미설치 환경에서 AV_SCAN 등록 시 DB CHECK 제약 위반. v0.2+ 별도 마이그레이션 후 도입.
 */
@Data
@Builder
public class MediaProcessingJob {

    private Long id;
    private Long assetId;
    /** WEBP_CONVERT / THUMBNAIL / EXIF_STRIP (AV_SCAN은 v0.2+ 후속) */
    private String jobType;
    private JobStatus status;
    private Instant startedAt;
    private Instant finishedAt;
    private String errorMessage;
}
