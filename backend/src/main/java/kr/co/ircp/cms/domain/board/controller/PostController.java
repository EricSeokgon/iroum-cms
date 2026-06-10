package kr.co.ircp.cms.domain.board.controller;

import jakarta.validation.Valid;
import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.auth.security.JwtPrincipal;
import kr.co.ircp.cms.domain.board.dto.PostCreateRequest;
import kr.co.ircp.cms.domain.board.dto.PostDetail;
import kr.co.ircp.cms.domain.board.dto.PostHistoryDetail;
import kr.co.ircp.cms.domain.board.dto.PostHistoryItem;
import kr.co.ircp.cms.domain.board.dto.PostScheduleRequest;
import kr.co.ircp.cms.domain.board.dto.PostSummary;
import kr.co.ircp.cms.domain.board.dto.PostUpdateRequest;
import kr.co.ircp.cms.domain.board.service.PostHistoryService;
import kr.co.ircp.cms.domain.board.service.PostService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
    private final PostHistoryService postHistoryService;

    /** GET /api/v1/board/posts?bbsId=X&page=0&size=20&lang=ko — 게시글 목록 페이징 조회 */
    @GetMapping
    public ResponseEntity<PageResponse<PostSummary>> listPosts(
            @RequestParam Long bbsId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String sort,
            @RequestParam(value = "lang", defaultValue = "ko") String lang
    ) {
        // SPEC-CMS-NOTICE-I18N-002: lang 파라미터를 서비스에 전달하여 번역 목록 오버레이 활성화.
        return ResponseEntity.ok(postService.listPosts(bbsId, page, size, lang));
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

    /** GET /api/v1/board/posts/{postId}?lang=ko — 게시글 단건 상세 조회 */
    @GetMapping("/{postId}")
    public ResponseEntity<PostDetail> getPost(
            @PathVariable Long postId,
            @AuthenticationPrincipal JwtPrincipal principal,
            @RequestParam(required = false) String ipHash,
            @RequestParam(value = "lang", defaultValue = "ko") String lang
    ) {
        Long userId = principal != null ? principal.userId() : null;
        PostDetail detail = postService.getPost(postId, userId, ipHash);

        // SPEC-CMS-NOTICE-I18N-001: lang=en이고 번역이 존재하면 en 버전 오버레이.
        // ko(원본) 또는 번역 부재 시 원본 반환 + Content-Language: ko.
        if (!"ko".equals(lang)) {
            var translation = postService.getTranslation(postId, lang);
            if (translation.isPresent()) {
                var t = translation.get();
                PostDetail localized = new PostDetail(
                        detail.id(), detail.bbsMasterId(), detail.bbsMasterCode(),
                        detail.useComment(), t.title(), t.contentHtml(),
                        detail.authorId(), detail.authorName(),
                        detail.isNotice(), detail.noticeFrom(), detail.noticeUntil(),
                        detail.isSecret(), detail.viewCount(), detail.commentCount(),
                        detail.status(), detail.metadata(), detail.attachments(),
                        detail.createdAt(), detail.updatedAt()
                );
                return ResponseEntity.ok()
                        .header("Content-Language", lang)
                        .body(localized);
            }
        }
        return ResponseEntity.ok()
                .header("Content-Language", "ko")
                .body(detail);
    }

    /** POST /api/v1/board/posts — 게시글 작성 (bbsMasterId는 body의 bbsId 필드로 전달) */
    @PostMapping
    @PreAuthorize("isAuthenticated()")
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
    @PreAuthorize("isAuthenticated()")
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
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> deletePost(
            @PathVariable Long postId,
            @AuthenticationPrincipal JwtPrincipal principal
    ) {
        Long requesterId = principal != null ? principal.userId() : null;
        postService.deletePost(postId, requesterId);
        return ResponseEntity.noContent().build();
    }

    // ─── SPEC-CMS-POST-SCHEDULE-001: 예약 발행 ──────────────────────────────────

    /** POST /api/v1/board/posts/{postId}/schedule — 게시글 예약 발행 */
    @PostMapping("/{postId}/schedule")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PostDetail> schedulePost(
            @PathVariable Long postId,
            @Valid @RequestBody PostScheduleRequest request
    ) {
        return ResponseEntity.ok(postService.schedulePost(postId, request));
    }

    /** DELETE /api/v1/board/posts/{postId}/schedule — 예약 취소(→DRAFT) */
    @DeleteMapping("/{postId}/schedule")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PostDetail> cancelSchedule(@PathVariable Long postId) {
        return ResponseEntity.ok(postService.cancelSchedule(postId));
    }

    // ─── SPEC-CMS-POST-HISTORY-001: 버전 히스토리 (read-only) ────────────────────
    //
    // 본 GET 엔드포인트는 listPosts/getPost와 동일하게 메소드 레벨 @PreAuthorize 없이
    // SecurityConfig HTTP 레벨 인증 정책(.anyRequest().authenticated())으로 보호된다
    // (REQ-PH-006 — 비인증 접근은 HTTP 레이어에서 401/403). 인가 매트릭스 회귀는
    // AUTHZ-MATRIX IT 레이어에서 검증한다.

    /** GET /api/v1/board/posts/{postId}/history?page=0&size=20 — 버전 히스토리 페이징 목록 */
    @GetMapping("/{postId}/history")
    public ResponseEntity<PageResponse<PostHistoryItem>> getPostHistory(
            @PathVariable Long postId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(postHistoryService.getHistory(postId, page, size));
    }

    /** GET /api/v1/board/posts/{postId}/history/{version} — 특정 버전 단건 본문 */
    @GetMapping("/{postId}/history/{version}")
    public ResponseEntity<PostHistoryDetail> getPostVersion(
            @PathVariable Long postId,
            @PathVariable int version
    ) {
        return ResponseEntity.ok(postHistoryService.getVersion(postId, version));
    }
}
