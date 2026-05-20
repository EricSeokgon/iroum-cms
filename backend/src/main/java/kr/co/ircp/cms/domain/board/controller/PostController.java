package kr.co.ircp.cms.domain.board.controller;

import jakarta.validation.Valid;
import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.auth.security.JwtPrincipal;
import kr.co.ircp.cms.domain.board.dto.PostCreateRequest;
import kr.co.ircp.cms.domain.board.dto.PostDetail;
import kr.co.ircp.cms.domain.board.dto.PostSummary;
import kr.co.ircp.cms.domain.board.dto.PostUpdateRequest;
import kr.co.ircp.cms.domain.board.service.PostService;
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

/**
 * 게시글 REST 컨트롤러.
 * REQ-BOARD-002: 게시글 CRUD + 페이징·검색 API
 * 경로: /api/v1/board/posts (프론트엔드 board.ts 스펙 — flat 구조, bbsId는 쿼리파라미터)
 */
@RestController
@RequestMapping("/api/v1/board/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    /** GET /api/v1/board/posts?bbsId=X&page=0&size=20 — 게시글 목록 페이징 조회 */
    @GetMapping
    public ResponseEntity<PageResponse<PostSummary>> listPosts(
            @RequestParam Long bbsId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String sort
    ) {
        return ResponseEntity.ok(postService.listPosts(bbsId, page, size));
    }

    /** GET /api/v1/board/posts/search?bbsId=X&keyword=Y — 게시글 전문검색 */
    @GetMapping("/search")
    public ResponseEntity<PageResponse<PostSummary>> searchPosts(
            @RequestParam Long bbsId,
            @RequestParam String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(postService.searchPosts(bbsId, keyword, page, size));
    }

    /** GET /api/v1/board/posts/{postId} — 게시글 단건 상세 조회 */
    @GetMapping("/{postId}")
    public ResponseEntity<PostDetail> getPost(
            @PathVariable Long postId,
            @AuthenticationPrincipal JwtPrincipal principal,
            @RequestParam(required = false) String ipHash
    ) {
        Long userId = principal != null ? principal.userId() : null;
        return ResponseEntity.ok(postService.getPost(postId, userId, ipHash));
    }

    /** POST /api/v1/board/posts — 게시글 작성 (bbsMasterId는 body의 bbsId 필드로 전달) */
    @PostMapping
    public ResponseEntity<PostDetail> createPost(
            @Valid @RequestBody PostCreateRequest request,
            @AuthenticationPrincipal JwtPrincipal principal
    ) {
        Long authorId = principal != null ? principal.userId() : null;
        PostDetail created = postService.createPost(request, authorId);
        return ResponseEntity.created(
                URI.create("/api/v1/board/posts/" + created.id())
        ).body(created);
    }

    /** PUT /api/v1/board/posts/{postId} — 게시글 수정 */
    @PutMapping("/{postId}")
    public ResponseEntity<PostDetail> updatePost(
            @PathVariable Long postId,
            @Valid @RequestBody PostUpdateRequest request,
            @AuthenticationPrincipal JwtPrincipal principal
    ) {
        Long editorId = principal != null ? principal.userId() : null;
        return ResponseEntity.ok(postService.updatePost(postId, request, editorId));
    }

    /** DELETE /api/v1/board/posts/{postId} — 게시글 삭제 */
    @DeleteMapping("/{postId}")
    public ResponseEntity<Void> deletePost(
            @PathVariable Long postId,
            @AuthenticationPrincipal JwtPrincipal principal
    ) {
        Long requesterId = principal != null ? principal.userId() : null;
        postService.deletePost(postId, requesterId);
        return ResponseEntity.noContent().build();
    }
}
