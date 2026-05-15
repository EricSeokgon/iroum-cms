package kr.co.ircp.cms.domain.content.page.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Data;

import java.time.Instant;

/**
 * 페이지 변경 이력 엔티티 (풀 스냅샷).
 * REQ-CONTENT-005-D-2/7: 수정 이력 누적, 롤백
 *
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageHistory {

    private Long id;
    private Long pageId;
    private int version;
    /** page row + content_block 배열 + i18n_resource 배열을 jsonb로 통째 저장 */
    private String snapshot;
    private Long editedBy;
    private Instant editedAt;
    /** 변경 사유 (예: ROLLBACK_FROM_v3, SLUG_CHANGE) */
    private String changeSummary;
}
