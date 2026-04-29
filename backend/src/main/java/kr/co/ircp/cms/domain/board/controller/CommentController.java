package kr.co.ircp.cms.domain.board.controller;

import jakarta.validation.Valid;
import kr.co.ircp.cms.domain.board.dto.CommentCreateRequest;
import kr.co.ircp.cms.domain.board.dto.CommentSummary;
import kr.co.ircp.cms.domain.board.service.CommentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/**
 * 댓글 REST 컨트롤러.
 * REQ-BOARD-003: 댓글 CRUD API
 */
@RestController
@RequestMapping("/api/v1/boards/{bbsMasterId}/posts/{postId}/comments")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    /** GET — 댓글 목록 조회 */
    @GetMapping
    public ResponseEntity<List<CommentSummary>> listComments(
            @PathVariable Long bbsMasterId,
            @PathVariable Long postId
    ) {
        return ResponseEntity.ok(commentService.listComments(postId));
    }

    /** POST — 댓글 작성 */
    @PostMapping
    public ResponseEntity<CommentSummary> createComment(
            @PathVariable Long bbsMasterId,
            @PathVariable Long postId,
            @Valid @RequestBody CommentCreateRequest request,
            @AuthenticationPrincipal Long authorId
    ) {
        CommentSummary created = commentService.createComment(postId, request, authorId);
        return ResponseEntity.created(
                URI.create("/api/v1/boards/" + bbsMasterId + "/posts/" + postId + "/comments/" + created.id())
        ).body(created);
    }

    /** PUT /{commentId} — 댓글 수정 */
    @PutMapping("/{commentId}")
    public ResponseEntity<CommentSummary> updateComment(
            @PathVariable Long bbsMasterId,
            @PathVariable Long postId,
            @PathVariable Long commentId,
            @RequestParam String content,
            @AuthenticationPrincipal Long requesterId
    ) {
        return ResponseEntity.ok(commentService.updateComment(commentId, content, requesterId));
    }

    /** DELETE /{commentId} — 댓글 삭제 */
    @DeleteMapping("/{commentId}")
    public ResponseEntity<Void> deleteComment(
            @PathVariable Long bbsMasterId,
            @PathVariable Long postId,
            @PathVariable Long commentId,
            @AuthenticationPrincipal Long requesterId
    ) {
        commentService.deleteComment(commentId, requesterId);
        return ResponseEntity.noContent().build();
    }
}
