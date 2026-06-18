package kr.co.ircp.cms.domain.policy.dispatch.repository;

import kr.co.ircp.cms.domain.policy.dispatch.dto.NotificationDispatchTargetWithUser;
import kr.co.ircp.cms.domain.policy.dispatch.entity.NotificationDispatchTarget;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 발송 대상 매퍼.
 * REQ-POLICY-003-D-2: idempotency_key UNIQUE
 */
@Mapper
public interface NotificationDispatchTargetMapper {

    void insert(NotificationDispatchTarget target);

    List<NotificationDispatchTarget> findByScheduleId(@Param("scheduleId") Long scheduleId);

    int updateStatus(
            @Param("id") Long id,
            @Param("status") String status,
            @Param("failedReason") String failedReason
    );

    /**
     * 발송 대기 대상 + 수신자 암호화 이메일 조인 조회 (SPEC-CMS-NOTI-EXT-001).
     * 채널 실행기가 대상별 이메일을 복호화하기 위해 사용한다.
     */
    List<NotificationDispatchTargetWithUser> findPendingTargetsWithEmail(
            @Param("scheduleId") Long scheduleId);
}
