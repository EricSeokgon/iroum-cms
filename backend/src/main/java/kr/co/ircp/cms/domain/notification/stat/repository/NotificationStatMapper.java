package kr.co.ircp.cms.domain.notification.stat.repository;

import java.time.LocalDate;
import java.util.List;
import kr.co.ircp.cms.domain.notification.stat.entity.NotificationStatRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 알림 발송 통계 집계 매퍼.
 *
 * <p>SPEC-CMS-NOTIFICATION-STAT-001 REQ-NS-001~005 — user_notification_inbox(V35) 단일 모수.
 */
// @MX:ANCHOR: [AUTO] NotificationStatMapper — 통계 집계 쿼리 진입점 (REQ-NS-001~005)
// @MX:REASON: user_notification_inbox 단일 진실 원천 집계 계약. admin_notification 혼용 금지(SPEC §1.1).
@Mapper
public interface NotificationStatMapper {

    /** REQ-NS-001 — 3구간 요약 통계 (today/7d/30d). */
    List<NotificationStatRow> findSummary();

    /** REQ-NS-002 — 카테고리별 통계 (구간 필터). */
    List<NotificationStatRow> findByCategory(
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    /** REQ-NS-003 — 일별 추이 (구간 필터, 최대 90일은 서비스단에서 캡). */
    List<NotificationStatRow> findDailyTrend(
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    /** REQ-NS-004 — 오류/미발송 목록 (페이지네이션). */
    List<NotificationStatRow> findErrors(
            @Param("offset") int offset,
            @Param("limit") int limit);

    /** REQ-NS-004 — 전체 오류 건수. */
    long countErrors();

    /** REQ-NS-005 — 재발송 상태 정정. */
    int updateDeliveryStatus(
            @Param("id") Long id,
            @Param("status") String status);
}
