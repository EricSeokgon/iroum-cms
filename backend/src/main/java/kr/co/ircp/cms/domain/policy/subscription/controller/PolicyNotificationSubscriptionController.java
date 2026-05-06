package kr.co.ircp.cms.domain.policy.subscription.controller;

import jakarta.validation.Valid;
import kr.co.ircp.cms.domain.policy.subscription.dto.SubscriptionEntry;
import kr.co.ircp.cms.domain.policy.subscription.dto.SubscriptionUpdateRequest;
import kr.co.ircp.cms.domain.policy.subscription.service.PolicyNotificationSubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 정책 알림 수신 동의 REST 컨트롤러.
 * REQ-POLICY-004
 *
 * NOTE: 인증 적용 시 SecurityContext 의 user_id 를 사용해야 함.
 *       1차 구현은 단순화를 위해 query param 으로 받고, sync 에서 SecurityContext 로 전환.
 */
@RestController
@RequestMapping("/api/v1/policy/subscriptions")
@RequiredArgsConstructor
public class PolicyNotificationSubscriptionController {

    private final PolicyNotificationSubscriptionService subscriptionService;

    /** GET /api/v1/policy/subscriptions/me */
    @GetMapping("/me")
    public ResponseEntity<List<SubscriptionEntry>> getMine(@RequestParam Long userId) {
        return ResponseEntity.ok(subscriptionService.getMySubscriptions(userId));
    }

    /** PUT /api/v1/policy/subscriptions/me */
    @PutMapping("/me")
    public ResponseEntity<Void> updateMine(
            @RequestParam Long userId,
            @Valid @RequestBody SubscriptionUpdateRequest request) {
        subscriptionService.updateMySubscriptions(userId, request);
        return ResponseEntity.noContent().build();
    }
}
