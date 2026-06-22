package kr.co.ircp.cms.domain.board.controller;

import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.board.dto.AdminReviewResponse;
import kr.co.ircp.cms.domain.board.service.ReviewAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 리뷰 모더레이션 REST 컨트롤러.
 * SPEC-CMS-REVIEW-001 REQ-REV-004/006/010/012
 *
 * // @MX:NOTE: [AUTO] 권한 가드 — 목록 조회는 REVIEW:READ, 숨김/삭제는 REVIEW:DELETE.
 * //           DELETE 는 idempotent: 이미 DELETED 인 리뷰도 204 로 응답한다 (REQ-REV-006).
 * //           SPEC-CMS-COMMENT-MODERATE-001 의 관리자 모더레이션 RBAC 패턴을 따른다.
 * // @MX:SPEC: SPEC-CMS-REVIEW-001
 */
@RestController
@RequestMapping("/api/v1/admin/reviews")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('REVIEW:READ')")
public class ReviewAdminController {

    private final ReviewAdminService reviewAdminService;

    /** GET — 전체 리뷰 목록 (게시물/상태 필터 + 페이징). REQ-REV-004/011 */
    @GetMapping
    public ResponseEntity<PageResponse<AdminReviewResponse>> listReviews(
            @RequestParam(required = false) Long postId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(reviewAdminService.listAll(postId, status, page, size));
    }

    /** PATCH — 리뷰 숨김(HIDDEN). REQ-REV-010/011 */
    @PatchMapping("/{id}/hide")
    @PreAuthorize("hasAuthority('REVIEW:DELETE')")
    public ResponseEntity<Void> hideReview(@PathVariable Long id) {
        reviewAdminService.hide(id);
        return ResponseEntity.noContent().build();
    }

    /** DELETE — 리뷰 삭제(DELETED, idempotent → 204). REQ-REV-006/010 */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('REVIEW:DELETE')")
    public ResponseEntity<Void> deleteReview(@PathVariable Long id) {
        reviewAdminService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
