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

    List<LayoutResponse> listForUser(Long ownerId, List<String> roleCodes);

    void delete(Long id, Long ownerId);

    /** REQ-VIZ-002-D-5: deep-copy layout + widgets to new owner. */
    LayoutResponse clone(Long sourceId, Long newOwnerId);

    /** REQ-VIZ-002-D-4: one-default constraint. */
    void setDefault(Long id, Long ownerId);
}
