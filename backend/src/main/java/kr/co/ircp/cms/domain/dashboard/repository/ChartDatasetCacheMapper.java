package kr.co.ircp.cms.domain.dashboard.repository;

import kr.co.ircp.cms.domain.dashboard.entity.ChartDatasetCache;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * chart_dataset_cache MyBatis 매퍼.
 * REQ-VIZ-005-D-3, 005-D-5
 */
@Mapper
public interface ChartDatasetCacheMapper {

    void insert(ChartDatasetCache cache);

    Optional<ChartDatasetCache> findActiveByCacheKey(@Param("cacheKey") String cacheKey);

    /** REQ-VIZ-005-D-5: 위젯/KPI 단위 만료 처리. */
    int expireByWidgetIds(@Param("widgetIds") List<Long> widgetIds);

    int expireByCacheKeyPrefix(@Param("prefix") String prefix);

    int expireAll();

    long countActive();

    long countExpired();
}
