package kr.co.ircp.cms.domain.content.site.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Data;

import java.time.Instant;

/**
 * 사이트 마스터 엔티티.
 * REQ-CONTENT-003-D: 사이트 마스터 정의 (1차 단일 row)
 *
 * // @MX:ANCHOR: [AUTO] Site — 콘텐츠 도메인의 루트 엔티티. 모든 메뉴·페이지·팝업·배너의 site_id 참조 원점
 * // @MX:REASON: MenuService, PageService, PopupService, BannerService에서 fan_in >= 4로 참조
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Site {

    private Long id;
    private String code;
    private String name;
    private String domain;
    private String defaultLanguage;
    private String supportedLanguages;
    private String status;
    private String metadata;
    private Instant createdAt;
    private Instant updatedAt;
}
