package kr.co.ircp.cms.domain.auth.controller;

import jakarta.validation.Valid;
import kr.co.ircp.cms.domain.auth.dto.RoleCreateRequest;
import kr.co.ircp.cms.domain.auth.dto.RoleDetail;
import kr.co.ircp.cms.domain.auth.dto.RoleSummary;
import kr.co.ircp.cms.domain.auth.dto.RoleUpdateRequest;
import kr.co.ircp.cms.domain.auth.security.JwtPrincipal;
import kr.co.ircp.cms.domain.auth.service.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

/**
 * 역할 관리 REST 컨트롤러.
 *
 * <p>SPEC-CMS-002 REQ-AUTH-013 — GET/POST/PUT/DELETE /api/v1/roles.
 * 모든 엔드포인트는 SUPER_ADMIN 전용.
 */
// @MX:ANCHOR: [AUTO] RoleController — 역할 CRUD API 진입점
// @MX:REASON: RoleService 호출 + Spring Security + 외부 API 경계 (fan_in >= 3)
@RestController
@RequestMapping("/api/v1/roles")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class RoleController {

    private final RoleService roleService;

    /**
     * 전체 역할 목록 조회.
     *
     * <p>권한: SUPER_ADMIN.
     * GET /api/v1/roles → RoleSummary[]
     */
    @GetMapping
    public List<RoleSummary> list() {
        return roleService.findAll();
    }

    /**
     * 역할 상세 조회.
     *
     * <p>권한: SUPER_ADMIN.
     * GET /api/v1/roles/{code} → RoleDetail
     */
    @GetMapping("/{code}")
    public RoleDetail detail(@PathVariable String code) {
        return roleService.findByCode(code);
    }

    /**
     * 역할 신규 생성.
     *
     * <p>권한: SUPER_ADMIN.
     * POST /api/v1/roles → 201 RoleDetail
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RoleDetail create(@Valid @RequestBody RoleCreateRequest req,
                             @AuthenticationPrincipal JwtPrincipal principal) {
        return roleService.create(req, principal.userId());
    }

    /**
     * 역할 정보 수정.
     *
     * <p>권한: SUPER_ADMIN.
     * PUT /api/v1/roles/{code} → RoleDetail
     */
    @PutMapping("/{code}")
    public RoleDetail update(@PathVariable String code,
                             @Valid @RequestBody RoleUpdateRequest req,
                             @AuthenticationPrincipal JwtPrincipal principal) {
        return roleService.update(code, req, principal.userId());
    }

    /**
     * 역할 삭제.
     *
     * <p>권한: SUPER_ADMIN.
     * DELETE /api/v1/roles/{code} → 204 No Content
     * 400: is_system=true 역할
     * 409: 사용자 매핑 존재
     */
    @DeleteMapping("/{code}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String code,
                       @AuthenticationPrincipal JwtPrincipal principal) {
        roleService.delete(code, principal.userId());
    }

    /**
     * 역할 권한 재설정 (atomic replace).
     *
     * <p>권한: SUPER_ADMIN.
     * PUT /api/v1/roles/{code}/permissions → 200
     * 요청 Body: Set&lt;String&gt; permissionCodes
     */
    @PutMapping("/{code}/permissions")
    public void updatePermissions(@PathVariable String code,
                                  @RequestBody Set<String> permissionCodes,
                                  @AuthenticationPrincipal JwtPrincipal principal) {
        roleService.updatePermissions(code, permissionCodes, principal.userId());
    }
}
