package kr.co.ircp.cms.domain.content.banner.controller;

import jakarta.validation.Valid;
import kr.co.ircp.cms.domain.content.banner.dto.BannerRequest;
import kr.co.ircp.cms.domain.content.banner.dto.BannerResponse;
import kr.co.ircp.cms.domain.content.banner.service.BannerService;
import kr.co.ircp.cms.domain.auth.security.JwtPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
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
     * 배너 목록 조회.
     * - siteId만 있으면: 관리자용 전체 목록 (CONTENT:READ 권한 필요)
     * - group만 있으면: 공개 활성 배너 조회 (인증 불필요)
     * REQ-CONTENT-009-D-2
     */
    @GetMapping
    public List<BannerResponse> getBanners(
            @RequestParam(required = false) Long siteId,
            @RequestParam(required = false) String groupCode,
            @RequestParam(required = false) String group,
            @AuthenticationPrincipal JwtPrincipal principal
    ) {
        // 관리자 요청: siteId 파라미터가 있으면 CONTENT:READ 권한 필수 (REQ-CONTENT-009-D-2)
        if (siteId != null) {
            if (principal == null || !principal.permissions().contains("CONTENT:READ")) {
                throw new AccessDeniedException("CONTENT:READ 권한이 필요합니다.");
            }
            return bannerService.listBanners(siteId, groupCode);
        }
        // 공개 요청: group 파라미터로 활성 배너 조회
        String targetGroup = (group != null && !group.isBlank()) ? group
                : (groupCode != null && !groupCode.isBlank()) ? groupCode : "";
        return bannerService.getActiveBannersByGroup(targetGroup);
    }

    /**
     * 사이트별 배너 그룹 코드 목록 조회 (관리자용).
     */
    @GetMapping("/groups")
    @PreAuthorize("hasAuthority('CONTENT:READ')")
    public List<String> listGroups(@RequestParam(required = false) Long siteId) {
        return bannerService.listGroups(siteId);
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
