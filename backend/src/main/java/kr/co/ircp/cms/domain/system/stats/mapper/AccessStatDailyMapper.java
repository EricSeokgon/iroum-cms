package kr.co.ircp.cms.domain.system.stats.mapper;

import kr.co.ircp.cms.domain.system.stats.dto.TrendItemResponse;
import kr.co.ircp.cms.domain.system.stats.entity.AccessStatDaily;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 일별 접속 통계 MyBatis Mapper.
 * REQ-SYSTEM-002-D
 */
@Mapper
public interface AccessStatDailyMapper {

    /**
     * 지정 날짜·사이트의 access_log를 집계하여 UPSERT.
     * DailyStatsBatchJob에서 호출.
     */
    void upsertForDate(@Param("targetDate") LocalDate targetDate,
                       @Param("siteId") long siteId);

    Optional<AccessStatDaily> findByDateAndSite(@Param("statDate") LocalDate statDate,
                                                 @Param("siteId") long siteId);

    /** 최근 30일 추이 */
    List<TrendItemResponse> findTrend30Days(@Param("siteId") long siteId);

    /** days 파라미터로 동적 기간 추이 (7/30/90일) */
    List<TrendItemResponse> findTrendDays(@Param("siteId") long siteId, @Param("days") int days);

    /** 오늘 통계 (todayVisits, todayUnique, todayPageViews 합산) */
    Optional<AccessStatDaily> findToday(@Param("siteId") long siteId);

    /** 최근 24h 평균 응답시간 + 에러율 */
    AccessStatDaily findLast24hStats(@Param("siteId") long siteId);
}
