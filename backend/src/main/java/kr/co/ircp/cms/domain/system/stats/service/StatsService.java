package kr.co.ircp.cms.domain.system.stats.service;

import kr.co.ircp.cms.domain.system.stats.dto.TopPageResponse;
import kr.co.ircp.cms.domain.system.stats.dto.TrendItemResponse;

import java.time.LocalDate;
import java.util.List;

/**
 * 접속 통계 서비스 인터페이스.
 * REQ-SYSTEM-002-D, REQ-SYSTEM-003-D
 */
public interface StatsService {

    /** 전일 일별 통계 집계 (DailyStatsBatchJob 호출) */
    void aggregateDaily(LocalDate targetDate, long siteId);

    /** 전월 월별 통계 집계 (MonthlyStatsBatchJob 호출) */
    void aggregateMonthly(String statMonth, long siteId);

    /** 수동 재집계: 날짜 범위 */
    void recompute(LocalDate from, LocalDate to, long siteId);

    /** 최근 30일 추이 */
    List<TrendItemResponse> getTrend30Days(long siteId);

    /** days 파라미터로 동적 기간 추이 (7/30/90일) */
    List<TrendItemResponse> getTrendDays(long siteId, int days);

    /** Top Pages (7d 또는 30d) */
    List<TopPageResponse> getTopPages(int days, long siteId);
}
