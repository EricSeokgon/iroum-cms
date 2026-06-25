package kr.co.ircp.cms.domain.approval.repository;

import kr.co.ircp.cms.domain.auth.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;

/**
 * 가입 승인 대기열 조회·상태 전환 MyBatis 매퍼.
 *
 * <p>SPEC-CMS-USER-APPROVAL-001 REQ-UA-007/008/010/011/021.
 */
// @MX:ANCHOR: [AUTO] UserApprovalMapper — 승인 대기열 조회·승인/거절 상태 전환 DB 접근 계층
// @MX:REASON: UserApprovalServiceImpl 의 목록/단건/일괄 메서드에서 다중 참조 (fan_in >= 3)
// @MX:SPEC: SPEC-CMS-USER-APPROVAL-001#REQ-UA-007
@Mapper
public interface UserApprovalMapper {

    /**
     * PENDING_APPROVAL 사용자 목록 조회 (가입일시 오름차순, 검색/페이지).
     *
     * <p>email 평문 컬럼은 V26 에서 제거되었으므로 암호화 컬럼을 함께 조회하고
     * 서비스 레이어에서 복호화하여 {@code UserApprovalSummary.email} 을 채운다.
     *
     * @param offset  시작 오프셋
     * @param limit   페이지 크기
     * @param keyword 이름/이메일(=username) 부분 검색어 (null 이면 전체)
     */
    List<User> selectPendingApprovals(
            @Param("offset") int offset,
            @Param("limit") int limit,
            @Param("keyword") String keyword);

    /**
     * PENDING_APPROVAL 사용자 총 개수 (검색 조건 동일).
     */
    long countPendingApprovals(@Param("keyword") String keyword);

    /**
     * 단건 승인 대기 사용자 상세 조회. PENDING_APPROVAL 이 아니면 null.
     */
    User selectPendingById(@Param("userId") long userId);

    /**
     * 승인 상태 전환 (REQ-UA-010/011/021).
     *
     * <p>PENDING_APPROVAL 인 행만 갱신한다. 갱신 행이 0 이면 호출 측이 409 처리.
     *
     * @param userId         대상 사용자
     * @param newStatus      전환 상태 ('ACTIVE' | 'INACTIVE')
     * @param rejectionReason 거절 사유 (승인 시 null)
     * @param changedBy      처리한 관리자 ID
     * @param changedAt      처리 시각
     * @return 갱신된 행 수 (0 = 대기 상태 아님)
     */
    int updateApprovalStatus(
            @Param("userId") long userId,
            @Param("newStatus") String newStatus,
            @Param("rejectionReason") String rejectionReason,
            @Param("changedBy") long changedBy,
            @Param("changedAt") Instant changedAt);

    /**
     * 리마인더 대상 조회 — PENDING_APPROVAL 이면서 N일 경과 + 미발송(reminder_sent_at IS NULL).
     *
     * <p>SPEC-CMS-USER-APPROVAL-002 REQ-UA2-003 — created_at &lt;= NOW() - thresholdDays.
     *
     * @param threshold 임계 시각 (NOW() - reminderDays). 이보다 created_at 이 이르면 대상.
     */
    List<User> selectReminderTargets(@Param("threshold") Instant threshold);

    /**
     * 리마인더 발송 완료 표시 — reminder_sent_at 을 멱등하게 기록한다.
     *
     * <p>SPEC-CMS-USER-APPROVAL-002 REQ-UA2-003 — reminder_sent_at IS NULL 인 행만 갱신하여 중복 미발송.
     *
     * @return 갱신 행 수 (0 = 이미 발송됨)
     */
    int markReminderSent(@Param("userId") long userId, @Param("sentAt") Instant sentAt);

    /**
     * 자동 거절 대상 조회 — PENDING_APPROVAL 이면서 maxWaitDays 초과.
     *
     * <p>SPEC-CMS-USER-APPROVAL-002 REQ-UA2-004 — created_at &lt; NOW() - maxWaitDays.
     *
     * @param threshold 임계 시각 (NOW() - maxWaitDays). 이보다 created_at 이 이르면 대상.
     */
    List<User> selectAutoRejectTargets(@Param("threshold") Instant threshold);

    /**
     * 자동 거절 처리 — PENDING_APPROVAL 행을 INACTIVE 로 전환한다(시스템 처리).
     *
     * <p>SPEC-CMS-USER-APPROVAL-002 REQ-UA2-004 — approval_changed_by 를 NULL(시스템)로 기록한다.
     * APPROVAL-001 의 {@link #updateApprovalStatus} 는 changedBy 가 primitive long 이라 NULL 불가하므로
     * 자동 거절 전용 갱신을 분리한다.
     *
     * @return 갱신 행 수 (0 = 이미 처리되었거나 대기 상태 아님)
     */
    int autoReject(
            @Param("userId") long userId,
            @Param("rejectionReason") String rejectionReason,
            @Param("changedAt") Instant changedAt);
}
