package kr.co.ircp.cms.domain.system.stats.mapper;

import kr.co.ircp.cms.domain.system.stats.dto.TopPageResponse;
import kr.co.ircp.cms.domain.system.stats.entity.AccessStatMonthly;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * 월별 접속 통계 MyBatis Mapper.
 * REQ-SYSTEM-003-D
 */
@Mapper
public interface AccessStatMonthlyMapper {

    /**
     * 지정 월(YYYY-MM)의 daily 통계를 집계하여 UPSERT.
     * MonthlyStatsBatchJob에서 호출.
     */
    void upsertForMonth(@Param("statMonth") String statMonth,
                        @Param("siteId") long siteId);

    Optional<AccessStatMonthly> findByMonthAndSite(@Param("statMonth") String statMonth,
                                                    @Param("siteId") long siteId);

    /** Top Pages (7일 또는 30일) */
    List<TopPageResponse> findTopPages(@Param("days") int days,
                                       @Param("siteId") long siteId);
}
