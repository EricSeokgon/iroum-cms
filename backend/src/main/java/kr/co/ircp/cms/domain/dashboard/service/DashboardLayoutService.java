package kr.co.ircp.cms.domain.dashboard.service;

import kr.co.ircp.cms.domain.dashboard.dto.LayoutRequest;
import kr.co.ircp.cms.domain.dashboard.dto.LayoutResponse;

import java.util.List;

/**
 * 대시보드 레이아웃 서비스 인터페이스.
 * REQ-VIZ-002
 */
public interface DashboardLayoutService {

    LayoutResponse create(Long ownerId, LayoutRequest req);

    LayoutResponse update(Long id, Long ownerId, LayoutRequest req);

    LayoutResponse getById(Long id);

    /**
     * REQ-VIZ-001-D-5: 사용자 역할 기반으로 접근 불가 위젯을 묵시적으로 필터링한 레이아웃을 반환.
     *
     * <p>403 을 던지지 않고 위젯 목록에서만 제거한다. SUPER_ADMIN 은 항상 전체 위젯 확인.
     */
    LayoutResponse getByIdForUser(Long id, List<String> userRoles);

    List<LayoutResponse> listForUser(Long ownerId, List<String> roleCodes);

    void delete(Long id, Long ownerId);

    /** REQ-VIZ-002-D-5: deep-copy layout + widgets to new owner. */
    LayoutResponse clone(Long sourceId, Long newOwnerId);

    /** REQ-VIZ-002-D-4: one-default constraint. */
    void setDefault(Long id, Long ownerId);
}
