package kr.co.ircp.cms.domain.policy.dispatch.repository;

import kr.co.ircp.cms.domain.policy.dispatch.entity.NotificationDispatchSchedule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * 발송 예약 매퍼.
 * REQ-POLICY-003
 */
@Mapper
public interface NotificationDispatchScheduleMapper {

    Optional<NotificationDispatchSchedule> findById(@Param("id") Long id);

    List<NotificationDispatchSchedule> findFiltered(
            @Param("status") String status,
            @Param("policyId") Long policyId,
            @Param("offset") int offset,
            @Param("size") int size
    );

    long countFiltered(
            @Param("status") String status,
            @Param("policyId") Long policyId
    );

    void insert(NotificationDispatchSchedule schedule);

    int updateStatus(
            @Param("id") Long id,
            @Param("status") String status
    );
}
