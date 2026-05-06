package kr.co.ircp.cms.domain.dashboard.service;

import kr.co.ircp.cms.domain.dashboard.dto.SavedViewRequest;
import kr.co.ircp.cms.domain.dashboard.dto.SavedViewResponse;

import java.util.List;

/**
 * 저장된 필터/뷰 서비스 인터페이스.
 * REQ-VIZ-004
 */
public interface SavedViewService {

    SavedViewResponse create(Long ownerId, SavedViewRequest req);

    SavedViewResponse update(Long id, Long ownerId, SavedViewRequest req);

    void delete(Long id, Long ownerId);

    SavedViewResponse apply(Long id, Long requesterId);

    List<SavedViewResponse> listForUser(Long ownerId, Long dashboardId);
}
