package kr.co.ircp.cms.domain.board.controller;

import jakarta.validation.Valid;
import kr.co.ircp.cms.domain.auth.security.JwtPrincipal;
import kr.co.ircp.cms.domain.board.dto.CommentCreateRequest;
import kr.co.ircp.cms.domain.board.dto.CommentSummary;
import kr.co.ircp.cms.domain.board.service.CommentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/**
 * 댓글 REST 컨트롤러 — 게시물 기준 목록/작성.
 * REQ-BOARD-003: 댓글 CRUD API
 *
 * // @MX:NOTE: [AUTO] 프론트엔드 호출 경로(/api/v1/board/posts/{postId}/comments)에 맞춰
 * //           bbsMasterId 경로 변수를 제거함. 서비스 레이어는 postId로 충분히 처리.
 */
@RestController
@RequestMapping("/api/v1/board/posts/{postId}/comments")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    /** GET — 댓글 목록 조회 */
    @GetMapping
    public ResponseEntity<List<CommentSummary>> listComments(
            @PathVariable Long postId
    ) {
        return ResponseEntity.ok(commentService.listComments(postId));
    }

    /** POST — 댓글 작성 */
    @PostMapping
    public ResponseEntity<CommentSummary> createComment(
            @PathVariable Long postId,
            @Valid @RequestBody CommentCreateRequest request,
            @AuthenticationPrincipal JwtPrincipal principal
    ) {
        Long authorId = principal != null ? principal.userId() : null;
        CommentSummary created = commentService.createComment(postId, request, authorId);
        return ResponseEntity.created(
                URI.create("/api/v1/board/comments/" + created.id())
        ).body(created);
    }
}
