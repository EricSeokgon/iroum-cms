package kr.co.ircp.cms.domain.board.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import kr.co.ircp.cms.domain.auth.security.JwtPrincipal;
import kr.co.ircp.cms.domain.board.dto.ReviewCreateRequest;
import kr.co.ircp.cms.domain.board.dto.ReviewResponse;
import kr.co.ircp.cms.domain.board.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.net.URI;
import java.util.List;

/**
 * 게시물 별점 리뷰 공개 REST 컨트롤러.
 * SPEC-CMS-REVIEW-001 REQ-REV-001/005/007
 *
 * <p>GET 은 공개(비인증 허용), POST 는 인증 필수(SecurityConfig 에서 401 강제).
 */
@RestController
@RequestMapping("/api/v1/posts/{postId}/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    /** GET — 게시물 VISIBLE 리뷰 목록 (공개). REQ-REV-005 */
    @GetMapping
    public ResponseEntity<List<ReviewResponse>> listReviews(@PathVariable Long postId) {
        return ResponseEntity.ok(reviewService.listByPost(postId));
    }

    /** POST — 리뷰 작성 (인증 필수, 201 Created). REQ-REV-001/007 */
    @PostMapping
    public ResponseEntity<ReviewResponse> createReview(
            @PathVariable Long postId,
            @Valid @RequestBody ReviewCreateRequest request,
            @AuthenticationPrincipal JwtPrincipal principal,
            HttpServletRequest httpRequest
    ) {
        // REQ-REV-007: 비인증 요청 차단 (SecurityConfig 와 이중 방어).
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "리뷰 작성은 로그인이 필요합니다.");
        }
        String ipAddress = httpRequest.getRemoteAddr();
        ReviewResponse created =
                reviewService.createReview(postId, request, principal.userId(), ipAddress);
        return ResponseEntity
                .created(URI.create("/api/v1/posts/" + postId + "/reviews/" + created.id()))
                .body(created);
    }
}
