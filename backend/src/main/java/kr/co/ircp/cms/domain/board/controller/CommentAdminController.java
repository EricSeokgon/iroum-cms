package kr.co.ircp.cms.domain.board.controller;

import jakarta.validation.constraints.NotBlank;
import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.board.dto.CommentAdminListRequest;
import kr.co.ircp.cms.domain.board.dto.CommentAdminSummary;
import kr.co.ircp.cms.domain.board.service.CommentAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 댓글 모더레이션 REST 컨트롤러.
 *
 * <p>SPEC-CMS-COMMENT-MODERATE-001 REQ-CMTM-001~005 — 전체 게시판 댓글 조회·상태 변경·강제 삭제.
 * 클래스 레벨 @PreAuthorize("hasAnyRole('ADMIN','MANAGER')") 로 모든 endpoint 를 보호한다
 * (REQ-CMTM-005 — 비ADMIN/MANAGER 는 403). 비인증 접근은 SecurityConfig HTTP 레이어에서 401.
 *
 * // @MX:ANCHOR: [AUTO] CommentAdminController — ADMIN/MANAGER 전용 댓글 모더레이션 보안 경계
 * // @MX:REASON: REQ-CMTM-005 권한 invariant. 클래스 레벨 @PreAuthorize 계약 (RagAdminController 패턴)
 * // @MX:SPEC: SPEC-CMS-COMMENT-MODERATE-001
 */
@RestController
@RequestMapping("/api/v1/admin/comments")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
public class CommentAdminController {

    private final CommentAdminService commentAdminService;

    /** GET — 전체 댓글 목록 (게시판/상태/키워드 필터 + 페이징). REQ-CMTM-001/002 */
    @GetMapping
    public ResponseEntity<PageResponse<CommentAdminSummary>> listComments(
            @RequestParam(required = false) Long boardId,
            @RequestParam(defaultValue = "ALL") String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        CommentAdminListRequest request =
                new CommentAdminListRequest(boardId, status, keyword, page, size);
        return ResponseEntity.ok(commentAdminService.listComments(request));
    }

    /** PATCH — 댓글 상태 변경 (VISIBLE/HIDDEN). REQ-CMTM-003 */
    @PatchMapping("/{id}/status")
    public ResponseEntity<CommentAdminSummary> changeStatus(
            @PathVariable Long id,
            @RequestBody StatusChangeRequest request
    ) {
        return ResponseEntity.ok(commentAdminService.changeStatus(id, request.status()));
    }

    /** DELETE — 댓글 강제 삭제 (소프트 삭제). REQ-CMTM-004 (idempotent → 204) */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteComment(@PathVariable Long id) {
        commentAdminService.deleteComment(id);
        return ResponseEntity.noContent().build();
    }

    /** 상태 변경 요청 body. */
    public record StatusChangeRequest(@NotBlank String status) {
    }
}
