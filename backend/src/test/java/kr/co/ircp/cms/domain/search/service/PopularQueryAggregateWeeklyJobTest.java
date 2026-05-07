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

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
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
 * PopularQueryAggregateWeeklyJob TDD 테스트.
 *
 * <p>SPEC-CMS-010 REQ-SEARCH-006/007: 직전 한 주(월~일) search_log → search_popular_cache(WEEKLY).
 * locale별로 query 합산 후 rank 부여하는 로직 검증.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PopularQueryAggregateWeeklyJob TDD 테스트 (REQ-SEARCH-006/007)")
class PopularQueryAggregateWeeklyJobTest {

    @Mock private SearchLogMapper searchLogMapper;
    @Mock private SearchPopularCacheMapper popularCacheMapper;
    @Mock private BatchExecutionLogService batchLog;

    private PopularQueryAggregateWeeklyJob job;

    @BeforeEach
    void setUp() {
        job = new PopularQueryAggregateWeeklyJob(searchLogMapper, popularCacheMapper, batchLog);
    }

    private Map<String, Object> aggRow(String locale, String query, long count) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("locale", locale);
        row.put("normalizedQuery", query);
        row.put("searchCount", count);
        return row;
    }

    @Test
    @DisplayName("run() — 직전 주의 월요일~일요일 범위로 aggregateDaily 호출")
    void run_default_callsRangeWithPreviousWeek() {
        // 프로덕션과 동일한 로직으로 기대값 산출 (timezone-stable)
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        LocalDate expectedStart = today.minusWeeks(1)
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate expectedEnd = expectedStart.plusDays(6);

        when(searchLogMapper.aggregateDaily(eq(expectedStart), eq(expectedEnd), any()))
                .thenReturn(List.of());

        int processed = job.run();

        assertThat(processed).isZero();
        verify(searchLogMapper).aggregateDaily(eq(expectedStart), eq(expectedEnd), any());
    }

    @Test
    @DisplayName("run(범위) — 3개 row → 합산 후 rank 1,2,3 부여")
    void run_validRange_assignsRanks() {
        LocalDate weekStart = LocalDate.of(2026, 4, 27); // 월요일
        LocalDate weekEnd = LocalDate.of(2026, 5, 3);    // 일요일

        // 같은 locale 내에서 query별 합산 후 정렬되어 rank가 부여되는지 확인
        // 일자가 다른 row가 같은 query에 대해 여러 개 들어올 수 있음
        List<Map<String, Object>> rows = List.of(
                aggRow("ko", "쿼리A", 50L),  // 합계 50
                aggRow("ko", "쿼리B", 100L), // 합계 100 → 1위
                aggRow("ko", "쿼리C", 70L),  // 합계 70  → 2위
                aggRow("ko", "쿼리A", 10L)   // 쿼리A 추가 (50+10=60) → 3위
        );
        when(searchLogMapper.aggregateDaily(eq(weekStart), eq(weekEnd), any()))
                .thenReturn(rows);

        int processed = job.run(weekStart, weekEnd);

        assertThat(processed).isEqualTo(3);
        ArgumentCaptor<SearchPopularCache> captor = ArgumentCaptor.forClass(SearchPopularCache.class);
        verify(popularCacheMapper, times(3)).upsert(captor.capture());

        List<SearchPopularCache> all = captor.getAllValues();
        // 정렬: 쿼리B(100) > 쿼리C(70) > 쿼리A(60)
        assertThat(all.get(0).getRank()).isEqualTo(1);
        assertThat(all.get(0).getQuery()).isEqualTo("쿼리B");
        assertThat(all.get(0).getSearchCount()).isEqualTo(100L);

        assertThat(all.get(1).getRank()).isEqualTo(2);
        assertThat(all.get(1).getQuery()).isEqualTo("쿼리C");
        assertThat(all.get(1).getSearchCount()).isEqualTo(70L);

        assertThat(all.get(2).getRank()).isEqualTo(3);
        assertThat(all.get(2).getQuery()).isEqualTo("쿼리A");
        assertThat(all.get(2).getSearchCount()).isEqualTo(60L);
    }

    @Test
    @DisplayName("run(범위) — 빈 결과면 0 반환 + upsert 호출 안 됨")
    void run_emptyRange_returnsZero() {
        LocalDate weekStart = LocalDate.of(2026, 4, 27);
        LocalDate weekEnd = LocalDate.of(2026, 5, 3);
        when(searchLogMapper.aggregateDaily(eq(weekStart), eq(weekEnd), any()))
                .thenReturn(List.of());

        int processed = job.run(weekStart, weekEnd);

        assertThat(processed).isZero();
        verify(popularCacheMapper, never()).upsert(any(SearchPopularCache.class));
    }

    @Test
    @DisplayName("run(범위) — 캐시 빌더 필드 검증 (periodType=WEEKLY, periodDate=weekStart)")
    void run_buildsCacheWithWeeklyType() {
        LocalDate weekStart = LocalDate.of(2026, 4, 27);
        LocalDate weekEnd = LocalDate.of(2026, 5, 3);
        when(searchLogMapper.aggregateDaily(eq(weekStart), eq(weekEnd), any()))
                .thenReturn(List.of(aggRow("ko", "주간쿼리", 200L)));

        job.run(weekStart, weekEnd);

        ArgumentCaptor<SearchPopularCache> captor = ArgumentCaptor.forClass(SearchPopularCache.class);
        verify(popularCacheMapper).upsert(captor.capture());
        SearchPopularCache cache = captor.getValue();
        assertThat(cache.getPeriodType()).isEqualTo("WEEKLY");
        assertThat(cache.getPeriodDate()).isEqualTo(weekStart);
        assertThat(cache.getLocale()).isEqualTo("ko");
        assertThat(cache.getQuery()).isEqualTo("주간쿼리");
        assertThat(cache.getSearchCount()).isEqualTo(200L);
        assertThat(cache.getRank()).isEqualTo(1);
    }

    @Test
    @DisplayName("scheduled — 성공 시 batchLog.start → mapper 호출 → batchLog.success 순서")
    void scheduled_success_logsStartSuccess() {
        Long logId = 7L;
        when(batchLog.start(eq("PopularQueryAggregateWeeklyJob"), eq("SEARCH")))
                .thenReturn(logId);
        when(searchLogMapper.aggregateDaily(any(), any(), any()))
                .thenReturn(List.of(
                        aggRow("ko", "주간A", 100L),
                        aggRow("ko", "주간B", 50L)
                ));

        job.scheduled();

        InOrder ord = inOrder(batchLog, searchLogMapper, popularCacheMapper);
        ord.verify(batchLog).start("PopularQueryAggregateWeeklyJob", "SEARCH");
        ord.verify(searchLogMapper).aggregateDaily(any(), any(), any());
        ord.verify(popularCacheMapper, times(2)).upsert(any(SearchPopularCache.class));
        ord.verify(batchLog).success(eq(logId), eq(2));
        verify(batchLog, never()).failure(anyLong(), anyString());
    }

    @Test
    @DisplayName("scheduled — RuntimeException 시 batchLog.failure 호출 후 재던짐")
    void scheduled_runtimeException_logsFailureAndRethrows() {
        Long logId = 13L;
        when(batchLog.start(anyString(), anyString())).thenReturn(logId);
        RuntimeException boom = new RuntimeException("주간 집계 실패");
        when(searchLogMapper.aggregateDaily(any(), any(), any())).thenThrow(boom);

        assertThatThrownBy(() -> job.scheduled())
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("주간 집계 실패");

        InOrder ord = inOrder(batchLog);
        ord.verify(batchLog).start("PopularQueryAggregateWeeklyJob", "SEARCH");
        ord.verify(batchLog).failure(eq(logId), eq("주간 집계 실패"));
        verify(batchLog, never()).success(anyLong(), anyInt());
    }
}
