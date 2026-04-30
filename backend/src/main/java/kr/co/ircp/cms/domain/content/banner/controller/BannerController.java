package kr.co.ircp.cms.domain.content.banner.controller;

import jakarta.validation.Valid;
import kr.co.ircp.cms.domain.content.banner.dto.BannerRequest;
import kr.co.ircp.cms.domain.content.banner.dto.BannerResponse;
import kr.co.ircp.cms.domain.content.banner.service.BannerService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 배너 REST 컨트롤러.
 * REQ-CONTENT-009-D: 배너 CRUD + 클릭 이벤트
 */
@RestController
@RequestMapping("/api/v1/content/banners")
@RequiredArgsConstructor
public class BannerController {

    private final BannerService bannerService;

    /**
     * 그룹별 활성 배너 조회 (PUBLIC).
     * REQ-CONTENT-009-D-2
     */
    @GetMapping
    public List<BannerResponse> getActiveBanners(@RequestParam String group) {
        return bannerService.getActiveBannersByGroup(group);
    }

    /**
     * 배너 등록.
     * REQ-CONTENT-009-D-1
     */
    @PostMapping
    @PreAuthorize("hasAuthority('CONTENT:WRITE')")
    public ResponseEntity<BannerResponse> registerBanner(@Valid @RequestBody BannerRequest request) {
        return ResponseEntity.ok(bannerService.registerBanner(request));
    }

    /**
     * 배너 수정.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('CONTENT:WRITE')")
    public ResponseEntity<BannerResponse> updateBanner(
            @PathVariable Long id,
            @Valid @RequestBody BannerRequest request) {
        return ResponseEntity.ok(bannerService.updateBanner(id, request));
    }

    /**
     * 배너 삭제.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('CONTENT:WRITE')")
    public ResponseEntity<Void> deleteBanner(@PathVariable Long id) {
        bannerService.deleteBanner(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * 클릭 이벤트 기록 (PUBLIC).
     * REQ-CONTENT-009-D-3
     */
    @PostMapping("/{id}/click")
    public ResponseEntity<Void> recordClick(@PathVariable Long id) {
        bannerService.recordClick(id);
        return ResponseEntity.noContent().build();
    }
}
