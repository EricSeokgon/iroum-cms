package kr.co.ircp.cms.domain.content.template.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 템플릿 생성·수정 요청 DTO.
 * REQ-CONTENT-004-D-1: 템플릿 등록
 */
public record TemplateRequest(
        @NotBlank @Size(max = 50)
        String code,

        @NotBlank @Size(max = 200)
        String name,

        @NotBlank @Pattern(regexp = "FULL|SIDEBAR_LEFT|SIDEBAR_RIGHT|LANDING|BLANK")
        String layoutType,

        /** {{CONTENT}} 슬롯 필수 포함 */
        @NotBlank
        String htmlTemplate,

        /** JSON 배열 형식 */
        String cssAssets,

        /** JSON 배열 형식 */
        String jsAssets,

        String description
) {}
