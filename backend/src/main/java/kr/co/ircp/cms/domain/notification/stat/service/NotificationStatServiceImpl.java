package kr.co.ircp.cms.domain.notification.stat.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.dashboard.repository.KpiValueMapper;
import kr.co.ircp.cms.domain.notification.stat.dto.CategoryStat;
import kr.co.ircp.cms.domain.notification.stat.dto.DailyTrendPoint;
import kr.co.ircp.cms.domain.notification.stat.dto.FailedNotificationDto;
import kr.co.ircp.cms.domain.notification.stat.dto.NotificationStatSummary;
import kr.co.ircp.cms.domain.notification.stat.entity.NotificationStatRow;
import kr.co.ircp.cms.domain.notification.stat.repository.NotificationStatMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 알림 발송 통계 서비스 구현.
 *
 * <p>SPEC-CMS-NOTIFICATION-STAT-001 REQ-NS-001~006, 008 — user_notification_inbox 단일 모수 집계,
 * 구간 안전성(90일 캡·기본 30일), KPI graceful degradation.
 */
// @MX:NOTE: [AUTO] NotificationStatServiceImpl — Controller 단일 호출자. KPI 피드는 KPI 미배포 환경에서도
//           통계 패널이 단독 동작하도록 try-catch 로 격리한다(SPEC §7.1 graceful degradation).
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationStatServiceImpl implements NotificationStatService {

    /** REQ-NS-008 — 일별 추이 구간 상한(일자 개수). */
    static final int MAX_TREND_DAYS = 90;
    /** REQ-NS-002 — 카테고리/추이 기본 조회 구간(일수). */
    static final int DEFAULT_RANGE_DAYS = 30;
    /** REQ-NS-004 — 페이지 사이즈 상한. */
    static final int MAX_PAGE_SIZE = 100;
    /** REQ-NS-004 — 기본 페이지 사이즈. */
    static final int DEFAULT_PAGE_SIZE = 20;

    /** REQ-NS-006 — 알림 건전성 KPI definition id (KPI 미배포 시 graceful no-op). */
    static final long NOTIFICATION_KPI_ID = 999L;

    private final NotificationStatMapper statMapper;
    private final KpiValueMapper kpiValueMapper;

    @Override
    public NotificationStatSummary getSummary() {
        Map<String, NotificationStatRow> byPeriod = statMapper.findSummary().stream()
                .collect(Collectors.toMap(NotificationStatRow::getPeriod, Function.identity()));

        NotificationStatRow today = byPeriod.getOrDefault("today", emptyRow());
        NotificationStatRow week = byPeriod.getOrDefault("7d", emptyRow());
        NotificationStatRow month = byPeriod.getOrDefault("30d", emptyRow());

        return new NotificationStatSummary(
                today.getDispatched(), readRate(today), today.getUnreadCount(), today.getErrorCount(),
                week.getDispatched(), readRate(week), week.getUnreadCount(), week.getErrorCount(),
                month.getDispatched(), readRate(month), month.getUnreadCount(), month.getErrorCount());
    }

    @Override
    public List<CategoryStat> getByCategory(LocalDate from, LocalDate to) {
        LocalDate effectiveTo = (to != null) ? to : LocalDate.now();
        LocalDate effectiveFrom = (from != null) ? from : effectiveTo.minusDays(DEFAULT_RANGE_DAYS);

        return statMapper.findByCategory(effectiveFrom, effectiveTo).stream()
                .map(r -> new CategoryStat(r.getType(), r.getDispatched(), r.getReadCount()))
                .toList();
    }

    @Override
    public List<DailyTrendPoint> getDailyTrend(LocalDate from, LocalDate to) {
        LocalDate effectiveTo = (to != null) ? to : LocalDate.now();
        LocalDate effectiveFrom = (from != null) ? from : effectiveTo.minusDays(DEFAULT_RANGE_DAYS);

        // REQ-NS-008 — 구간 상한 90일 캡: 90 일자(= 89 간격)를 초과하면 to 를 고정하고 from 을 당긴다.
        long span = ChronoUnit.DAYS.between(effectiveFrom, effectiveTo);
        if (span > MAX_TREND_DAYS - 1) {
            effectiveFrom = effectiveTo.minusDays(MAX_TREND_DAYS - 1L);
        }

        return statMapper.findDailyTrend(effectiveFrom, effectiveTo).stream()
                .map(r -> new DailyTrendPoint(r.getStatDate(), r.getDispatched(), r.getReadCount()))
                .toList();
    }

    @Override
    public PageResponse<FailedNotificationDto> getErrors(int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = clampSize(size);
        int offset = safePage * safeSize;

        List<FailedNotificationDto> content = statMapper.findErrors(offset, safeSize).stream()
                .map(r -> new FailedNotificationDto(
                        r.getId(), r.getUserId(), r.getType(), r.getTitle(),
                        r.getDeliveryStatus(), r.getCreatedAt()))
                .toList();
        long total = statMapper.countErrors();
        return PageResponse.of(content, safePage, safeSize, total);
    }

    @Override
    @Transactional
    public void resend(Long id) {
        statMapper.updateDeliveryStatus(id, "SENT");
    }

    @Override
    @Transactional
    public void refreshKpiFeed() {
        NotificationStatSummary summary = getSummary();

        BigDecimal readRate = summary.thirtyDayReadRate();
        BigDecimal errorRate = computeErrorRate(
                summary.thirtyDayErrors(), summary.thirtyDayDispatched());

        // REQ-NS-006 — KPI 미배포(테이블/정의 부재) 환경에서도 통계 패널이 단독 동작해야 한다.
        try {
            kpiValueMapper.upsertNotificationKpi(
                    NOTIFICATION_KPI_ID, "{\"period\":\"30d\",\"metric\":\"read_rate\"}", readRate);
            kpiValueMapper.upsertNotificationKpi(
                    NOTIFICATION_KPI_ID, "{\"period\":\"30d\",\"metric\":\"error_rate\"}", errorRate);
        } catch (DataAccessException ex) {
            log.warn("KPI 피드 갱신 건너뜀 — kpi_value 미배포 또는 정의 부재 (REQ-NS-006 graceful degradation): {}",
                    ex.getMessage());
        }
    }

    // ─── private helpers ───────────────────────────────────────────────────

    private static NotificationStatRow emptyRow() {
        NotificationStatRow row = new NotificationStatRow();
        row.setReadRate(BigDecimal.ZERO);
        return row;
    }

    private static BigDecimal readRate(NotificationStatRow row) {
        BigDecimal rate = row.getReadRate();
        return (rate != null) ? rate.setScale(2, RoundingMode.HALF_UP) : zeroRate();
    }

    private static BigDecimal computeErrorRate(long errors, long dispatched) {
        if (dispatched == 0) {
            return zeroRate();
        }
        return BigDecimal.valueOf(errors)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(dispatched), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal zeroRate() {
        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    private static int clampSize(int size) {
        if (size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }
}
