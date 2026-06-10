package kr.co.ircp.cms.domain.board.controller;

import jakarta.validation.Valid;
import kr.co.ircp.cms.domain.board.dto.PublicationCategoryCreateRequest;
import kr.co.ircp.cms.domain.board.dto.PublicationCategoryDto;
import kr.co.ircp.cms.domain.board.dto.PublicationCategoryUpdateRequest;
import kr.co.ircp.cms.domain.board.service.PublicationCategoryAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/**
 * 발간자료 카테고리 관리자 REST 컨트롤러.
 * SPEC-CMS-PUB-CAT-001 REQ-PCA-001~004
 */
@RestController
@RequestMapping("/api/v1/admin/publication-categories")
@PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
@RequiredArgsConstructor
public class PublicationCategoryAdminController {

    private final PublicationCategoryAdminService service;

    /** REQ-PCA-004: 어드민용 카테고리 트리 조회 (INACTIVE 포함). */
    @GetMapping
    public ResponseEntity<List<PublicationCategoryDto>> listAll() {
        return ResponseEntity.ok(service.listAllForAdmin());
    }

    /** REQ-PCA-001: 카테고리 생성. */
    @PostMapping
    public ResponseEntity<PublicationCategoryDto> create(
            @Valid @RequestBody PublicationCategoryCreateRequest request) {
        PublicationCategoryDto created = service.createCategory(request);
        return ResponseEntity
                .created(URI.create("/api/v1/admin/publication-categories/" + created.id()))
                .body(created);
    }

    /** REQ-PCA-002: 카테고리 수정. */
    @PutMapping("/{id}")
    public ResponseEntity<PublicationCategoryDto> update(
            @PathVariable Long id,
            @Valid @RequestBody PublicationCategoryUpdateRequest request) {
        return ResponseEntity.ok(service.updateCategory(id, request));
    }

    /** REQ-PCA-003: 카테고리 삭제 (자식·연결 발간자료 없어야 함). */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.deleteCategory(id);
        return ResponseEntity.noContent().build();
    }
}
