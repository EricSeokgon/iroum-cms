package kr.co.ircp.cms.domain.dashboard.repository;

import kr.co.ircp.cms.domain.dashboard.entity.SavedView;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * saved_view MyBatis 매퍼.
 * REQ-VIZ-004
 */
@Mapper
public interface SavedViewMapper {

    void insert(SavedView view);

    int update(SavedView view);

    int delete(@Param("id") Long id);

    Optional<SavedView> findById(@Param("id") Long id);

    List<SavedView> findByOwnerAndDashboard(
            @Param("ownerId") Long ownerId,
            @Param("dashboardId") Long dashboardId
    );

    int touchLastUsedAt(@Param("id") Long id);
}
