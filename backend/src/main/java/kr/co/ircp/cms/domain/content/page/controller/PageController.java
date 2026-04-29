package kr.co.ircp.cms.domain.content.page.controller;

import jakarta.validation.Valid;
import kr.co.ircp.cms.domain.content.page.dto.PageCreateRequest;
import kr.co.ircp.cms.domain.content.page.dto.PageHistoryResponse;
import kr.co.ircp.cms.domain.content.page.dto.PagePublishRequest;
import kr.co.ircp.cms.domain.content.page.dto.PageResponse;
import kr.co.ircp.cms.domain.content.page.dto.PageScheduleRequest;
import kr.co.ircp.cms.domain.content.page.dto.PageUpdateRequest;
import kr.co.ircp.cms.domain.content.page.service.PageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
 * 페이지 REST 컨트롤러.
 * REQ-CONTENT-005-D: 페이지 CRUD + 발행/예약/철회 + 이력 API
 */
@RestController
@RequestMapping("/api/v1/content/pages")
@RequiredArgsConstructor
public class PageController {

    private final PageService pageService;

    /** POST /api/v1/content/pages — 페이지 생성 */
    @PostMapping
    @PreAuthorize("hasAuthority('PAGE:WRITE')")
    public ResponseEntity<PageResponse> createPage(
            @Valid @RequestBody PageCreateRequest request,
            @AuthenticationPrincipal Long userId
    ) {
        PageResponse created = pageService.createPage(request, userId);
        return ResponseEntity.created(URI.create("/api/v1/content/pages/" + created.id())).body(created);
    }

    /** PUT /api/v1/content/pages/{id} — 페이지 수정 */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PAGE:WRITE')")
    public ResponseEntity<PageResponse> updatePage(
            @PathVariable Long id,
            @Valid @RequestBody PageUpdateRequest request,
            @AuthenticationPrincipal Long userId
    ) {
        return ResponseEntity.ok(pageService.updatePage(id, request, userId));
    }

    /** POST /api/v1/content/pages/{id}/publish — 즉시 발행 */
    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAuthority('PAGE:PUBLISH')")
    public ResponseEntity<PageResponse> publishPage(
            @PathVariable Long id,
            @RequestBody(required = false) PagePublishRequest request,
            @AuthenticationPrincipal Long userId
    ) {
        return ResponseEntity.ok(pageService.publishPage(id, request, userId));
    }

    /** POST /api/v1/content/pages/{id}/schedule — 예약 발행 */
    @PostMapping("/{id}/schedule")
    @PreAuthorize("hasAuthority('PAGE:PUBLISH')")
    public ResponseEntity<PageResponse> schedulePage(
            @PathVariable Long id,
            @Valid @RequestBody PageScheduleRequest request,
            @AuthenticationPrincipal Long userId
    ) {
        return ResponseEntity.ok(pageService.schedulePage(id, request, userId));
    }

    /** POST /api/v1/content/pages/{id}/retract — 철회 */
    @PostMapping("/{id}/retract")
    @PreAuthorize("hasAuthority('PAGE:PUBLISH')")
    public ResponseEntity<PageResponse> retractPage(
            @PathVariable Long id,
            @AuthenticationPrincipal Long userId
    ) {
        return ResponseEntity.ok(pageService.retractPage(id, userId));
    }

    /** GET /api/v1/content/pages/{id}/history — 이력 목록 조회 */
    @GetMapping("/{id}/history")
    @PreAuthorize("hasAuthority('PAGE:HISTORY:READ')")
    public ResponseEntity<List<PageHistoryResponse>> getPageHistory(@PathVariable Long id) {
        return ResponseEntity.ok(pageService.getPageHistory(id));
    }

    /** POST /api/v1/content/pages/{id}/rollback/{version} — 롤백 */
    @PostMapping("/{id}/rollback/{version}")
    @PreAuthorize("hasAuthority('PAGE:ROLLBACK')")
    public ResponseEntity<PageResponse> rollbackPage(
            @PathVariable Long id,
            @PathVariable int version,
            @AuthenticationPrincipal Long userId
    ) {
        return ResponseEntity.ok(pageService.rollbackPage(id, version, userId));
    }

    /** GET /api/v1/content/pages/by-slug/{slug} — slug로 발행 페이지 조회 (시민 라우팅) */
    @GetMapping("/by-slug/{slug}")
    public ResponseEntity<PageResponse> getBySlug(
            @PathVariable String slug,
            @RequestParam Long siteId
    ) {
        return ResponseEntity.ok(pageService.getPublishedPageBySlug(siteId, slug));
    }
}
