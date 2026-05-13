package kr.co.ircp.cms.domain.board.controller;

import jakarta.validation.Valid;
import kr.co.ircp.cms.domain.auth.dto.PageResponse;
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
 */
@RestController
@RequestMapping("/api/v1/boards/{bbsMasterId}/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    /** GET /api/v1/boards/{bbsMasterId}/posts — 게시글 목록 페이징 조회 */
    @GetMapping
    public ResponseEntity<PageResponse<PostSummary>> listPosts(
            @PathVariable Long bbsMasterId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(postService.listPosts(bbsMasterId, page, size));
    }

    /** GET /api/v1/boards/{bbsMasterId}/posts/search — 게시글 전문검색 */
    @GetMapping("/search")
    public ResponseEntity<PageResponse<PostSummary>> searchPosts(
            @PathVariable Long bbsMasterId,
            @RequestParam String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(postService.searchPosts(bbsMasterId, keyword, page, size));
    }

    /** GET /api/v1/boards/{bbsMasterId}/posts/{postId} — 게시글 단건 상세 조회 */
    @GetMapping("/{postId}")
    public ResponseEntity<PostDetail> getPost(
            @PathVariable Long bbsMasterId,
            @PathVariable Long postId,
            @AuthenticationPrincipal(expression = "userId") Long userId,
            @RequestParam(required = false) String ipHash
    ) {
        return ResponseEntity.ok(postService.getPost(postId, userId, ipHash));
    }

    /** POST /api/v1/boards/{bbsMasterId}/posts — 게시글 작성 */
    @PostMapping
    public ResponseEntity<PostDetail> createPost(
            @PathVariable Long bbsMasterId,
            @Valid @RequestBody PostCreateRequest request,
            @AuthenticationPrincipal Long authorId
    ) {
        PostDetail created = postService.createPost(request, authorId);
        return ResponseEntity.created(
                URI.create("/api/v1/boards/" + bbsMasterId + "/posts/" + created.id())
        ).body(created);
    }

    /** PUT /api/v1/boards/{bbsMasterId}/posts/{postId} — 게시글 수정 */
    @PutMapping("/{postId}")
    public ResponseEntity<PostDetail> updatePost(
            @PathVariable Long bbsMasterId,
            @PathVariable Long postId,
            @Valid @RequestBody PostUpdateRequest request,
            @AuthenticationPrincipal Long editorId
    ) {
        return ResponseEntity.ok(postService.updatePost(postId, request, editorId));
    }

    /** DELETE /api/v1/boards/{bbsMasterId}/posts/{postId} — 게시글 삭제 */
    @DeleteMapping("/{postId}")
    public ResponseEntity<Void> deletePost(
            @PathVariable Long bbsMasterId,
            @PathVariable Long postId,
            @AuthenticationPrincipal Long requesterId
    ) {
        postService.deletePost(postId, requesterId);
        return ResponseEntity.noContent().build();
    }
}
