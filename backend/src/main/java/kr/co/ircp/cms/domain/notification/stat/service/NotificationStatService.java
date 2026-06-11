package kr.co.ircp.cms.domain.notification.stat.service;

import java.time.LocalDate;
import java.util.List;
import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.notification.stat.dto.CategoryStat;
import kr.co.ircp.cms.domain.notification.stat.dto.DailyTrendPoint;
import kr.co.ircp.cms.domain.notification.stat.dto.FailedNotificationDto;
import kr.co.ircp.cms.domain.notification.stat.dto.NotificationStatSummary;

/**
 * 알림 발송 통계 서비스.
 *
 * <p>SPEC-CMS-NOTIFICATION-STAT-001 REQ-NS-001~006 — 발송 현황 요약·카테고리·일별 추이·
 * 오류 목록·재발송·KPI 피드.
 */
public interface NotificationStatService {

    /** REQ-NS-001 — today/7일/30일 발송 현황 요약. */
    NotificationStatSummary getSummary();

    /** REQ-NS-002 — 카테고리(type)별 통계 (from/to null 시 최근 30일). */
    List<CategoryStat> getByCategory(LocalDate from, LocalDate to);

    /** REQ-NS-003/008 — 일별 발송 추이 (최대 90일 캡, gap-fill). */
    List<DailyTrendPoint> getDailyTrend(LocalDate from, LocalDate to);

    /** REQ-NS-004 — 오류/미발송 목록 (페이지네이션). */
    PageResponse<FailedNotificationDto> getErrors(int page, int size);

    /** REQ-NS-005 — 개별 재발송 (delivery_status SENT 정정). */
    void resend(Long id);

    /** REQ-NS-006 — 알림 건전성 KPI 피드 갱신 (KPI 미배포 시 graceful no-op). */
    void refreshKpiFeed();
}
