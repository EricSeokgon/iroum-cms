package kr.co.ircp.cms.domain.board.controller;

import kr.co.ircp.cms.domain.auth.security.JwtPrincipal;
import kr.co.ircp.cms.domain.board.dto.CommentSummary;
import kr.co.ircp.cms.domain.board.service.CommentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 댓글 리소스 컨트롤러 — 개별 댓글 수정/삭제.
 * REQ-BOARD-003: 댓글 CRUD API
 *
 * // @MX:NOTE: [AUTO] 프론트엔드가 PUT/DELETE /api/v1/board/comments/{id} 경로로 호출하므로
 * //           CommentController(게시물 기준)와 분리하여 별도 컨트롤러로 제공.
 */
@RestController
@RequestMapping("/api/v1/board/comments")
@RequiredArgsConstructor
public class CommentResourceController {

    private final CommentService commentService;

    /** PUT /{id} — 댓글 수정. 요청 body: { "content": "..." } */
    @PutMapping("/{id}")
    public ResponseEntity<CommentSummary> updateComment(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal JwtPrincipal principal
    ) {
        Long requesterId = principal != null ? principal.userId() : null;
        return ResponseEntity.ok(commentService.updateComment(id, body.get("content"), requesterId));
    }

    /** DELETE /{id} — 댓글 삭제 */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteComment(
            @PathVariable Long id,
            @AuthenticationPrincipal JwtPrincipal principal
    ) {
        Long requesterId = principal != null ? principal.userId() : null;
        commentService.deleteComment(id, requesterId);
        return ResponseEntity.noContent().build();
    }
}
