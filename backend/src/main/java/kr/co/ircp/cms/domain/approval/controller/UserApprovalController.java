package kr.co.ircp.cms.domain.approval.controller;

import jakarta.validation.Valid;
import kr.co.ircp.cms.domain.approval.dto.BulkApproveRequest;
import kr.co.ircp.cms.domain.approval.dto.BulkOperationResult;
import kr.co.ircp.cms.domain.approval.dto.BulkRejectRequest;
import kr.co.ircp.cms.domain.approval.dto.RejectRequest;
import kr.co.ircp.cms.domain.approval.dto.UserApprovalSummary;
import kr.co.ircp.cms.domain.approval.service.UserApprovalService;
import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.auth.security.JwtPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 가입 승인 관리 REST 컨트롤러.
 *
 * <p>SPEC-CMS-USER-APPROVAL-001 REQ-UA-007~016/020 —
 * 대기열 조회, 단건/일괄 승인·거절. SUPER_ADMIN/DEPT_ADMIN 전용.
 *
 * <p>인가는 클래스 레벨 {@code @PreAuthorize} 로 통일하여 모든 엔드포인트에 동일 정책을 적용한다.
 */
// @MX:ANCHOR: [AUTO] UserApprovalController — 가입 승인 API 진입점 (대기열·승인·거절 6개 엔드포인트)
// @MX:REASON: 외부 어드민 클라이언트, Spring Security 인가 필터, API 문서에서 참조 (fan_in >= 3)
// @MX:SPEC: SPEC-CMS-USER-APPROVAL-001#REQ-UA-020
@RestController
@RequestMapping("/api/v1/users/approvals")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','DEPT_ADMIN')")
public class UserApprovalController {

    private final UserApprovalService approvalService;

    public UserApprovalController(UserApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    /** 대기열 목록 (검색/페이지) — REQ-UA-007/008. */
    @GetMapping
    public PageResponse<UserApprovalSummary> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword) {
        UserApprovalService.PageResult result =
                approvalService.getPendingApprovals(page, size, keyword);
        return PageResponse.of(result.content(), page, size, result.totalElements());
    }

    /** 대기 사용자 상세 — REQ-UA-009. */
    @GetMapping("/{userId}")
    public UserApprovalSummary detail(@PathVariable long userId) {
        return approvalService.getPendingDetail(userId);
    }

    /** 단건 승인 — REQ-UA-010. */
    @PostMapping("/{userId}/approve")
    public void approve(@PathVariable long userId,
                        @AuthenticationPrincipal JwtPrincipal principal) {
        approvalService.approve(userId, principal.userId());
    }

    /** 단건 거절(사유 필수) — REQ-UA-011/012. */
    @PostMapping("/{userId}/reject")
    public void reject(@PathVariable long userId,
                       @Valid @RequestBody RejectRequest request,
                       @AuthenticationPrincipal JwtPrincipal principal) {
        approvalService.reject(userId, request.reason(), principal.userId());
    }

    /** 일괄 승인 — REQ-UA-014/016. */
    @PostMapping("/bulk-approve")
    public BulkOperationResult bulkApprove(@Valid @RequestBody BulkApproveRequest request,
                                           @AuthenticationPrincipal JwtPrincipal principal) {
        return approvalService.bulkApprove(request.userIds(), principal.userId());
    }

    /** 일괄 거절(공통 사유) — REQ-UA-015/016. */
    @PostMapping("/bulk-reject")
    public BulkOperationResult bulkReject(@Valid @RequestBody BulkRejectRequest request,
                                          @AuthenticationPrincipal JwtPrincipal principal) {
        return approvalService.bulkReject(request.userIds(), request.reason(), principal.userId());
    }
}
