package kr.co.ircp.cms.domain.content.page.controller;

import jakarta.validation.Valid;
import kr.co.ircp.cms.domain.content.page.dto.BlockOrderRequest;
import kr.co.ircp.cms.domain.content.page.dto.ContentBlockRequest;
import kr.co.ircp.cms.domain.content.page.dto.ContentBlockResponse;
import kr.co.ircp.cms.domain.content.page.service.ContentBlockService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 콘텐츠 블록 REST 컨트롤러.
 * REQ-CONTENT-006-D: 블록 CRUD + 순서 변경 API
 */
@RestController
@RequestMapping("/api/v1/content/pages/{pageId}/blocks")
@RequiredArgsConstructor
public class ContentBlockController {

    private final ContentBlockService contentBlockService;

    /** GET /api/v1/content/pages/{pageId}/blocks — 블록 목록 조회 */
    @GetMapping
    @PreAuthorize("hasAuthority('PAGE:READ')")
    public ResponseEntity<List<ContentBlockResponse>> listBlocks(@PathVariable Long pageId) {
        return ResponseEntity.ok(contentBlockService.listBlocks(pageId));
    }

    /** POST /api/v1/content/pages/{pageId}/blocks — 블록 생성 */
    @PostMapping
    @PreAuthorize("hasAuthority('BLOCK:WRITE')")
    public ResponseEntity<ContentBlockResponse> createBlock(
            @PathVariable Long pageId,
            @Valid @RequestBody ContentBlockRequest request
    ) {
        Set<String> authorities = resolveAuthorities();
        ContentBlockResponse created = contentBlockService.createBlock(pageId, request, authorities);
        return ResponseEntity.created(
                URI.create("/api/v1/content/pages/" + pageId + "/blocks/" + created.id())
        ).body(created);
    }

    /** PUT /api/v1/content/pages/{pageId}/blocks/{blockId} — 블록 수정 */
    @PutMapping("/{blockId}")
    @PreAuthorize("hasAuthority('BLOCK:WRITE')")
    public ResponseEntity<ContentBlockResponse> updateBlock(
            @PathVariable Long pageId,
            @PathVariable Long blockId,
            @Valid @RequestBody ContentBlockRequest request
    ) {
        Set<String> authorities = resolveAuthorities();
        return ResponseEntity.ok(contentBlockService.updateBlock(pageId, blockId, request, authorities));
    }

    /** DELETE /api/v1/content/pages/{pageId}/blocks/{blockId} — 블록 삭제 */
    @DeleteMapping("/{blockId}")
    @PreAuthorize("hasAuthority('BLOCK:WRITE')")
    public ResponseEntity<Void> deleteBlock(
            @PathVariable Long pageId,
            @PathVariable Long blockId
    ) {
        contentBlockService.deleteBlock(pageId, blockId);
        return ResponseEntity.noContent().build();
    }

    /** PATCH /api/v1/content/pages/{pageId}/blocks/order — 블록 순서 일괄 갱신 */
    @PatchMapping("/order")
    @PreAuthorize("hasAuthority('BLOCK:WRITE')")
    public ResponseEntity<Void> reorderBlocks(
            @PathVariable Long pageId,
            @Valid @RequestBody BlockOrderRequest request
    ) {
        contentBlockService.reorderBlocks(pageId, request);
        return ResponseEntity.noContent().build();
    }

    /** SecurityContext에서 권한 코드 Set 추출 */
    private Set<String> resolveAuthorities() {
        return SecurityContextHolder.getContext().getAuthentication()
                .getAuthorities().stream()
                .map(a -> a.getAuthority())
                .collect(Collectors.toSet());
    }
}
