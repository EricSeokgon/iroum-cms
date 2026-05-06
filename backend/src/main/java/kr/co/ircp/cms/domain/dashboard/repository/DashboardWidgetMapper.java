package kr.co.ircp.cms.domain.dashboard.repository;

import kr.co.ircp.cms.domain.dashboard.entity.DashboardWidget;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * dashboard_widget MyBatis 매퍼.
 * REQ-VIZ-001
 */
@Mapper
public interface DashboardWidgetMapper {

    void insert(DashboardWidget widget);

    int update(DashboardWidget widget);

    int updateStatus(@Param("id") Long id, @Param("status") String status);

    Optional<DashboardWidget> findById(@Param("id") Long id);

    Optional<DashboardWidget> findByCode(@Param("code") String code);

    List<DashboardWidget> findAll(
            @Param("widgetType") String widgetType,
            @Param("status") String status,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    long countAll(
            @Param("widgetType") String widgetType,
            @Param("status") String status
    );
}
