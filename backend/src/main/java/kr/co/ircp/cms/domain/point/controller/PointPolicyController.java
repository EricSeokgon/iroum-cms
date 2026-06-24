package kr.co.ircp.cms.domain.point.controller;

import kr.co.ircp.cms.domain.point.dto.PointPolicyResponse;
import kr.co.ircp.cms.domain.point.dto.PointPolicyUpdateRequest;
import kr.co.ircp.cms.domain.point.service.PointPolicyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 포인트 정책 관리 REST 컨트롤러 (관리자용).
 * SPEC-CMS-POINTS-001 REQ-PNT-001, REQ-PNT-006
 */
@RestController
@RequestMapping("/api/v1/admin/points/policy")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
public class PointPolicyController {

    private final PointPolicyService policyService;

    /** GET /api/v1/admin/points/policy — 포인트 정책 조회. */
    @GetMapping
    public ResponseEntity<PointPolicyResponse> get() {
        return ResponseEntity.ok(policyService.getPolicy());
    }

    /** PUT /api/v1/admin/points/policy — 포인트 정책 수정. */
    @PutMapping
    public ResponseEntity<PointPolicyResponse> update(@RequestBody PointPolicyUpdateRequest request) {
        return ResponseEntity.ok(policyService.updatePolicy(request));
    }
}
