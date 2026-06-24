package kr.co.ircp.cms.domain.point.controller;

import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.point.dto.LikeToggleResponse;
import kr.co.ircp.cms.domain.point.dto.PointLedgerResponse;
import kr.co.ircp.cms.domain.point.dto.PointLedgerSearchRequest;
import kr.co.ircp.cms.domain.point.service.BbsPostLikeService;
import kr.co.ircp.cms.domain.point.service.UserPointService;
import kr.co.ircp.cms.domain.auth.security.JwtPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 포인트 이력 조회 및 좋아요 REST 컨트롤러.
 * SPEC-CMS-POINTS-001 REQ-PNT-004~005, REQ-PNT-007
 */
@RestController
@RequiredArgsConstructor
public class PointLedgerController {

    private final UserPointService pointService;
    private final BbsPostLikeService likeService;

    /** GET /api/v1/admin/points/ledger — 포인트 이력 목록 (관리자). REQ-PNT-007 */
    @GetMapping("/api/v1/admin/points/ledger")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<PageResponse<PointLedgerResponse>> listLedger(
            @RequestParam(required = false) Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var req = new PointLedgerSearchRequest(userId, page, size);
        List<PointLedgerResponse> content = pointService.listLedger(req);
        int total = pointService.countLedger(req);
        return ResponseEntity.ok(PageResponse.of(content, page, size, total));
    }

    /** POST /api/v1/posts/{postId}/like — 좋아요 toggle. REQ-PNT-004~005 */
    @PostMapping("/api/v1/posts/{postId}/like")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<LikeToggleResponse> toggleLike(
            @PathVariable Long postId,
            @AuthenticationPrincipal JwtPrincipal principal) {
        return ResponseEntity.ok(likeService.toggle(postId, principal.userId()));
    }
}
