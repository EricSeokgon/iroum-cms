package kr.co.ircp.cms.domain.search.controller;

import jakarta.validation.Valid;
import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.search.dto.SynonymCreateRequest;
import kr.co.ircp.cms.domain.search.dto.SynonymUpdateRequest;
import kr.co.ircp.cms.domain.search.entity.SearchSynonym;
import kr.co.ircp.cms.domain.search.service.SynonymService;
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
 * 동의어 사전 REST 컨트롤러 (ADMIN 전용).
 *
 * <p>SPEC-CMS-010 §6.5: 동의어 CRUD.
 */
// @MX:NOTE: [AUTO] SPEC-CMS-010 동의어 관리 컨트롤러 (ADMIN 전용)
// @MX:SPEC: SPEC-CMS-010#REQ-SEARCH-009
@RestController
@RequestMapping("/api/v1/search/synonyms")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class SynonymController {

    private final SynonymService synonymService;

    @GetMapping
    public ResponseEntity<PageResponse<SearchSynonym>> list(
            @RequestParam(name = "locale", required = false, defaultValue = "ko") String locale,
            @RequestParam(name = "page", required = false, defaultValue = "0") int page,
            @RequestParam(name = "size", required = false, defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(synonymService.listSynonyms(locale, page, size));
    }

    @PostMapping
    public ResponseEntity<SearchSynonym> create(
            @Valid @RequestBody SynonymCreateRequest req,
            @AuthenticationPrincipal(expression = "userId") Long userId
    ) {
        SearchSynonym created = synonymService.createSynonym(req, userId);
        return ResponseEntity.created(URI.create("/api/v1/search/synonyms/" + created.getId()))
                .body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<SearchSynonym> update(
            @PathVariable Long id,
            @Valid @RequestBody SynonymUpdateRequest req,
            @AuthenticationPrincipal(expression = "userId") Long userId
    ) {
        return ResponseEntity.ok(synonymService.updateSynonym(id, req, userId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal(expression = "userId") Long userId
    ) {
        synonymService.deleteSynonym(id, userId);
        return ResponseEntity.noContent().build();
    }
}
