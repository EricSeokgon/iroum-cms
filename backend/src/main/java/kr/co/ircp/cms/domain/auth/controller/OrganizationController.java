package kr.co.ircp.cms.domain.auth.controller;

import jakarta.validation.Valid;
import kr.co.ircp.cms.domain.auth.dto.AssignOrganizationRequest;
import kr.co.ircp.cms.domain.auth.dto.OrganizationCreateRequest;
import kr.co.ircp.cms.domain.auth.dto.OrganizationDetail;
import kr.co.ircp.cms.domain.auth.dto.OrganizationHistoryEntry;
import kr.co.ircp.cms.domain.auth.dto.OrganizationSummary;
import kr.co.ircp.cms.domain.auth.dto.OrganizationTreeNode;
import kr.co.ircp.cms.domain.auth.dto.OrganizationUpdateRequest;
import kr.co.ircp.cms.domain.auth.security.JwtPrincipal;
import kr.co.ircp.cms.domain.auth.service.OrganizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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

import java.util.List;

/**
 * 부서·조직 관리 REST 컨트롤러.
 *
 * <p>SPEC-CMS-002 REQ-AUTH-014 — 조직 트리 CRUD API.
 * 권한: SUPER_ADMIN (전체), DEPT_ADMIN (조회 전용).
 */
@RestController
@RequestMapping("/api/v1/organizations")
@RequiredArgsConstructor
public class OrganizationController {

    private final OrganizationService orgService;

    /**
     * 조직 트리 조회 (재귀 계층).
     *
     * <p>GET /api/v1/organizations/tree
     */
    @GetMapping("/tree")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DEPT_ADMIN')")
    public ResponseEntity<List<OrganizationTreeNode>> getTree() {
        return ResponseEntity.ok(orgService.getTree());
    }

    /**
     * 조직 목록 조회 (flat, status 필터).
     *
     * <p>GET /api/v1/organizations?status=ACTIVE
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DEPT_ADMIN')")
    public ResponseEntity<List<OrganizationSummary>> findAll(
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(orgService.findAll(status));
    }

    /**
     * 조직 단건 조회.
     *
     * <p>GET /api/v1/organizations/{id}
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DEPT_ADMIN')")
    public ResponseEntity<OrganizationDetail> findById(@PathVariable long id) {
        return ResponseEntity.ok(orgService.findById(id));
    }

    /**
     * 조직 생성.
     *
     * <p>POST /api/v1/organizations
     */
    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<OrganizationDetail> create(
            @Valid @RequestBody OrganizationCreateRequest req,
            @AuthenticationPrincipal JwtPrincipal principal) {
        long actorId = principal.userId();
        OrganizationDetail created = orgService.create(req, actorId);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * 조직 수정.
     *
     * <p>PUT /api/v1/organizations/{id}
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<OrganizationDetail> update(
            @PathVariable long id,
            @Valid @RequestBody OrganizationUpdateRequest req,
            @AuthenticationPrincipal JwtPrincipal principal) {
        return ResponseEntity.ok(orgService.update(id, req, principal.userId()));
    }

    /**
     * 조직 삭제 (소프트 삭제).
     *
     * <p>DELETE /api/v1/organizations/{id}
     * 자식 노드 또는 소속 사용자 존재 시 409 반환.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Void> delete(
            @PathVariable long id,
            @AuthenticationPrincipal JwtPrincipal principal) {
        orgService.delete(id, principal.userId());
        return ResponseEntity.noContent().build();
    }

    /**
     * 조직 변경 이력 조회.
     *
     * <p>GET /api/v1/organizations/{id}/history
     */
    @GetMapping("/{id}/history")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<List<OrganizationHistoryEntry>> getHistory(@PathVariable long id) {
        return ResponseEntity.ok(orgService.getHistory(id));
    }

    /**
     * 사용자의 소속 조직 변경.
     *
     * <p>POST /api/v1/users/{userId}/organization
     */
    @PostMapping("/users/{userId}/organization")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DEPT_ADMIN')")
    public ResponseEntity<Void> assignUser(
            @PathVariable long userId,
            @RequestBody AssignOrganizationRequest req,
            @AuthenticationPrincipal JwtPrincipal principal) {
        orgService.assignUser(userId, req.organizationId(), principal.userId());
        return ResponseEntity.ok().build();
    }
}
