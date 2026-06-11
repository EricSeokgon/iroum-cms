package kr.co.ircp.cms.domain.notification.stat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.dashboard.repository.KpiValueMapper;
import kr.co.ircp.cms.domain.notification.stat.dto.CategoryStat;
import kr.co.ircp.cms.domain.notification.stat.dto.DailyTrendPoint;
import kr.co.ircp.cms.domain.notification.stat.dto.FailedNotificationDto;
import kr.co.ircp.cms.domain.notification.stat.dto.NotificationStatSummary;
import kr.co.ircp.cms.domain.notification.stat.entity.NotificationStatRow;
import kr.co.ircp.cms.domain.notification.stat.repository.NotificationStatMapper;
import kr.co.ircp.cms.domain.notification.stat.service.NotificationStatService;
import kr.co.ircp.cms.domain.notification.stat.service.NotificationStatServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;

/**
 * NotificationStatService 단위 테스트.
 *
 * <p>SPEC-CMS-NOTIFICATION-STAT-001 REQ-NS-001~006, 008 — 발송 통계 집계·구간 안전성·KPI graceful degradation.
 */
@DisplayName("NotificationStatService (REQ-NS-001~006, 008)")
class NotificationStatServiceTest {

    private NotificationStatMapper statMapper;
    private KpiValueMapper kpiValueMapper;
    private NotificationStatService service;

    @BeforeEach
    void setUp() {
        statMapper = mock(NotificationStatMapper.class);
        kpiValueMapper = mock(KpiValueMapper.class);
        service = new NotificationStatServiceImpl(statMapper, kpiValueMapper);
    }

    private NotificationStatRow summaryRow(String period, long dispatched, long readCount,
                                           long unread, long errors, String readRate) {
        NotificationStatRow row = new NotificationStatRow();
        row.setPeriod(period);
        row.setDispatched(dispatched);
        row.setReadCount(readCount);
        row.setUnreadCount(unread);
        row.setErrorCount(errors);
        row.setReadRate(new BigDecimal(readRate));
        return row;
    }

    @Test
    @DisplayName("REQ-NS-001 — 요약 통계는 today/7d/30d 3구간을 반환한다")
    void getSummary_returnsSummaryWithThreePeriods() {
        when(statMapper.findSummary()).thenReturn(List.of(
                summaryRow("today", 10, 4, 6, 1, "40.00"),
                summaryRow("7d", 70, 35, 35, 3, "50.00"),
                summaryRow("30d", 300, 240, 60, 9, "80.00")));

        NotificationStatSummary summary = service.getSummary();

        assertThat(summary.todayDispatched()).isEqualTo(10);
        assertThat(summary.todayReadRate()).isEqualByComparingTo("40.00");
        assertThat(summary.todayUnread()).isEqualTo(6);
        assertThat(summary.todayErrors()).isEqualTo(1);
        assertThat(summary.sevenDayDispatched()).isEqualTo(70);
        assertThat(summary.sevenDayReadRate()).isEqualByComparingTo("50.00");
        assertThat(summary.thirtyDayDispatched()).isEqualTo(300);
        assertThat(summary.thirtyDayErrors()).isEqualTo(9);
    }

    @Test
    @DisplayName("REQ-NS-001 — 특정 구간 데이터가 없으면 0/0.00 으로 채운다")
    void getSummary_fillsMissingPeriodsWithZero() {
        when(statMapper.findSummary()).thenReturn(List.of(
                summaryRow("today", 5, 5, 0, 0, "100.00")));

        NotificationStatSummary summary = service.getSummary();

        assertThat(summary.todayDispatched()).isEqualTo(5);
        assertThat(summary.sevenDayDispatched()).isEqualTo(0);
        assertThat(summary.sevenDayReadRate()).isEqualByComparingTo("0.00");
        assertThat(summary.thirtyDayDispatched()).isEqualTo(0);
    }

    @Test
    @DisplayName("REQ-NS-008 — 일별 추이 구간이 90일을 넘으면 90일로 캡한다")
    void getDailyTrend_capAt90Days() {
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 12, 31); // 364일 차이
        when(statMapper.findDailyTrend(any(), any())).thenReturn(List.of());

        service.getDailyTrend(from, to);

        ArgumentCaptor<LocalDate> fromCaptor = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> toCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(statMapper).findDailyTrend(fromCaptor.capture(), toCaptor.capture());

        long days = ChronoUnit.DAYS.between(fromCaptor.getValue(), toCaptor.getValue());
        // 90일 캡: to - from 의 일수 차이가 89 (90개 일자 = 89 간격) 이하여야 한다
        assertThat(days).isLessThanOrEqualTo(89);
        assertThat(toCaptor.getValue()).isEqualTo(to); // to 고정, from 을 당겨 캡
    }

    @Test
    @DisplayName("REQ-NS-003 — 일별 추이는 매퍼 결과를 시계열 포인트로 매핑한다")
    void getDailyTrend_mapsRowsToPoints() {
        NotificationStatRow row = new NotificationStatRow();
        row.setStatDate("2026-06-01");
        row.setDispatched(12);
        row.setReadCount(8);
        when(statMapper.findDailyTrend(any(), any())).thenReturn(List.of(row));

        List<DailyTrendPoint> points = service.getDailyTrend(
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30));

        assertThat(points).hasSize(1);
        assertThat(points.get(0).date()).isEqualTo("2026-06-01");
        assertThat(points.get(0).dispatched()).isEqualTo(12);
        assertThat(points.get(0).readCount()).isEqualTo(8);
    }

    @Test
    @DisplayName("REQ-NS-002 — 카테고리 통계는 from/to 가 null 이면 최근 30일로 기본 설정한다")
    void getByCategory_defaultsToLast30Days() {
        when(statMapper.findByCategory(any(), any())).thenReturn(List.of());

        service.getByCategory(null, null);

        ArgumentCaptor<LocalDate> fromCaptor = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> toCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(statMapper).findByCategory(fromCaptor.capture(), toCaptor.capture());

        long days = ChronoUnit.DAYS.between(fromCaptor.getValue(), toCaptor.getValue());
        assertThat(days).isEqualTo(30);
        assertThat(toCaptor.getValue()).isEqualTo(LocalDate.now());
    }

    @Test
    @DisplayName("REQ-NS-002 — 카테고리 통계는 매퍼 결과를 DTO 로 매핑한다")
    void getByCategory_mapsRowsToDto() {
        NotificationStatRow row = new NotificationStatRow();
        row.setType("QNA_ANSWERED");
        row.setDispatched(50);
        row.setReadCount(40);
        when(statMapper.findByCategory(any(), any())).thenReturn(List.of(row));

        List<CategoryStat> stats = service.getByCategory(
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30));

        assertThat(stats).hasSize(1);
        assertThat(stats.get(0).type()).isEqualTo("QNA_ANSWERED");
        assertThat(stats.get(0).dispatched()).isEqualTo(50);
        assertThat(stats.get(0).readCount()).isEqualTo(40);
    }

    @Test
    @DisplayName("REQ-NS-004 — 오류 목록은 page/size 를 offset/limit 로 변환하고 페이지 응답을 만든다")
    void getErrors_returnsPaginatedList() {
        NotificationStatRow row = new NotificationStatRow();
        row.setId(7L);
        row.setUserId(99L);
        row.setType("SYSTEM");
        row.setTitle("발송 실패 알림");
        row.setDeliveryStatus("FAILED");
        when(statMapper.findErrors(eq(40), eq(20))).thenReturn(List.of(row));
        when(statMapper.countErrors()).thenReturn(41L);

        PageResponse<FailedNotificationDto> page = service.getErrors(2, 20);

        verify(statMapper).findErrors(40, 20); // page=2, size=20 → offset=40
        assertThat(page.content()).hasSize(1);
        assertThat(page.content().get(0).id()).isEqualTo(7L);
        assertThat(page.content().get(0).deliveryStatus()).isEqualTo("FAILED");
        assertThat(page.totalElements()).isEqualTo(41L);
        assertThat(page.page()).isEqualTo(2);
        assertThat(page.totalPages()).isEqualTo(3);
    }

    @Test
    @DisplayName("REQ-NS-005 — 재발송은 delivery_status 를 SENT 로 갱신한다")
    void resend_callsUpdateDeliveryStatus() {
        when(statMapper.updateDeliveryStatus(7L, "SENT")).thenReturn(1);

        service.resend(7L);

        verify(statMapper).updateDeliveryStatus(7L, "SENT");
    }

    @Test
    @DisplayName("REQ-NS-006 — KPI 테이블 부재 시 예외를 전파하지 않는다 (graceful degradation)")
    void refreshKpiFeed_gracefulWhenKpiTableMissing() {
        when(statMapper.findSummary()).thenReturn(List.of(
                summaryRow("30d", 300, 240, 60, 9, "80.00")));
        doThrow(new DataAccessResourceFailureException("kpi_value 없음"))
                .when(kpiValueMapper).upsertNotificationKpi(any(), anyString(), any());

        assertThatCode(() -> service.refreshKpiFeed()).doesNotThrowAnyException();
    }
}
