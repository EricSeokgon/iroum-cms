package kr.co.ircp.cms.domain.policy.tracking.controller;

import jakarta.validation.Valid;
import kr.co.ircp.cms.domain.policy.tracking.dto.ConversionStats;
import kr.co.ircp.cms.domain.policy.tracking.dto.TrackEventRequest;
import kr.co.ircp.cms.domain.policy.tracking.service.PolicyTrackingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 정책 신청·클릭 추적 REST 컨트롤러.
 * REQ-POLICY-005
 */
@RestController
@RequestMapping("/api/v1/policy")
@RequiredArgsConstructor
public class PolicyTrackingController {

    private final PolicyTrackingService trackingService;

    /** POST /api/v1/policy/programs/{id}/track */
    @PostMapping("/programs/{id}/track")
    public ResponseEntity<Void> trackEvent(
            @PathVariable Long id,
            @RequestParam Long userId,
            @Valid @RequestBody TrackEventRequest request) {
        trackingService.trackEvent(userId, id, request);
        return ResponseEntity.noContent().build();
    }

    /** GET /api/v1/policy/admin/stats/conversion */
    @GetMapping("/admin/stats/conversion")
    public ResponseEntity<ConversionStats> getConversionStats(@RequestParam Long policyId) {
        return ResponseEntity.ok(trackingService.getConversionStats(policyId));
    }
}
