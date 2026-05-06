package kr.co.ircp.cms.domain.dashboard.service;

import kr.co.ircp.cms.domain.dashboard.dto.WidgetDataResponse;
import kr.co.ircp.cms.domain.dashboard.dto.WidgetRequest;
import kr.co.ircp.cms.domain.dashboard.dto.WidgetResponse;

import java.util.List;
import java.util.Map;

/**
 * 위젯 정의 + 데이터 조회 서비스 인터페이스.
 * REQ-VIZ-001, REQ-VIZ-005
 */
public interface DashboardWidgetService {

    WidgetResponse create(WidgetRequest req, Long createdBy);

    WidgetResponse update(Long id, WidgetRequest req);

    WidgetResponse getById(Long id);

    List<WidgetResponse> list(String widgetType, String status, int page, int size);

    void delete(Long id);

    /**
     * 위젯 데이터 조회 (REQ-VIZ-005-D-1).
     * @param widgetId   대상 위젯
     * @param filters    적용 필터 (period, feature, ...)
     * @param userRoles  사용자 역할 목록 (권한 검사용 — REQ-VIZ-001-D-3)
     */
    WidgetDataResponse getData(Long widgetId, Map<String, Object> filters, List<String> userRoles);

    /** 미리보기 — 영속 저장 없음 (REQ-VIZ-001-D-5). */
    WidgetDataResponse preview(WidgetRequest req, List<String> userRoles);
}
