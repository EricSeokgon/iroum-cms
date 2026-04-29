package kr.co.ircp.cms.domain.media.entity;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * 미디어 자산 사용처 추적 엔티티 (Reference Counting).
 * removed_at IS NULL인 행 수가 활성 사용처 수.
 * REQ-MEDIA-004-D-2: 활성 사용처 존재 시 삭제 차단
 */
@Data
@Builder
public class MediaAssetUsage {

    private Long id;
    private Long assetId;
    /** 사용 도메인: POST/PAGE/CONTENT_BLOCK/COMMENT/POPUP/BANNER/EMAIL_TEMPLATE/ATTACHMENT */
    private String usedIn;
    private Long referenceId;
    private String referenceTable;
    private Instant usedAt;
    /** null이면 활성 사용처 */
    private Instant removedAt;
}
