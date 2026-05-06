package kr.co.ircp.cms.domain.policy.dispatch.controller;

import jakarta.validation.Valid;
import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.policy.dispatch.dto.DispatchScheduleCreateRequest;
import kr.co.ircp.cms.domain.policy.dispatch.dto.DispatchScheduleResponse;
import kr.co.ircp.cms.domain.policy.dispatch.service.PolicyDispatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * 정책 알림 발송 예약 REST 컨트롤러.
 * REQ-POLICY-003
 */
@RestController
@RequestMapping("/api/v1/policy/admin/dispatch")
@RequiredArgsConstructor
public class PolicyDispatchController {

    private final PolicyDispatchService dispatchService;

    /** GET /api/v1/policy/admin/dispatch/schedules */
    @GetMapping("/schedules")
    public ResponseEntity<PageResponse<DispatchScheduleResponse>> listSchedules(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long policyId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(dispatchService.listSchedules(status, policyId, page, size));
    }

    /** POST /api/v1/policy/admin/dispatch/schedules */
    @PostMapping("/schedules")
    public ResponseEntity<DispatchScheduleResponse> createSchedule(
            @Valid @RequestBody DispatchScheduleCreateRequest request) {
        DispatchScheduleResponse created = dispatchService.createSchedule(request);
        return ResponseEntity.created(URI.create("/api/v1/policy/admin/dispatch/schedules/" + created.id()))
                .body(created);
    }

    /** POST /api/v1/policy/admin/dispatch/schedules/{id}/trigger */
    @PostMapping("/schedules/{id}/trigger")
    public ResponseEntity<DispatchScheduleResponse> triggerNow(@PathVariable Long id) {
        return ResponseEntity.ok(dispatchService.triggerNow(id));
    }

    /** POST /api/v1/policy/admin/dispatch/schedules/{id}/cancel */
    @PostMapping("/schedules/{id}/cancel")
    public ResponseEntity<Void> cancelSchedule(@PathVariable Long id) {
        dispatchService.cancelSchedule(id);
        return ResponseEntity.noContent().build();
    }
}
