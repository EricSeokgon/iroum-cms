package kr.co.ircp.cms.domain.content.seo.controller;

import jakarta.validation.Valid;
import kr.co.ircp.cms.domain.content.seo.dto.SeoRedirectRequest;
import kr.co.ircp.cms.domain.content.seo.dto.SeoRedirectResponse;
import kr.co.ircp.cms.domain.content.seo.service.SeoRedirectService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * SEO 리다이렉트 REST 컨트롤러.
 * REQ-CONTENT-005-D-8: URL 리다이렉트 관리 API
 */
@RestController
@RequestMapping("/api/v1/content/seo/redirects")
@RequiredArgsConstructor
public class SeoRedirectController {

    private final SeoRedirectService seoRedirectService;

    /**
     * 리다이렉트 목록 조회.
     */
    @GetMapping
    @PreAuthorize("hasAuthority('SYSTEM:READ')")
    public List<SeoRedirectResponse> getAllRedirects() {
        return seoRedirectService.getAllRedirects();
    }

    /**
     * 리다이렉트 생성.
     */
    @PostMapping
    @PreAuthorize("hasAuthority('SYSTEM:ADMIN')")
    public ResponseEntity<SeoRedirectResponse> createRedirect(
            @Valid @RequestBody SeoRedirectRequest request) {
        return ResponseEntity.ok(seoRedirectService.createRedirect(request));
    }

    /**
     * 리다이렉트 삭제.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('SYSTEM:ADMIN')")
    public ResponseEntity<Void> deleteRedirect(@PathVariable Long id) {
        seoRedirectService.deleteRedirect(id);
        return ResponseEntity.noContent().build();
    }
}
