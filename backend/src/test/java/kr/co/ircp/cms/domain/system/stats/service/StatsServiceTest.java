package kr.co.ircp.cms.domain.system.stats.service;

import kr.co.ircp.cms.domain.system.stats.dto.TopPageResponse;
import kr.co.ircp.cms.domain.system.stats.dto.TrendItemResponse;
import kr.co.ircp.cms.domain.system.stats.mapper.AccessStatDailyMapper;
import kr.co.ircp.cms.domain.system.stats.mapper.AccessStatMonthlyMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * StatsService 단위 테스트.
 * REQ-SYSTEM-002-D / REQ-SYSTEM-003-D — 일·월 집계 + 추이/Top 페이지
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("StatsService 단위 테스트")
class StatsServiceTest {

    @Mock private AccessStatDailyMapper dailyMapper;
    @Mock private AccessStatMonthlyMapper monthlyMapper;

    private StatsServiceImpl statsService;

    @BeforeEach
    void setUp() {
        statsService = new StatsServiceImpl(dailyMapper, monthlyMapper);
    }

    // ──────────────────────────────────────────────
    // aggregateDaily
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("aggregateDaily — dailyMapper.upsertForDate 호출")
    void aggregateDaily_callsDailyMapper() {
        // given
        LocalDate target = LocalDate.of(2026, 5, 7);

        // when
        statsService.aggregateDaily(target, 1L);

        // then
        verify(dailyMapper).upsertForDate(target, 1L);
    }

    // ──────────────────────────────────────────────
    // aggregateMonthly
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("aggregateMonthly — monthlyMapper.upsertForMonth 호출")
    void aggregateMonthly_callsMonthlyMapper() {
        // when
        statsService.aggregateMonthly("2026-05", 1L);

        // then
        verify(monthlyMapper).upsertForMonth("2026-05", 1L);
    }

    // ──────────────────────────────────────────────
    // recompute (날짜 범위 + 월별 갱신)
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("recompute — 동일 날짜이면 daily 1회 + monthly 1회 호출")
    void recompute_singleDay_oneDailyOneMonthly() {
        // given
        LocalDate single = LocalDate.of(2026, 5, 7);

        // when
        statsService.recompute(single, single, 1L);

        // then
        verify(dailyMapper, times(1)).upsertForDate(single, 1L);
        verify(monthlyMapper, times(1)).upsertForMonth("2026-05", 1L);
    }

    @Test
    @DisplayName("recompute — 같은 달의 3일 범위면 daily 3회 + monthly 1회 호출")
    void recompute_sameMonthRange_threeDaysOneMonth() {
        // given
        LocalDate from = LocalDate.of(2026, 5, 1);
        LocalDate to = LocalDate.of(2026, 5, 3);

        // when
        statsService.recompute(from, to, 2L);

        // then
        verify(dailyMapper, times(3)).upsertForDate(any(LocalDate.class), eq(2L));
        verify(monthlyMapper, times(1)).upsertForMonth("2026-05", 2L);
    }

    @Test
    @DisplayName("recompute — 월 경계를 넘는 범위면 monthly 2회 호출 (fromMonth + toMonth)")
    void recompute_crossMonthRange_twoMonthlyCalls() {
        // given
        LocalDate from = LocalDate.of(2026, 4, 30);
        LocalDate to = LocalDate.of(2026, 5, 1);

        // when
        statsService.recompute(from, to, 3L);

        // then — daily 2회 (4/30, 5/1)
        verify(dailyMapper, times(2)).upsertForDate(any(LocalDate.class), eq(3L));
        // monthly 2회 (2026-04, 2026-05)
        verify(monthlyMapper, times(1)).upsertForMonth("2026-04", 3L);
        verify(monthlyMapper, times(1)).upsertForMonth("2026-05", 3L);
    }

    @Test
    @DisplayName("recompute — fromMonth==toMonth면 monthly 1회만 호출 (중복 방지)")
    void recompute_sameMonth_noDuplicateMonthlyCall() {
        // given
        LocalDate from = LocalDate.of(2026, 5, 10);
        LocalDate to = LocalDate.of(2026, 5, 12);

        // when
        statsService.recompute(from, to, 4L);

        // then — monthly 정확히 1회
        verify(monthlyMapper, times(1)).upsertForMonth("2026-05", 4L);
        verify(monthlyMapper, never()).upsertForMonth("2026-04", 4L);
        verify(monthlyMapper, never()).upsertForMonth("2026-06", 4L);
    }

    // ──────────────────────────────────────────────
    // getTrend30Days
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("getTrend30Days — dailyMapper.findTrend30Days 결과 반환")
    void getTrend30Days_returnsList() {
        // given
        TrendItemResponse item = new TrendItemResponse(LocalDate.of(2026, 5, 7), 100, 250, 2);
        when(dailyMapper.findTrend30Days(1L)).thenReturn(List.of(item));

        // when
        List<TrendItemResponse> result = statsService.getTrend30Days(1L);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getVisits()).isEqualTo(100);
    }

    @Test
    @DisplayName("getTrend30Days — 데이터 없으면 빈 리스트 반환")
    void getTrend30Days_empty() {
        // given
        when(dailyMapper.findTrend30Days(99L)).thenReturn(List.of());

        // when
        List<TrendItemResponse> result = statsService.getTrend30Days(99L);

        // then
        assertThat(result).isEmpty();
    }

    // ──────────────────────────────────────────────
    // getTopPages
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("getTopPages — monthlyMapper.findTopPages 결과 반환")
    void getTopPages_returnsList() {
        // given
        TopPageResponse top = new TopPageResponse("/index", 1234L, null, null, 1);
        when(monthlyMapper.findTopPages(7, 1L)).thenReturn(List.of(top));

        // when
        List<TopPageResponse> result = statsService.getTopPages(7, 1L);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPageUrl()).isEqualTo("/index");
        assertThat(result.get(0).getRank()).isEqualTo(1);
    }

    @Test
    @DisplayName("getTopPages — 30일 days 인자로도 정상 호출")
    void getTopPages_30days() {
        // given
        when(monthlyMapper.findTopPages(30, 5L)).thenReturn(List.of());

        // when
        List<TopPageResponse> result = statsService.getTopPages(30, 5L);

        // then
        assertThat(result).isEmpty();
        verify(monthlyMapper).findTopPages(30, 5L);
    }
}
