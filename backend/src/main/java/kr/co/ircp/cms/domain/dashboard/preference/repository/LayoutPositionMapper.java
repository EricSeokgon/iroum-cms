package kr.co.ircp.cms.domain.dashboard.preference.repository;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * SPEC-CMS-DASHBOARD-PERSONALIZE-001 §7 REQ-DP-003-2: dashboard_layout_widget.position 부분 갱신.
 *
 * <p>SPEC-CMS-008 의 {@code DashboardLayoutMapper} 를 수정하지 않기 위해 별도 매퍼로 격리한다.
 * 본 매퍼는 오로지 position JSON 컬럼만 갱신하며, sort_order 또는 config_override 등 다른 컬럼은
 * 손대지 않는다.
 *
 * <p>레이아웃 owner 검증은 서비스 계층에서 {@code DashboardLayoutMapper.findById} 로 수행한다.
 */
@Mapper
public interface LayoutPositionMapper {

    /**
     * 단건 위젯 인스턴스의 position JSONB 컬럼을 갱신한다.
     *
     * @param layoutId   레이아웃 ID
     * @param instanceId 위젯 인스턴스 ID
     * @param positionJson "{x,y,w,h}" 형식의 JSON 문자열
     * @return 갱신된 행 수 (정상 1, 미존재 0)
     */
    int updatePosition(
            @Param("layoutId") Long layoutId,
            @Param("instanceId") String instanceId,
            @Param("positionJson") String positionJson
    );

    /**
     * 레이아웃의 updated_at 을 갱신하여 다중 세션 충돌 감지의 기준으로 사용한다.
     */
    int touchLayoutUpdatedAt(@Param("layoutId") Long layoutId);
}
