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

    /**
     * REQ-VIZ-001-D-8 A-8: DEPT_ADMIN 부서 범위 검증을 포함한 위젯 수정.
     *
     * <p>requester 가 DEPT_ADMIN 이고 위젯 작성자(createdBy)의 organization 이 requester
     * 의 organization 과 다르면 {@code WidgetDeptMismatchException} 발생.
     * SUPER_ADMIN 은 부서 검사 우회.
     */
    WidgetResponse update(Long id, WidgetRequest req, Long requesterId, List<String> requesterRoles);

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
