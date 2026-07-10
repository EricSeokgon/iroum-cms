package kr.co.ircp.cms.domain.content.block.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 공유 콘텐츠 블록 생성/수정 요청 DTO.
 *
 * <p>SPEC-CMS-CONTENT-BLOCK-001 REQ-CB-009 — slug 형식 ^[a-z0-9]+(-[a-z0-9]+)*$ (최대 100자).
 *
 * <p>클래스명에 Shared prefix 를 부여한 이유: MyBatis {@code type-aliases-package}
 * (kr.co.ircp.cms.domain) 가 단순 클래스명을 alias 로 등록하므로,
 * content.page.dto.ContentBlockRequest 와 단순명이 충돌하면 SqlSessionFactory 가 깨진다.
 */
public record SharedContentBlockRequest(

        @NotBlank
        @Size(max = 200)
        String name,

        @NotBlank
        @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$", message = "슬러그 형식이 올바르지 않습니다")
        @Size(max = 100)
        String slug,

        @NotBlank
        String blockType,

        String contentHtml,

        String contentRaw,

        @Size(max = 500)
        String description,

        String status
) {}
