package kr.co.ircp.cms.domain.search.repository;

import kr.co.ircp.cms.domain.search.entity.SearchPopularCache;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * 인기 검색어 캐시 MyBatis 매퍼.
 *
 * <p>SPEC-CMS-010 REQ-SEARCH-006/007: 일/주/월별 인기 검색어 캐시 UPSERT 및 조회.
 */
// @MX:NOTE: [AUTO] SPEC-CMS-010 인기 검색어 캐시 매퍼 (REQ-SEARCH-006/007)
@Mapper
public interface SearchPopularCacheMapper {

    /**
     * UNIQUE(period_type, period_date, locale, query) 충돌 시 search_count/rank/refreshed_at 갱신.
     * PostgreSQL ON CONFLICT 사용.
     */
    void upsert(SearchPopularCache row);

    /**
     * 인기 검색어 TOP-N 조회 (REQ-SEARCH-006).
     * rank 오름차순 정렬, limit 적용.
     */
    List<SearchPopularCache> findTopN(
            @Param("periodType") String periodType,
            @Param("periodDate") LocalDate periodDate,
            @Param("locale") String locale,
            @Param("limit") int limit
    );

    /** 재집계 시 cleanup 용 (UPSERT 전 기존 행 삭제 옵션) */
    int deleteByPeriod(
            @Param("periodType") String periodType,
            @Param("periodDate") LocalDate periodDate,
            @Param("locale") String locale
    );

    /** 캐시 존재 여부 검증 (캐시 미스 폴백 판단용) */
    long countCached(
            @Param("periodType") String periodType,
            @Param("periodDate") LocalDate periodDate,
            @Param("locale") String locale
    );
}
