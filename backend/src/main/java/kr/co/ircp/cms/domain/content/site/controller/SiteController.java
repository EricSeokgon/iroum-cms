package kr.co.ircp.cms.domain.content.site.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import kr.co.ircp.cms.domain.content.site.dto.SiteResponse;
import kr.co.ircp.cms.domain.content.site.dto.SiteUpdateRequest;
import kr.co.ircp.cms.domain.content.site.service.SiteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 사이트 REST 컨트롤러.
 * REQ-CONTENT-003-D: 사이트 마스터 API
 */
@RestController
@RequestMapping("/api/v1/content/sites")
@RequiredArgsConstructor
public class SiteController {

    private final SiteService siteService;

    /** GET /api/v1/content/sites/current — 현재 사이트 조회 */
    @GetMapping("/current")
    public ResponseEntity<SiteResponse> getCurrentSite(HttpServletRequest request) {
        String domain = request.getServerName();
        return ResponseEntity.ok(siteService.getCurrentSite(domain));
    }

    /** PUT /api/v1/content/sites/{id} — 사이트 수정 */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('SITE:WRITE')")
    public ResponseEntity<SiteResponse> updateSite(
            @PathVariable Long id,
            @Valid @RequestBody SiteUpdateRequest request
    ) {
        return ResponseEntity.ok(siteService.updateSite(id, request));
    }

    /** POST /api/v1/content/sites — 신규 사이트 생성 (멀티사이트 비활성화 시 409) */
    @PostMapping
    @PreAuthorize("hasAuthority('SYSTEM:ADMIN')")
    public ResponseEntity<SiteResponse> createSite(
            @Valid @RequestBody SiteUpdateRequest request
    ) {
        return ResponseEntity.status(201).body(siteService.createSite(request));
    }
}
