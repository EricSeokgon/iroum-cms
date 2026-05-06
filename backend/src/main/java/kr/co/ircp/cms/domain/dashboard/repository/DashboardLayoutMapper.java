package kr.co.ircp.cms.domain.dashboard.repository;

import kr.co.ircp.cms.domain.dashboard.entity.DashboardLayout;
import kr.co.ircp.cms.domain.dashboard.entity.DashboardLayoutWidget;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * dashboard_layout / dashboard_layout_widget MyBatis 매퍼.
 * REQ-VIZ-002
 */
@Mapper
public interface DashboardLayoutMapper {

    void insertLayout(DashboardLayout layout);

    int updateLayout(DashboardLayout layout);

    int deleteLayout(@Param("id") Long id);

    Optional<DashboardLayout> findById(@Param("id") Long id);

    List<DashboardLayout> findByOwnerOrShared(
            @Param("ownerId") Long ownerId,
            @Param("roleCodes") List<String> roleCodes
    );

    /** REQ-VIZ-002-D-4: 단일 owner 의 기존 default 해제. */
    int clearDefaultForOwner(@Param("ownerId") Long ownerId);

    int setDefault(@Param("id") Long id, @Param("isDefault") boolean isDefault);

    // ── widgets ─────────────────────────────────────────────
    void insertWidget(DashboardLayoutWidget mapping);

    int deleteWidgetsByLayoutId(@Param("layoutId") Long layoutId);

    List<DashboardLayoutWidget> findWidgetsByLayoutId(@Param("layoutId") Long layoutId);
}
