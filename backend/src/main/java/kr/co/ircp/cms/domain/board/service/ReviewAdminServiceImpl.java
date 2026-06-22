package kr.co.ircp.cms.domain.board.service;

import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.board.dto.AdminReviewResponse;
import kr.co.ircp.cms.domain.board.entity.BbsPostReview;
import kr.co.ircp.cms.domain.board.exception.ReviewNotFoundException;
import kr.co.ircp.cms.domain.board.repository.BbsPostReviewMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * 관리자 리뷰 모더레이션 서비스 구현체.
 * SPEC-CMS-REVIEW-001 REQ-REV-004/006/010/011
 *
 * <p>집계 재계산은 {@link ReviewService#recalculateAggregate(Long)} 단일 계약을 재사용한다
 * (full-recompute — HIDDEN/DELETED 리뷰는 VISIBLE 모수에서 자연 제외).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewAdminServiceImpl implements ReviewAdminService {

    private static final int DEFAULT_SIZE = 20;

    private final BbsPostReviewMapper reviewMapper;
    private final ReviewService reviewService;

    @Override
    public PageResponse<AdminReviewResponse> listAll(Long postId, String status, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? DEFAULT_SIZE : size;
        String filter = (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status))
                ? null
                : status.toUpperCase();
        int offset = safePage * safeSize;

        List<AdminReviewResponse> content = reviewMapper.selectAdminPage(postId, filter, offset, safeSize);
        long total = reviewMapper.countAdminPage(postId, filter);
        return PageResponse.of(content, safePage, safeSize, total);
    }

    @Override
    @Transactional
    public void hide(Long reviewId) {
        BbsPostReview review = reviewMapper.findById(reviewId)
                .orElseThrow(() -> new ReviewNotFoundException(reviewId));

        // REQ-REV-006: DELETED 는 비가역 — 숨김으로 되돌릴 수 없다.
        if ("DELETED".equals(review.getStatus())) {
            throw new IllegalStateException("삭제된 리뷰는 숨김 처리할 수 없습니다. id=" + reviewId);
        }
        // 이미 HIDDEN 이면 멱등 — 상태 전이 없이 종료.
        if ("HIDDEN".equals(review.getStatus())) {
            return;
        }

        reviewMapper.updateStatus(reviewId, "HIDDEN", null);
        // REQ-REV-010: HIDDEN 은 VISIBLE 집계에서 제외 → 재계산.
        reviewService.recalculateAggregate(review.getPostId());
    }

    @Override
    @Transactional
    public void delete(Long reviewId) {
        BbsPostReview review = reviewMapper.findById(reviewId)
                .orElseThrow(() -> new ReviewNotFoundException(reviewId));

        // REQ-REV-006: 이미 DELETED 면 멱등 — no-op 으로 204 응답.
        if ("DELETED".equals(review.getStatus())) {
            return;
        }

        reviewMapper.updateStatus(reviewId, "DELETED", Instant.now());
        // REQ-REV-010: DELETED 는 VISIBLE 집계에서 제외 → 재계산.
        reviewService.recalculateAggregate(review.getPostId());
    }
}
