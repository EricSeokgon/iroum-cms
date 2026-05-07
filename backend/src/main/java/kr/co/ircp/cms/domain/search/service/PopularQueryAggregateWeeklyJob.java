package kr.co.ircp.cms.domain.search.service;

import kr.co.ircp.cms.domain.governance.service.BatchExecutionLogService;
import kr.co.ircp.cms.domain.search.entity.SearchPopularCache;
import kr.co.ircp.cms.domain.search.repository.SearchLogMapper;
import kr.co.ircp.cms.domain.search.repository.SearchPopularCacheMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 주별 인기 검색어 집계 배치.
 *
 * <p>SPEC-CMS-010 REQ-SEARCH-006/007 — 매주 월요일 05:00 KST 실행.
 * 직전 한 주(월~일) search_log → search_popular_cache(period_type=WEEKLY).
 * period_date는 해당 주의 월요일.
 */
// @MX:NOTE: [AUTO] SPEC-CMS-010 주별 인기 검색어 집계 (REQ-SEARCH-007)
@Slf4j
@Component
@RequiredArgsConstructor
public class PopularQueryAggregateWeeklyJob {

    static final String JOB_NAME = "PopularQueryAggregateWeeklyJob";
    static final String JOB_GROUP = "SEARCH";

    private final SearchLogMapper searchLogMapper;
    private final SearchPopularCacheMapper popularCacheMapper;
    private final BatchExecutionLogService batchLog;

    public int run() {
        // 직전 주의 월요일을 period_date로 설정
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        LocalDate weekStart = today.minusWeeks(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate weekEnd = weekStart.plusDays(6);
        return run(weekStart, weekEnd);
    }

    public int run(LocalDate weekStart, LocalDate weekEnd) {
        List<Map<String, Object>> rows = searchLogMapper.aggregateDaily(weekStart, weekEnd, null);
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        // locale별로 query 합산 (조회 결과는 일자/locale별 그룹이므로 추가 reduce 필요)
        Map<String, Map<String, Long>> totalsByLocale = new HashMap<>();
        for (Map<String, Object> row : rows) {
            String locale = (String) row.get("locale");
            String query = (String) row.get("normalizedQuery");
            long count = ((Number) row.get("searchCount")).longValue();
            totalsByLocale
                    .computeIfAbsent(locale == null ? "ko" : locale, k -> new HashMap<>())
                    .merge(query, count, Long::sum);
        }

        // locale별 정렬 후 rank 1..N 부여
        int processed = 0;
        for (Map.Entry<String, Map<String, Long>> localeEntry : totalsByLocale.entrySet()) {
            String locale = localeEntry.getKey();
            List<Map.Entry<String, Long>> sorted = new ArrayList<>(localeEntry.getValue().entrySet());
            sorted.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));

            int rank = 0;
            for (Map.Entry<String, Long> e : sorted) {
                rank++;
                SearchPopularCache cache = SearchPopularCache.builder()
                        .periodType("WEEKLY")
                        .periodDate(weekStart)
                        .locale(locale)
                        .query(e.getKey())
                        .searchCount(e.getValue())
                        .rank(rank)
                        .build();
                popularCacheMapper.upsert(cache);
                processed++;
            }
        }
        return processed;
    }

    @Scheduled(cron = "${search.batch.popular-weekly.cron:0 0 5 * * MON}", zone = "Asia/Seoul")
    public void scheduled() {
        Long logId = batchLog.start(JOB_NAME, JOB_GROUP);
        try {
            int processed = run();
            batchLog.success(logId, processed);
            log.info("PopularQueryAggregateWeeklyJob 완료: processed={}", processed);
        } catch (RuntimeException e) {
            batchLog.failure(logId, e.getMessage());
            log.error("PopularQueryAggregateWeeklyJob 실패", e);
            throw e;
        }
    }
}
