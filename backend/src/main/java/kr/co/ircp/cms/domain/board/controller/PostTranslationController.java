package kr.co.ircp.cms.domain.board.controller;

import jakarta.validation.Valid;
import kr.co.ircp.cms.domain.board.dto.PostTranslationRequest;
import kr.co.ircp.cms.domain.board.dto.PostTranslationResponse;
import kr.co.ircp.cms.domain.board.service.PostService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 게시글 다국어 번역 REST 컨트롤러.
 * SPEC-CMS-NOTICE-I18N-001: 공지사항 게시글 번역 CRUD API.
 * 경로: /api/v1/board/posts/{postId}/translations
 */
@RestController
@RequestMapping("/api/v1/board/posts/{postId}/translations")
@RequiredArgsConstructor
public class PostTranslationController {

    private final PostService postService;

    /** GET — 게시글 전체 번역 목록 조회 (관리자) */
    @GetMapping
    @PreAuthorize("hasAnyRole('CONTENT_ADMIN','DEPT_ADMIN','ADMIN','SUPER_ADMIN')")
    public ResponseEntity<List<PostTranslationResponse>> listTranslations(
            @PathVariable Long postId
    ) {
        return ResponseEntity.ok(postService.listTranslations(postId));
    }

    /** PUT — 번역 등록/수정 (upsert, 관리자) */
    @PutMapping
    @PreAuthorize("hasAnyRole('CONTENT_ADMIN','DEPT_ADMIN','ADMIN','SUPER_ADMIN')")
    public ResponseEntity<PostTranslationResponse> upsertTranslation(
            @PathVariable Long postId,
            @Valid @RequestBody PostTranslationRequest request
    ) {
        return ResponseEntity.ok(postService.upsertTranslation(postId, request));
    }

    /** GET /{language} — 단건 번역 조회 */
    @GetMapping("/{language}")
    @PreAuthorize("hasAnyRole('CONTENT_ADMIN','DEPT_ADMIN','ADMIN','SUPER_ADMIN')")
    public ResponseEntity<PostTranslationResponse> getTranslation(
            @PathVariable Long postId,
            @PathVariable String language
    ) {
        return postService.getTranslation(postId, language)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** DELETE /{language} — 번역 삭제 (SUPER_ADMIN 전용) */
    @DeleteMapping("/{language}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Void> deleteTranslation(
            @PathVariable Long postId,
            @PathVariable String language
    ) {
        postService.deleteTranslation(postId, language);
        return ResponseEntity.noContent().build();
    }
}
