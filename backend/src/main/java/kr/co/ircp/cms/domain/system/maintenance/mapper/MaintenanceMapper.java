package kr.co.ircp.cms.domain.system.maintenance.mapper;

import kr.co.ircp.cms.domain.system.maintenance.entity.Maintenance;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * 점검 모드 MyBatis Mapper.
 * REQ-SYSTEM-005-D
 */
@Mapper
public interface MaintenanceMapper {

    void insert(Maintenance maintenance);

    Optional<Maintenance> findById(@Param("id") Long id);

    List<Maintenance> findAll();

    /** 현재 활성 점검 (status='ACTIVE', now between start_at and end_at) */
    Optional<Maintenance> findActive();

    void updateStatus(@Param("id") Long id, @Param("status") String status);

    void update(Maintenance maintenance);

    /** SCHEDULED 중 end_at이 현재보다 이전인 레코드 COMPLETED 처리 */
    int completeExpired();
}
