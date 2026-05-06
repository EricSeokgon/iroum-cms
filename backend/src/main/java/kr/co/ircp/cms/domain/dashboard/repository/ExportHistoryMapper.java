package kr.co.ircp.cms.domain.dashboard.repository;

import kr.co.ircp.cms.domain.dashboard.entity.ExportHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * export_history MyBatis 매퍼.
 * REQ-VIZ-006-D-5
 */
@Mapper
public interface ExportHistoryMapper {

    void insert(ExportHistory history);

    int update(ExportHistory history);

    int updateProgress(@Param("id") Long id, @Param("progressPct") int progressPct);

    Optional<ExportHistory> findById(@Param("id") Long id);

    List<ExportHistory> findByRequestor(
            @Param("requestorId") Long requestorId,
            @Param("status") String status
    );
}
