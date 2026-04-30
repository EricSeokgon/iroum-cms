package kr.co.ircp.cms.domain.content.i18n.entity;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * 다국어 리소스 엔티티.
 * REQ-CONTENT-010-D: 다국어 리소스 정규화 테이블
 *
 * // @MX:ANCHOR: [AUTO] I18nResource — 다국어 리소스 루트 엔티티
 * // @MX:REASON: I18nResolver, I18nController에서 fan_in >= 3으로 참조
 */
@Data
@Builder
public class I18nResource {

    private Long id;
    /** menu|page|popup|banner|content_block|system */
    private String namespace;
    /** 대상 리소스 ID */
    private Long resourceId;
    /** ko|en */
    private String language;
    /** 예: menu.name, page.title, popup.content_html */
    private String fieldName;
    private String value;
    private Instant createdAt;
    private Instant updatedAt;
}
