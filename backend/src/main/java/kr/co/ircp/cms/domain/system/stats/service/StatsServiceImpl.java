package kr.co.ircp.cms.domain.system.stats.service;

import kr.co.ircp.cms.domain.system.stats.dto.TopPageResponse;
import kr.co.ircp.cms.domain.system.stats.dto.TrendItemResponse;
import kr.co.ircp.cms.domain.system.stats.mapper.AccessStatDailyMapper;
import kr.co.ircp.cms.domain.system.stats.mapper.AccessStatMonthlyMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 접속 통계 서비스 구현체.
 *
 * <p>REQ-SYSTEM-002-D: 일별 집계 (UPSERT)
 * REQ-SYSTEM-003-D: 월별 집계 (UPSERT + JSONB)
 */
@Service
@RequiredArgsConstructor
public class StatsServiceImpl implements StatsService {

    private final AccessStatDailyMapper dailyMapper;
    private final AccessStatMonthlyMapper monthlyMapper;

    @Override
    @Transactional
    @CacheEvict(value = "dashboard", allEntries = true)
    public void aggregateDaily(LocalDate targetDate, long siteId) {
        dailyMapper.upsertForDate(targetDate, siteId);
    }

    @Override
    @Transactional
    @CacheEvict(value = "dashboard", allEntries = true)
    public void aggregateMonthly(String statMonth, long siteId) {
        monthlyMapper.upsertForMonth(statMonth, siteId);
    }

    @Override
    @Transactional
    @CacheEvict(value = "dashboard", allEntries = true)
    public void recompute(LocalDate from, LocalDate to, long siteId) {
        // 날짜 범위를 순회하며 일별 UPSERT
        LocalDate cur = from;
        while (!cur.isAfter(to)) {
            dailyMapper.upsertForDate(cur, siteId);
            cur = cur.plusDays(1);
        }
        // 영향받은 월 집계 갱신
        String fromMonth = from.toString().substring(0, 7);
        String toMonth = to.toString().substring(0, 7);
        monthlyMapper.upsertForMonth(fromMonth, siteId);
        if (!fromMonth.equals(toMonth)) {
            monthlyMapper.upsertForMonth(toMonth, siteId);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<TrendItemResponse> getTrend30Days(long siteId) {
        return dailyMapper.findTrend30Days(siteId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TopPageResponse> getTopPages(int days, long siteId) {
        return monthlyMapper.findTopPages(days, siteId);
    }
}
