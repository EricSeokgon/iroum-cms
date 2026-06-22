package kr.co.ircp.cms.domain.board.service;

import kr.co.ircp.cms.domain.board.dto.ReviewAggregate;
import kr.co.ircp.cms.domain.board.dto.ReviewCreateRequest;
import kr.co.ircp.cms.domain.board.dto.ReviewResponse;
import kr.co.ircp.cms.domain.board.entity.BbsPostReview;
import kr.co.ircp.cms.domain.board.exception.PostNotFoundException;
import kr.co.ircp.cms.domain.board.repository.BbsPostMapper;
import kr.co.ircp.cms.domain.board.repository.BbsPostReviewMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 게시물 별점 리뷰 공개 서비스 구현체.
 * SPEC-CMS-REVIEW-001 REQ-REV-001/003/005/009
 *
 * // @MX:ANCHOR: [AUTO] ReviewServiceImpl — 리뷰 생성·집계 불변 계약
 * // @MX:REASON: recalculateAggregate 가 createReview + ReviewAdminService(hide/delete) 에서 호출 (fan_in >= 3).
 * //             평균 별점은 VISIBLE 리뷰만 모수로 한다는 집계 일관성이 핵심 불변식.
 * // @MX:SPEC: SPEC-CMS-REVIEW-001
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewServiceImpl implements ReviewService {

    /** 리뷰 0건일 때 average_rating 기본값 (DB DEFAULT 와 일치). */
    private static final BigDecimal ZERO_RATING = new BigDecimal("0.0");

    private final BbsPostReviewMapper reviewMapper;
    private final BbsPostMapper postMapper;

    @Override
    @Transactional
    public ReviewResponse createReview(Long postId, ReviewCreateRequest request,
                                       Long authorId, String ipAddress) {
        // 게시물 존재 검증 (FK CASCADE 대상이지만 명확한 404 를 위해 선검증).
        postMapper.findById(postId)
                .orElseThrow(() -> new PostNotFoundException(postId));

        // 별점 범위 방어 검증 (DTO @Min/@Max 와 DB CHECK 외 서비스 계층 이중 방어). REQ-REV-008
        int rating = request.rating();
        if (rating < 1 || rating > 5) {
            throw new IllegalArgumentException("별점은 1 이상 5 이하의 정수여야 합니다. rating=" + rating);
        }

        BbsPostReview review = BbsPostReview.builder()
                .postId(postId)
                .authorId(authorId)
                .rating(rating)
                .content(request.content())
                .ipAddress(ipAddress)
                .status("VISIBLE")
                .build();
        reviewMapper.insert(review);

        // REQ-REV-009: 신규 VISIBLE 리뷰 반영 — 동일 트랜잭션에서 집계 재계산.
        recalculateAggregate(postId);

        BbsPostReview saved = reviewMapper.findById(review.getId()).orElse(review);
        return ReviewResponse.from(saved);
    }

    @Override
    public List<ReviewResponse> listByPost(Long postId) {
        return reviewMapper.selectByPostId(postId).stream()
                .map(ReviewResponse::from)
                .toList();
    }

    @Override
    @Transactional
    public void recalculateAggregate(Long postId) {
        ReviewAggregate agg = reviewMapper.aggregateVisible(postId);
        int count = agg == null ? 0 : agg.count();
        BigDecimal avg = (agg == null || agg.average() == null) ? ZERO_RATING : agg.average();
        postMapper.updateReviewAggregate(postId, count, avg);
    }
}
