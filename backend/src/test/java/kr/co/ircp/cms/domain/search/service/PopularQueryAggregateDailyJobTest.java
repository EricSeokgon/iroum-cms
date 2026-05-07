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
 * PopularQueryAggregateDailyJob TDD 테스트.
 *
 * <p>SPEC-CMS-010 REQ-SEARCH-006/007: 전일자 search_log 집계 → search_popular_cache UPSERT.
 * locale별 rank 정규화 (1..N) 및 BatchExecutionLog 라이프사이클 검증.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PopularQueryAggregateDailyJob TDD 테스트 (REQ-SEARCH-006/007)")
class PopularQueryAggregateDailyJobTest {

    @Mock private SearchLogMapper searchLogMapper;
    @Mock private SearchPopularCacheMapper popularCacheMapper;
    @Mock private BatchExecutionLogService batchLog;

    private PopularQueryAggregateDailyJob job;

    @BeforeEach
    void setUp() {
        job = new PopularQueryAggregateDailyJob(searchLogMapper, popularCacheMapper, batchLog);
    }

    /** 헬퍼: aggregateDaily가 반환하는 Map row 생성 */
    private Map<String, Object> aggRow(String locale, String query, long count) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("locale", locale);
        row.put("normalizedQuery", query);
        row.put("searchCount", count);
        return row;
    }

    // ─── A. run(LocalDate) 핵심 로직 (REQ-SEARCH-007) ───────────────────────

    @Test
    @DisplayName("run — 집계 결과가 빈 리스트면 0 반환 + upsert 호출 안 됨")
    void run_emptyAggregation_returnsZero() {
        LocalDate target = LocalDate.of(2026, 5, 1);
        when(searchLogMapper.aggregateDaily(eq(target), eq(target), any()))
                .thenReturn(List.of());

        int processed = job.run(target);

        assertThat(processed).isZero();
        verify(popularCacheMapper, never()).upsert(any(SearchPopularCache.class));
    }

    @Test
    @DisplayName("run — 집계 결과가 null이면 0 반환 + upsert 호출 안 됨")
    void run_nullAggregation_returnsZero() {
        LocalDate target = LocalDate.of(2026, 5, 1);
        when(searchLogMapper.aggregateDaily(eq(target), eq(target), any()))
                .thenReturn(null);

        int processed = job.run(target);

        assertThat(processed).isZero();
        verify(popularCacheMapper, never()).upsert(any(SearchPopularCache.class));
    }

    @Test
    @DisplayName("run — 단일 locale 5건이면 rank 1..5 순서로 upsert 5회 호출")
    void run_singleLocale_assignsRanks1ToN() {
        LocalDate target = LocalDate.of(2026, 5, 1);
        // SQL이 이미 locale + searchCount DESC로 정렬한다고 가정
        List<Map<String, Object>> rows = List.of(
                aggRow("ko", "검색어1", 100L),
                aggRow("ko", "검색어2", 80L),
                aggRow("ko", "검색어3", 60L),
                aggRow("ko", "검색어4", 40L),
                aggRow("ko", "검색어5", 20L)
        );
        when(searchLogMapper.aggregateDaily(eq(target), eq(target), any()))
                .thenReturn(rows);

        int processed = job.run(target);

        assertThat(processed).isEqualTo(5);
        ArgumentCaptor<SearchPopularCache> captor = ArgumentCaptor.forClass(SearchPopularCache.class);
        verify(popularCacheMapper, times(5)).upsert(captor.capture());

        List<SearchPopularCache> all = captor.getAllValues();
        assertThat(all).hasSize(5);
        assertThat(all.get(0).getRank()).isEqualTo(1);
        assertThat(all.get(0).getQuery()).isEqualTo("검색어1");
        assertThat(all.get(1).getRank()).isEqualTo(2);
        assertThat(all.get(2).getRank()).isEqualTo(3);
        assertThat(all.get(3).getRank()).isEqualTo(4);
        assertThat(all.get(4).getRank()).isEqualTo(5);
    }

    @Test
    @DisplayName("run — 다중 locale 혼합 시 locale별로 rank 독립 부여 (ko=1,2,3 / en=1,2)")
    void run_multipleLocales_resetsRankPerLocale() {
        LocalDate target = LocalDate.of(2026, 5, 1);
        // 의도적으로 interleaved 순서: ko-1, ko-2, en-1, en-2, ko-3
        // run(LocalDate)는 SQL 순서대로 순회하면서 locale별 rank 누적 (HashMap merge)
        List<Map<String, Object>> rows = List.of(
                aggRow("ko", "한국어1", 100L),
                aggRow("ko", "한국어2", 80L),
                aggRow("en", "english1", 70L),
                aggRow("en", "english2", 50L),
                aggRow("ko", "한국어3", 30L)
        );
        when(searchLogMapper.aggregateDaily(eq(target), eq(target), any()))
                .thenReturn(rows);

        int processed = job.run(target);

        assertThat(processed).isEqualTo(5);
        ArgumentCaptor<SearchPopularCache> captor = ArgumentCaptor.forClass(SearchPopularCache.class);
        InOrder ord = inOrder(popularCacheMapper);
        ord.verify(popularCacheMapper, times(5)).upsert(captor.capture());

        List<SearchPopularCache> all = captor.getAllValues();
        // 호출 순서대로: ko rank=1, ko rank=2, en rank=1, en rank=2, ko rank=3
        assertThat(all.get(0).getLocale()).isEqualTo("ko");
        assertThat(all.get(0).getRank()).isEqualTo(1);
        assertThat(all.get(0).getQuery()).isEqualTo("한국어1");

        assertThat(all.get(1).getLocale()).isEqualTo("ko");
        assertThat(all.get(1).getRank()).isEqualTo(2);

        assertThat(all.get(2).getLocale()).isEqualTo("en");
        assertThat(all.get(2).getRank()).isEqualTo(1);
        assertThat(all.get(2).getQuery()).isEqualTo("english1");

        assertThat(all.get(3).getLocale()).isEqualTo("en");
        assertThat(all.get(3).getRank()).isEqualTo(2);

        assertThat(all.get(4).getLocale()).isEqualTo("ko");
        assertThat(all.get(4).getRank()).isEqualTo(3);
        assertThat(all.get(4).getQuery()).isEqualTo("한국어3");
    }

    @Test
    @DisplayName("run — locale=null이면 ko로 기본값 처리")
    void run_nullLocale_defaultsToKo() {
        LocalDate target = LocalDate.of(2026, 5, 1);
        List<Map<String, Object>> rows = List.of(
                aggRow(null, "쿼리", 50L)
        );
        when(searchLogMapper.aggregateDaily(eq(target), eq(target), any()))
                .thenReturn(rows);

        int processed = job.run(target);

        assertThat(processed).isEqualTo(1);
        ArgumentCaptor<SearchPopularCache> captor = ArgumentCaptor.forClass(SearchPopularCache.class);
        verify(popularCacheMapper).upsert(captor.capture());
        assertThat(captor.getValue().getLocale()).isEqualTo("ko");
        assertThat(captor.getValue().getRank()).isEqualTo(1);
    }

    @Test
    @DisplayName("run — SearchPopularCache 빌더 필드 검증 (periodType=DAILY, periodDate, query, count)")
    void run_buildsCacheWithCorrectFields() {
        LocalDate target = LocalDate.of(2026, 5, 1);
        List<Map<String, Object>> rows = List.of(
                aggRow("ko", "테스트쿼리", 123L)
        );
        when(searchLogMapper.aggregateDaily(eq(target), eq(target), any()))
                .thenReturn(rows);

        job.run(target);

        ArgumentCaptor<SearchPopularCache> captor = ArgumentCaptor.forClass(SearchPopularCache.class);
        verify(popularCacheMapper).upsert(captor.capture());
        SearchPopularCache cache = captor.getValue();
        assertThat(cache.getPeriodType()).isEqualTo("DAILY");
        assertThat(cache.getPeriodDate()).isEqualTo(target);
        assertThat(cache.getLocale()).isEqualTo("ko");
        assertThat(cache.getQuery()).isEqualTo("테스트쿼리");
        assertThat(cache.getSearchCount()).isEqualTo(123L);
        assertThat(cache.getRank()).isEqualTo(1);
    }

    // ─── B. scheduled() — BatchExecutionLog 통합 (REQ-SEARCH-007) ─────────

    @Test
    @DisplayName("scheduled — 성공 경로: batchLog.start → mapper 호출 → batchLog.success 순서")
    void scheduled_success_logsStartSuccess() {
        Long logId = 42L;
        when(batchLog.start(eq("PopularQueryAggregateDailyJob"), eq("SEARCH")))
                .thenReturn(logId);
        when(searchLogMapper.aggregateDaily(any(), any(), any()))
                .thenReturn(List.of(
                        aggRow("ko", "쿼리A", 30L),
                        aggRow("ko", "쿼리B", 20L)
                ));

        job.scheduled();

        InOrder ord = inOrder(batchLog, searchLogMapper, popularCacheMapper);
        ord.verify(batchLog).start("PopularQueryAggregateDailyJob", "SEARCH");
        ord.verify(searchLogMapper).aggregateDaily(any(), any(), any());
        ord.verify(popularCacheMapper, times(2)).upsert(any(SearchPopularCache.class));
        ord.verify(batchLog).success(eq(logId), eq(2));
        verify(batchLog, never()).failure(anyLong(), anyString());
    }

    @Test
    @DisplayName("scheduled — RuntimeException 시 batchLog.failure 호출 후 재던짐")
    void scheduled_runtimeException_logsFailureAndRethrows() {
        Long logId = 99L;
        when(batchLog.start(anyString(), anyString())).thenReturn(logId);
        RuntimeException boom = new RuntimeException("DB 장애");
        when(searchLogMapper.aggregateDaily(any(), any(), any())).thenThrow(boom);

        assertThatThrownBy(() -> job.scheduled())
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("DB 장애");

        InOrder ord = inOrder(batchLog);
        ord.verify(batchLog).start("PopularQueryAggregateDailyJob", "SEARCH");
        ord.verify(batchLog).failure(eq(logId), eq("DB 장애"));
        verify(batchLog, never()).success(anyLong(), anyInt());
    }
}
