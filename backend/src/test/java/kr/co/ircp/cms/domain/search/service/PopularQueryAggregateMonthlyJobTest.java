package kr.co.ircp.cms.domain.search.service;

import kr.co.ircp.cms.domain.governance.service.BatchExecutionLogService;
import kr.co.ircp.cms.domain.search.entity.SearchPopularCache;
import kr.co.ircp.cms.domain.search.repository.SearchLogMapper;
import kr.co.ircp.cms.domain.search.repository.SearchPopularCacheMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PopularQueryAggregateMonthlyJob TDD 테스트.
 *
 * <p>SPEC-CMS-010 REQ-SEARCH-006/007: 전월 search_log → search_popular_cache(MONTHLY).
 * locale별로 query 합산 후 rank 부여하는 로직과 BatchExecutionLog 라이프사이클 검증.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PopularQueryAggregateMonthlyJob TDD 테스트 (REQ-SEARCH-006/007)")
class PopularQueryAggregateMonthlyJobTest {

    @Mock private SearchLogMapper searchLogMapper;
    @Mock private SearchPopularCacheMapper popularCacheMapper;
    @Mock private BatchExecutionLogService batchLog;

    private PopularQueryAggregateMonthlyJob job;

    @BeforeEach
    void setUp() {
        job = new PopularQueryAggregateMonthlyJob(searchLogMapper, popularCacheMapper, batchLog);
    }

    private Map<String, Object> aggRow(String locale, String query, long count) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("locale", locale);
        row.put("normalizedQuery", query);
        row.put("searchCount", count);
        return row;
    }

    @Test
    @DisplayName("run() — 전월 1일~말일 범위로 aggregateDaily 호출")
    void run_default_callsRangeWithPreviousMonth() {
        // 프로덕션과 동일한 로직으로 기대값 산출 (timezone-stable)
        YearMonth previous = YearMonth.now(ZoneId.of("Asia/Seoul")).minusMonths(1);
        LocalDate expectedStart = previous.atDay(1);
        LocalDate expectedEnd = previous.atEndOfMonth();

        when(searchLogMapper.aggregateDaily(eq(expectedStart), eq(expectedEnd), any()))
                .thenReturn(List.of());

        int processed = job.run();

        assertThat(processed).isZero();
        verify(searchLogMapper).aggregateDaily(eq(expectedStart), eq(expectedEnd), any());
    }

    @Test
    @DisplayName("run(범위) — locale별 query 합산 후 rank 1,2,3 부여")
    void run_validRange_assignsRanks() {
        LocalDate monthStart = LocalDate.of(2026, 4, 1);
        LocalDate monthEnd = LocalDate.of(2026, 4, 30);

        // 같은 locale 내 query별 합산 후 정렬되어 rank 부여
        List<Map<String, Object>> rows = List.of(
                aggRow("ko", "쿼리X", 500L),  // 합계 500 → 1위
                aggRow("ko", "쿼리Y", 300L),  // 합계 300
                aggRow("ko", "쿼리Z", 100L),  // 합계 100 → 3위
                aggRow("ko", "쿼리Y", 50L)    // 쿼리Y 추가 (300+50=350) → 2위
        );
        when(searchLogMapper.aggregateDaily(eq(monthStart), eq(monthEnd), any()))
                .thenReturn(rows);

        int processed = job.run(monthStart, monthEnd);

        assertThat(processed).isEqualTo(3);
        ArgumentCaptor<SearchPopularCache> captor = ArgumentCaptor.forClass(SearchPopularCache.class);
        verify(popularCacheMapper, times(3)).upsert(captor.capture());

        List<SearchPopularCache> all = captor.getAllValues();
        // 정렬: 쿼리X(500) > 쿼리Y(350) > 쿼리Z(100)
        assertThat(all.get(0).getRank()).isEqualTo(1);
        assertThat(all.get(0).getQuery()).isEqualTo("쿼리X");
        assertThat(all.get(0).getSearchCount()).isEqualTo(500L);

        assertThat(all.get(1).getRank()).isEqualTo(2);
        assertThat(all.get(1).getQuery()).isEqualTo("쿼리Y");
        assertThat(all.get(1).getSearchCount()).isEqualTo(350L);

        assertThat(all.get(2).getRank()).isEqualTo(3);
        assertThat(all.get(2).getQuery()).isEqualTo("쿼리Z");
        assertThat(all.get(2).getSearchCount()).isEqualTo(100L);
    }

    @Test
    @DisplayName("run(범위) — 빈 월 결과면 0 반환 + upsert 호출 안 됨")
    void run_emptyMonth_returnsZero() {
        LocalDate monthStart = LocalDate.of(2026, 4, 1);
        LocalDate monthEnd = LocalDate.of(2026, 4, 30);
        when(searchLogMapper.aggregateDaily(eq(monthStart), eq(monthEnd), any()))
                .thenReturn(null);

        int processed = job.run(monthStart, monthEnd);

        assertThat(processed).isZero();
        verify(popularCacheMapper, never()).upsert(any(SearchPopularCache.class));
    }

    @Test
    @DisplayName("run(범위) — 캐시 빌더 필드 검증 (periodType=MONTHLY, periodDate=monthStart)")
    void run_buildsCacheWithMonthlyType() {
        LocalDate monthStart = LocalDate.of(2026, 4, 1);
        LocalDate monthEnd = LocalDate.of(2026, 4, 30);
        when(searchLogMapper.aggregateDaily(eq(monthStart), eq(monthEnd), any()))
                .thenReturn(List.of(aggRow("ko", "월간쿼리", 999L)));

        job.run(monthStart, monthEnd);

        ArgumentCaptor<SearchPopularCache> captor = ArgumentCaptor.forClass(SearchPopularCache.class);
        verify(popularCacheMapper).upsert(captor.capture());
        SearchPopularCache cache = captor.getValue();
        assertThat(cache.getPeriodType()).isEqualTo("MONTHLY");
        assertThat(cache.getPeriodDate()).isEqualTo(monthStart);
        assertThat(cache.getLocale()).isEqualTo("ko");
        assertThat(cache.getQuery()).isEqualTo("월간쿼리");
        assertThat(cache.getSearchCount()).isEqualTo(999L);
        assertThat(cache.getRank()).isEqualTo(1);
    }

    @Test
    @DisplayName("scheduled — RuntimeException 시 batchLog.failure 호출 후 재던짐")
    void scheduled_failure_logsFailureAndRethrows() {
        Long logId = 21L;
        when(batchLog.start(eq("PopularQueryAggregateMonthlyJob"), eq("SEARCH")))
                .thenReturn(logId);
        RuntimeException boom = new RuntimeException("월간 집계 실패");
        when(searchLogMapper.aggregateDaily(any(), any(), any())).thenThrow(boom);

        assertThatThrownBy(() -> job.scheduled())
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("월간 집계 실패");

        InOrder ord = inOrder(batchLog);
        ord.verify(batchLog).start("PopularQueryAggregateMonthlyJob", "SEARCH");
        ord.verify(batchLog).failure(eq(logId), eq("월간 집계 실패"));
        verify(batchLog, never()).success(anyLong(), anyInt());
    }
}
