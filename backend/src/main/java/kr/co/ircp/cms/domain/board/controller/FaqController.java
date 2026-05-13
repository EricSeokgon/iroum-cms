package kr.co.ircp.cms.domain.board.controller;

import jakarta.validation.Valid;
import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.board.dto.FaqCategoryCount;
import kr.co.ircp.cms.domain.board.dto.FaqCreateRequest;
import kr.co.ircp.cms.domain.board.dto.FaqDetail;
import kr.co.ircp.cms.domain.board.dto.FaqReorderRequest;
import kr.co.ircp.cms.domain.board.dto.FaqSummary;
import kr.co.ircp.cms.domain.board.dto.FaqUpdateRequest;
import kr.co.ircp.cms.domain.board.service.FaqService;
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
import java.util.List;

/**
 * FAQ REST 컨트롤러.
 * REQ-BOARD-007: FAQ 카테고리·정렬·검색 + 일괄 정렬 변경
 */
@RestController
@RequestMapping("/api/v1/faqs")
@RequiredArgsConstructor
public class FaqController {

    private final FaqService faqService;

    /** GET /api/v1/faqs — FAQ 목록 페이징 조회 (공개). */
    @GetMapping
    public ResponseEntity<PageResponse<FaqSummary>> listFaqs(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(faqService.listFaqs(category, keyword, page, size));
    }

    /** GET /api/v1/faqs/categories — 카테고리별 FAQ 개수 (공개). */
    @GetMapping("/categories")
    public ResponseEntity<List<FaqCategoryCount>> getCategories() {
        return ResponseEntity.ok(faqService.getCategories());
    }

    /** GET /api/v1/faqs/{id} — FAQ 단건 조회 (공개, 조회수 증가). */
    @GetMapping("/{id}")
    public ResponseEntity<FaqDetail> getFaq(@PathVariable Long id) {
        return ResponseEntity.ok(faqService.getFaq(id));
    }

    /** POST /api/v1/faqs — FAQ 생성 (관리자). */
    @PostMapping
    @PreAuthorize("hasAuthority('CONTENT:WRITE') or hasRole('ADMIN') or hasRole('SUPER_ADMIN') or hasRole('CONTENT_ADMIN')")
    public ResponseEntity<FaqDetail> createFaq(
            @Valid @RequestBody FaqCreateRequest request,
            @AuthenticationPrincipal(expression = "userId") Long userId
    ) {
        FaqDetail created = faqService.createFaq(request, userId);
        return ResponseEntity.created(URI.create("/api/v1/faqs/" + created.id())).body(created);
    }

    /** PUT /api/v1/faqs/reorder — FAQ 정렬 순서 일괄 변경 (관리자). */
    @PutMapping("/reorder")
    @PreAuthorize("hasAuthority('CONTENT:WRITE') or hasRole('ADMIN') or hasRole('SUPER_ADMIN') or hasRole('CONTENT_ADMIN')")
    public ResponseEntity<Void> reorderFaqs(@Valid @RequestBody FaqReorderRequest request) {
        faqService.reorderFaqs(request);
        return ResponseEntity.noContent().build();
    }

    /** PUT /api/v1/faqs/{id} — FAQ 수정 (관리자). */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('CONTENT:WRITE') or hasRole('ADMIN') or hasRole('SUPER_ADMIN') or hasRole('CONTENT_ADMIN')")
    public ResponseEntity<FaqDetail> updateFaq(
            @PathVariable Long id,
            @Valid @RequestBody FaqUpdateRequest request
    ) {
        return ResponseEntity.ok(faqService.updateFaq(id, request));
    }

    /** DELETE /api/v1/faqs/{id} — FAQ 삭제 (관리자). */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('CONTENT:WRITE') or hasRole('ADMIN') or hasRole('SUPER_ADMIN') or hasRole('CONTENT_ADMIN')")
    public ResponseEntity<Void> deleteFaq(@PathVariable Long id) {
        faqService.deleteFaq(id);
        return ResponseEntity.noContent().build();
    }
}
