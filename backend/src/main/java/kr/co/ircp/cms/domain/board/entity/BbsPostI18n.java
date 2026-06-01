package kr.co.ircp.cms.domain.board.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 게시글 다국어 번역 엔티티.
 * SPEC-CMS-NOTICE-I18N-001: ko 원본은 bbs_post, en 번역은 bbs_post_i18n에 저장.
 */
// @MX:NOTE: @NoArgsConstructor + @AllArgsConstructor 필수 — MyBatis DefaultObjectFactory가 리플렉션으로 no-args 생성자 호출.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BbsPostI18n {

    private Long id;
    private Long postId;
    private String language;
    private String title;
    private String contentHtml;
    private String contentText;
    private Instant updatedAt;
}
