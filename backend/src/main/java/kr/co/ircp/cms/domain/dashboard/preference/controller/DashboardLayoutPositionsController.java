package kr.co.ircp.cms.domain.dashboard.preference.controller;

import jakarta.validation.Valid;
import kr.co.ircp.cms.domain.dashboard.preference.dto.PositionPatchRequest;
import kr.co.ircp.cms.domain.dashboard.preference.service.LayoutPositionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * SPEC-CMS-DASHBOARD-PERSONALIZE-001 §7 REQ-DP-003-2: 드래그앤드롭 결과 영속화 컨트롤러.
 *
 * <p>SPEC-CMS-008 의 {@code DashboardLayoutController} 는 수정하지 않고, 본 SPEC 의 신규
 * 엔드포인트 {@code PATCH /api/v1/dashboard/layouts/{id}/positions} 만 별도 컨트롤러로 노출한다.
 * Spring 의 RequestMapping 경로가 동일 prefix 를 공유해도 메서드/하위 경로가 겹치지 않아 충돌 없다.
 *
 * <p>응답 코드:
 * <ul>
 *   <li>204 No Content — 정상 갱신</li>
 *   <li>400 Bad Request — 위젯 겹침 (REQ-DP-003-3)</li>
 *   <li>403 Forbidden — 본인 소유 아님 (REQ-DP-003-4)</li>
 *   <li>404 Not Found — 레이아웃 미존재</li>
 *   <li>409 Conflict — 낙관적 잠금 충돌 (REQ-DP-003-5)</li>
 * </ul>
 */
// @MX:ANCHOR: [AUTO] DashboardLayoutPositionsController — REQ-DP-003 의 유일한 진입점
// @MX:REASON: DnD 결과 영속화의 단일 채널 + 소유권/겹침/낙관락 invariant 통과 필수 (fan_in ≥ 3: Vue store + IT + E2E)
// @MX:SPEC: SPEC-CMS-DASHBOARD-PERSONALIZE-001 §7 REQ-DP-003
@RestController
@RequestMapping("/api/v1/dashboard/layouts")
@RequiredArgsConstructor
public class DashboardLayoutPositionsController {

    private final LayoutPositionService service;

    /** REQ-DP-003-2 / AC-DP-003-1: 위젯 위치 일괄 갱신. */
    @PatchMapping("/{id}/positions")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> patchPositions(
            @AuthenticationPrincipal(expression = "userId") Long userId,
            @PathVariable Long id,
            @Valid @RequestBody PositionPatchRequest req) {
        service.patchPositions(id, userId, req);
        return ResponseEntity.noContent().build();
    }
}
