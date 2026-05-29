package kr.co.ircp.cms.domain.dashboard.preference.service;

import kr.co.ircp.cms.domain.dashboard.preference.dto.PositionPatchRequest;

/**
 * SPEC-CMS-DASHBOARD-PERSONALIZE-001 — 드래그앤드롭 결과 영속화 전용 서비스.
 *
 * <p>REQ-DP-003-2~5 의 PATCH /layouts/{id}/positions 처리 책임을 캡슐화한다.
 * SPEC-CMS-008 의 {@code DashboardLayoutServiceImpl.update} 와 분리하여 사이드 이펙트를 최소화한다.
 */
public interface LayoutPositionService {

    /**
     * REQ-DP-003-2 / 003-3 / 003-4 / 003-5: 일괄 위치 갱신.
     *
     * @throws kr.co.ircp.cms.domain.dashboard.exception.DashboardLayoutNotFoundException
     *         레이아웃 미존재
     * @throws SecurityException 본인 소유 레이아웃이 아닐 때 (REQ-DP-003-4)
     * @throws IllegalArgumentException 요청 자체로 위젯 간 겹침이 검출됐을 때 (REQ-DP-003-3)
     * @throws kr.co.ircp.cms.domain.dashboard.preference.exception.PreferenceConflictException
     *         낙관적 잠금 충돌 (REQ-DP-003-5)
     */
    void patchPositions(Long layoutId, Long ownerId, PositionPatchRequest req);
}
