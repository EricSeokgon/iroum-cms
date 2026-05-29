package kr.co.ircp.cms.domain.dashboard.preference.controller;

import jakarta.validation.Valid;
import kr.co.ircp.cms.domain.dashboard.preference.dto.PreferenceResponse;
import kr.co.ircp.cms.domain.dashboard.preference.dto.PreferenceUpdateRequest;
import kr.co.ircp.cms.domain.dashboard.preference.dto.WidgetVisibilityRequest;
import kr.co.ircp.cms.domain.dashboard.preference.service.UserDashboardPreferenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * SPEC-CMS-DASHBOARD-PERSONALIZE-001 §7 — 사용자별 대시보드 환경설정 REST 컨트롤러.
 *
 * <p>모든 엔드포인트는 본인 데이터에만 접근한다 — userId 는 {@code @AuthenticationPrincipal}
 * 로부터 추출되며, 경로/바디로 별도 전달받지 않는다.
 *
 * <p>엔드포인트 매핑:
 * <table>
 *   <tr><th>Method</th><th>Path</th><th>REQ</th></tr>
 *   <tr><td>GET</td><td>/preference</td><td>REQ-DP-002-1~3 (AC-DP-API-1: lazy 생성)</td></tr>
 *   <tr><td>PATCH</td><td>/preference</td><td>REQ-DP-002-4 (AC-DP-API-2: 부분 갱신)</td></tr>
 *   <tr><td>POST</td><td>/preference/reset</td><td>REQ-DP-002-5 (AC-DP-002-5: 스타일 초기화)</td></tr>
 *   <tr><td>PATCH</td><td>/preference/widgets/{layoutId}/hidden</td><td>REQ-DP-001-1,2</td></tr>
 *   <tr><td>POST</td><td>/preference/widgets/{layoutId}/show-all</td><td>REQ-DP-001-5</td></tr>
 * </table>
 */
// @MX:ANCHOR: [AUTO] UserDashboardPreferenceController — SPEC-CMS-DASHBOARD-PERSONALIZE-001 의 5/6 엔드포인트 진입점
// @MX:REASON: 5 endpoints fan_in ≥ 3 (Vue store + Cypress E2E + IT 테스트) — 계약 변경은 다층 회귀
// @MX:SPEC: SPEC-CMS-DASHBOARD-PERSONALIZE-001 §7 / acceptance.md AC-DP-001/002/API
@RestController
@RequestMapping("/api/v1/dashboard/preference")
@RequiredArgsConstructor
public class UserDashboardPreferenceController {

    private final UserDashboardPreferenceService service;

    /** REQ-DP-002-1~3 / AC-DP-API-1: 본인 환경설정 조회 (없으면 lazy 생성). */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PreferenceResponse> get(
            @AuthenticationPrincipal(expression = "userId") Long userId) {
        return ResponseEntity.ok(service.getOrCreate(userId));
    }

    /** REQ-DP-002-4 / AC-DP-API-2: 부분 갱신. */
    @PatchMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PreferenceResponse> update(
            @AuthenticationPrincipal(expression = "userId") Long userId,
            @Valid @RequestBody PreferenceUpdateRequest req) {
        return ResponseEntity.ok(service.update(userId, req));
    }

    /** REQ-DP-002-5 / AC-DP-002-5: 스타일을 DEFAULT 로 초기화 (hidden 보존). */
    @PostMapping("/reset")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PreferenceResponse> reset(
            @AuthenticationPrincipal(expression = "userId") Long userId) {
        return ResponseEntity.ok(service.reset(userId));
    }

    /** REQ-DP-001-1 / 001-2: 단건 위젯 가시성 토글. */
    @PatchMapping("/widgets/{layoutId}/hidden")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PreferenceResponse> toggleVisibility(
            @AuthenticationPrincipal(expression = "userId") Long userId,
            @PathVariable Long layoutId,
            @Valid @RequestBody WidgetVisibilityRequest req) {
        return ResponseEntity.ok(
                service.toggleVisibility(userId, layoutId, req.instanceId(), req.hidden()));
    }

    /** REQ-DP-001-5 / AC-DP-001-5: 특정 레이아웃의 모든 숨김 해제. */
    @PostMapping("/widgets/{layoutId}/show-all")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PreferenceResponse> showAll(
            @AuthenticationPrincipal(expression = "userId") Long userId,
            @PathVariable Long layoutId) {
        return ResponseEntity.ok(service.showAllWidgets(userId, layoutId));
    }
}
