package kr.co.ircp.cms.domain.content.template.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Data;

import java.time.Instant;

/**
 * 페이지 템플릿 엔티티.
 * REQ-CONTENT-004-D: 템플릿 정의 (Mustache 슬롯 기반)
 *
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Template {

    private Long id;
    private String code;
    private String name;
    /** FULL|SIDEBAR_LEFT|SIDEBAR_RIGHT|LANDING|BLANK */
    private String layoutType;
    /** Mustache 슬롯: {{HEADER}} {{CONTENT}} {{FOOTER}} 필수 */
    private String htmlTemplate;
    /** JSON 배열 (URL 문자열) */
    private String cssAssets;
    /** JSON 배열 (URL 문자열) */
    private String jsAssets;
    private String description;
    private String status;
    private Instant createdAt;
    private Instant updatedAt;
}
