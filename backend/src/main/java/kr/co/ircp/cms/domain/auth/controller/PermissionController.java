package kr.co.ircp.cms.domain.auth.controller;

import kr.co.ircp.cms.domain.auth.dto.PermissionSummary;
import kr.co.ircp.cms.domain.auth.service.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 권한 카탈로그 REST 컨트롤러.
 *
 * <p>SPEC-CMS-002 REQ-AUTH-013 — GET /api/v1/permissions.
 * 권한 카탈로그 조회 (SUPER_ADMIN 전용).
 */
@RestController
@RequestMapping("/api/v1/permissions")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class PermissionController {

    private final PermissionService permissionService;

    /**
     * 전체 권한 카탈로그 조회.
     *
     * <p>권한: SUPER_ADMIN.
     * GET /api/v1/permissions → PermissionSummary[]
     */
    @GetMapping
    public List<PermissionSummary> list() {
        return permissionService.findAll();
    }
}
