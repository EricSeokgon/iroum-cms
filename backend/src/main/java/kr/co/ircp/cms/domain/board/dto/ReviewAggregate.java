package kr.co.ircp.cms.domain.board.dto;

import java.math.BigDecimal;

/**
 * VISIBLE 리뷰 집계 결과(개수 + 평균 별점).
 * SPEC-CMS-REVIEW-001 REQ-REV-003 — bbs_post.review_count / average_rating 갱신에 사용.
 *
 * @param count VISIBLE 리뷰 수
 * @param average VISIBLE 리뷰 평균 별점 (리뷰 0건이면 null → 서비스에서 0.0 처리)
 */
public record ReviewAggregate(int count, BigDecimal average) {
}
