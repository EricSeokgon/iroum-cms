package kr.co.ircp.cms.domain.board.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 게시글 번역 등록/수정 요청 DTO.
 * SPEC-CMS-NOTICE-I18N-001: 번역 upsert 입력.
 * contentText는 null 허용 — 서비스에서 contentHtml로부터 파생.
 */
public record PostTranslationRequest(
        @NotNull
        @Pattern(regexp = "en|ko", message = "language는 en 또는 ko만 허용됩니다.")
        String language,

        @NotBlank
        @Size(max = 500)
        String title,

        String contentHtml,

        String contentText
) {
}
