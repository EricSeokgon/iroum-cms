package kr.co.ircp.cms.domain.board.dto;

import kr.co.ircp.cms.domain.board.entity.BbsPostI18n;

import java.time.Instant;

/**
 * 게시글 번역 응답 DTO.
 * SPEC-CMS-NOTICE-I18N-001: 번역 조회/등록 응답.
 */
public record PostTranslationResponse(
        Long id,
        Long postId,
        String language,
        String title,
        String contentHtml,
        String contentText,
        Instant updatedAt
) {
    /** 엔티티 → 응답 DTO 변환 */
    public static PostTranslationResponse from(BbsPostI18n entity) {
        return new PostTranslationResponse(
                entity.getId(),
                entity.getPostId(),
                entity.getLanguage(),
                entity.getTitle(),
                entity.getContentHtml(),
                entity.getContentText(),
                entity.getUpdatedAt()
        );
    }
}
