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

    /**
     * 발송 대기 배치 조회 (SPEC-CMS-NOTI-EXT-001).
     * scheduled_at 도래한 PENDING 건을 FOR UPDATE SKIP LOCKED로 잠가 멀티 인스턴스 중복 처리를 방지한다.
     */
    List<NotificationDispatchSchedule> findPendingBatch(@Param("limit") int limit);

    /** 처리 시작 마킹 (PENDING → PROCESSING). */
    void markAsDispatching(@Param("id") Long id);
}
