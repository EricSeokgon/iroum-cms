package kr.co.ircp.cms.domain.board.service;

import kr.co.ircp.cms.domain.board.dto.ReviewCreateRequest;
import kr.co.ircp.cms.domain.board.dto.ReviewResponse;

import java.util.List;

/**
 * 게시물 별점 리뷰 공개 서비스.
 * SPEC-CMS-REVIEW-001 REQ-REV-001/003/005
 */
public interface ReviewService {

    /**
     * 리뷰 작성 (VISIBLE) 후 게시물 집계 재계산. REQ-REV-001/009
     *
     * @param postId    대상 게시물 ID
     * @param request   별점(1~5) + 리뷰 텍스트
     * @param authorId  작성자 ID (인증 사용자)
     * @param ipAddress 작성 IP (선택)
     * @return 생성된 리뷰 응답
     */
    ReviewResponse createReview(Long postId, ReviewCreateRequest request, Long authorId, String ipAddress);

    /** 게시물 공개 리뷰 목록 (VISIBLE 만). REQ-REV-005 */
    List<ReviewResponse> listByPost(Long postId);

    /**
     * 게시물 VISIBLE 리뷰 집계(review_count, average_rating) 재계산. REQ-REV-003/009/010
     * full-recompute 방식 — 증분 갱신이 아니라 매번 전체 재집계하여 race condition 을 방지한다.
     */
    void recalculateAggregate(Long postId);
}
