package kr.co.ircp.cms.domain.board.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 게시물 별점 리뷰 엔티티.
 * SPEC-CMS-REVIEW-001 — bbs_comment 와 분리된 평점 가능 리뷰.
 */
// @MX:NOTE: @NoArgsConstructor + @AllArgsConstructor 필수 — MyBatis DefaultObjectFactory 가 리플렉션으로 no-args 생성자 호출.
// @Builder 단독 사용 시 package-private all-args 생성자만 생성되어 MyBatisSystemException 발생 (BbsPost 패턴 동일).
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BbsPostReview {

    private Long id;
    private Long postId;
    private Long authorId;
    private int rating;
    private String content;
    private String status;
    // INET 컬럼 — BbsComment 와 동일하게 String + jdbcType=OTHER 로 매핑.
    private String ipAddress;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant deletedAt;
}
