package kr.co.ircp.cms.domain.board.controller;

import jakarta.validation.Valid;
import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.board.dto.PostAdminStatusRequest;
import kr.co.ircp.cms.domain.board.dto.PostAdminSummary;
import kr.co.ircp.cms.domain.board.service.PostAdminService;
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
 * 게시글 관리자 모더레이션 REST 컨트롤러.
 * SPEC-CMS-POST-MODERATE-001 REQ-PA-001~004
 */
@RestController
@RequestMapping("/api/v1/admin/posts")
@PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
@RequiredArgsConstructor
public class PostAdminController {

    private final PostAdminService service;

    /** REQ-PA-001: 전체 게시글 목록 (HIDDEN 포함, 교차 게시판) */
    @GetMapping
    public ResponseEntity<PageResponse<PostAdminSummary>> list(
            @RequestParam(required = false) Long bbsId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        return ResponseEntity.ok(service.listAll(bbsId, page, size, status, keyword));
    }

    /** REQ-PA-002: 게시글 상태 변경 */
    @PatchMapping("/{id}/status")
    public ResponseEntity<PostAdminSummary> changeStatus(
            @PathVariable Long id,
            @Valid @RequestBody PostAdminStatusRequest request) {
        return ResponseEntity.ok(service.changeStatus(id, request.status()));
    }

    /** REQ-PA-003: 게시글 강제 삭제 */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
