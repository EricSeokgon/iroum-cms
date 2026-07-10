package kr.co.ircp.cms.domain.point.controller;

import jakarta.validation.Valid;
import kr.co.ircp.cms.domain.audit.annotation.AuditLog;
import kr.co.ircp.cms.domain.point.dto.PointPolicyDto;
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
 * 포인트 정책 관리 REST 컨트롤러.
 *
 * <p>SPEC-CMS-POINTS-001 REQ-PNT-005 — 조회는 POINTS:READ, 변경은 POINTS:WRITE 권한 필요.
 * 변경은 @AuditLog로 감사 로그에 자동 적재된다.
 */
// @MX:NOTE: [AUTO] 정책 조회(READ)/변경(WRITE)이 권한이 달라 메서드 레벨 @PreAuthorize 사용.
@RestController
@RequestMapping("/api/v1/admin/points/policy")
@RequiredArgsConstructor
public class PointPolicyController {

    private final PointPolicyService pointPolicyService;

    /** GET — 현재 포인트 정책 조회 (POINTS:READ). */
    @GetMapping
    @PreAuthorize("hasAuthority('POINTS:READ')")
    public ResponseEntity<PointPolicyDto> getPolicy() {
        return ResponseEntity.ok(pointPolicyService.getPolicy());
    }

    /** PUT — 포인트 정책 변경 (POINTS:WRITE + 감사 로그). */
    @PutMapping
    @PreAuthorize("hasAuthority('POINTS:WRITE')")
    @AuditLog(action = "UPDATE", entityType = "PointPolicy")
    public ResponseEntity<PointPolicyDto> updatePolicy(
            @Valid @RequestBody PointPolicyUpdateRequest request) {
        return ResponseEntity.ok(pointPolicyService.updatePolicy(request));
    }
}
