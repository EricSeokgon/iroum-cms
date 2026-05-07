package kr.co.ircp.cms.domain.search.service;

import kr.co.ircp.cms.domain.governance.service.BatchExecutionLogService;
import kr.co.ircp.cms.domain.search.entity.SearchPopularCache;
import kr.co.ircp.cms.domain.search.repository.SearchLogMapper;
import kr.co.ircp.cms.domain.search.repository.SearchPopularCacheMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 일별 인기 검색어 집계 배치.
 *
 * <p>SPEC-CMS-010 REQ-SEARCH-006/007 — 매일 04:30 KST 실행.
 * 전일자 search_log → search_popular_cache(period_type=DAILY) UPSERT.
 * locale별 rank 정규화 (1..N 내림차순).
 */
// @MX:NOTE: [AUTO] SPEC-CMS-010 일별 인기 검색어 집계 (REQ-SEARCH-007)
@Slf4j
@Component
@RequiredArgsConstructor
public class PopularQueryAggregateDailyJob {

    static final String JOB_NAME = "PopularQueryAggregateDailyJob";
    static final String JOB_GROUP = "SEARCH";

    private final SearchLogMapper searchLogMapper;
    private final SearchPopularCacheMapper popularCacheMapper;
    private final BatchExecutionLogService batchLog;

    /**
     * 전일자 인기 검색어 집계 실행.
     * @return 적재된 (locale, query) 행 수
     */
    public int run() {
        return run(LocalDate.now(ZoneId.of("Asia/Seoul")).minusDays(1));
    }

    public int run(LocalDate targetDate) {
        // 1) 일별 normalized_query 빈도 집계
        List<Map<String, Object>> rows = searchLogMapper.aggregateDaily(
                targetDate, targetDate, null
        );
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        // 2) locale별로 rank 정규화 (이미 SQL이 ORDER BY locale, "searchCount" DESC)
        Map<String, Integer> rankByLocale = new HashMap<>();
        int processed = 0;
        for (Map<String, Object> row : rows) {
            String locale = (String) row.get("locale");
            String query = (String) row.get("normalizedQuery");
            long count = ((Number) row.get("searchCount")).longValue();
            int rank = rankByLocale.merge(locale == null ? "ko" : locale, 1, Integer::sum);

            SearchPopularCache cache = SearchPopularCache.builder()
                    .periodType("DAILY")
                    .periodDate(targetDate)
                    .locale(locale == null ? "ko" : locale)
                    .query(query)
                    .searchCount(count)
                    .rank(rank)
                    .build();
            popularCacheMapper.upsert(cache);
            processed++;
        }
        return processed;
    }

    @Scheduled(cron = "${search.batch.popular-daily.cron:0 30 4 * * *}", zone = "Asia/Seoul")
    public void scheduled() {
        Long logId = batchLog.start(JOB_NAME, JOB_GROUP);
        try {
            int processed = run();
            batchLog.success(logId, processed);
            log.info("PopularQueryAggregateDailyJob 완료: processed={}", processed);
        } catch (RuntimeException e) {
            batchLog.failure(logId, e.getMessage());
            log.error("PopularQueryAggregateDailyJob 실패", e);
            throw e;
        }
    }
}
