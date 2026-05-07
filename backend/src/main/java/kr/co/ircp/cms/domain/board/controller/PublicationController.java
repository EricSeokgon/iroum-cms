package kr.co.ircp.cms.domain.board.controller;

import jakarta.validation.Valid;
import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.board.dto.PublicationCategoryDto;
import kr.co.ircp.cms.domain.board.dto.PublicationCreateRequest;
import kr.co.ircp.cms.domain.board.dto.PublicationDetail;
import kr.co.ircp.cms.domain.board.dto.PublicationSummary;
import kr.co.ircp.cms.domain.board.dto.PublicationUpdateRequest;
import kr.co.ircp.cms.domain.board.dto.ZipDownloadRequest;
import kr.co.ircp.cms.domain.board.dto.ZipDownloadResponse;
import kr.co.ircp.cms.domain.board.service.PublicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
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
 * 발간자료(Publication) REST 컨트롤러.
 * REQ-BOARD-012: 발간자료 카테고리·메타·다운로드 통계·ZIP 아카이브
 */
@RestController
@RequestMapping("/api/v1/publications")
@RequiredArgsConstructor
public class PublicationController {

    private final PublicationService publicationService;

    /** GET /api/v1/publications — 발간자료 목록 페이징 조회 (공개). */
    @GetMapping
    public ResponseEntity<PageResponse<PublicationSummary>> listPublications(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) String documentType,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(publicationService.listPublications(
                year, month, documentType, categoryId, keyword, page, size));
    }

    /** GET /api/v1/publications/categories — 카테고리 트리 조회 (공개). */
    @GetMapping("/categories")
    public ResponseEntity<List<PublicationCategoryDto>> getCategories() {
        return ResponseEntity.ok(publicationService.getCategories());
    }

    /** GET /api/v1/publications/{id} — 발간자료 단건 조회 (공개, 조회수 증가). */
    @GetMapping("/{id}")
    public ResponseEntity<PublicationDetail> getPublication(@PathVariable Long id) {
        return ResponseEntity.ok(publicationService.getPublication(id));
    }

    /** POST /api/v1/publications — 발간자료 생성 (관리자). */
    @PostMapping
    @PreAuthorize("hasAuthority('CONTENT:WRITE') or hasRole('ADMIN') or hasRole('SUPER_ADMIN') or hasRole('CONTENT_ADMIN')")
    public ResponseEntity<PublicationDetail> createPublication(
            @Valid @RequestBody PublicationCreateRequest request,
            Authentication authentication
    ) {
        Long authorId = resolveUserId(authentication);
        PublicationDetail created = publicationService.createPublication(request, authorId);
        return ResponseEntity.created(URI.create("/api/v1/publications/" + created.postId())).body(created);
    }

    /** PUT /api/v1/publications/{id} — 발간자료 수정 (관리자). */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('CONTENT:WRITE') or hasRole('ADMIN') or hasRole('SUPER_ADMIN') or hasRole('CONTENT_ADMIN')")
    public ResponseEntity<PublicationDetail> updatePublication(
            @PathVariable Long id,
            @Valid @RequestBody PublicationUpdateRequest request
    ) {
        return ResponseEntity.ok(publicationService.updatePublication(id, request));
    }

    /** DELETE /api/v1/publications/{id} — 발간자료 삭제 (관리자). */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('CONTENT:WRITE') or hasRole('ADMIN') or hasRole('SUPER_ADMIN') or hasRole('CONTENT_ADMIN')")
    public ResponseEntity<Void> deletePublication(@PathVariable Long id) {
        publicationService.deletePublication(id);
        return ResponseEntity.noContent().build();
    }

    /** POST /api/v1/publications/{id}/download-zip — ZIP 다운로드 요청 (익명 허용). */
    @PostMapping("/{id}/download-zip")
    public ResponseEntity<ZipDownloadResponse> requestZipDownload(
            @PathVariable Long id,
            @Valid @RequestBody ZipDownloadRequest request,
            Authentication authentication
    ) {
        // 익명 사용자 허용 (Authentication 이 null 이거나 anonymous 인 경우 requestedBy=null)
        Long requestedBy = resolveUserId(authentication);
        return ResponseEntity.ok(publicationService.requestZipDownload(id, request, requestedBy));
    }

    /**
     * Spring Security Authentication에서 사용자 ID를 추출.
     * principal이 Long이면 직접 사용하고, 그렇지 않으면 null(익명) 반환.
     */
    private Long resolveUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof Long userId) {
            return userId;
        }
        if (principal instanceof Number num) {
            return num.longValue();
        }
        // anonymous principal (String "anonymousUser") 등은 null로 처리
        return null;
    }
}
