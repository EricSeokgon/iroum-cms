package kr.co.ircp.cms.domain.content.seo.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Data;

import java.time.Instant;

/**
 * SEO 리다이렉트 엔티티.
 * REQ-CONTENT-005-D-8: slug 변경 시 자동 INSERT + 수동 관리
 *
 * // @MX:ANCHOR: [AUTO] SeoRedirect — URL 리다이렉트 루트 엔티티
 * // @MX:REASON: SeoRedirectService, PageServiceImpl, SeoRedirectController에서 fan_in >= 3으로 참조
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeoRedirect {

    private Long id;
    /** 구 경로 (slug 포함 전체 path) */
    private String fromPath;
    /** 신 경로 */
    private String toPath;
    /** 301|302 */
    private short httpStatus;
    private boolean active;
    /** 변경 사유 (예: SLUG_CHANGE_PAGE_ID:42) */
    private String reason;
    private Instant createdAt;
}
