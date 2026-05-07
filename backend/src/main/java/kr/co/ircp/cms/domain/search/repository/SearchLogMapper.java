package kr.co.ircp.cms.domain.search.repository;

import kr.co.ircp.cms.domain.search.entity.SearchLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 검색 로그 MyBatis 매퍼.
 *
 * <p>SPEC-CMS-010 REQ-SEARCH-008: 검색 로그 적재/조회/클릭 추적/보존 정책 지원.
 */
// @MX:NOTE: [AUTO] SPEC-CMS-010 검색 로그 매퍼 (REQ-SEARCH-008)
@Mapper
public interface SearchLogMapper {

    /** 검색 로그 적재 (비동기 INSERT) */
    void insert(SearchLog log);

    /** ID로 단건 조회 (클릭 추적 사전 검증용) */
    Optional<SearchLog> findById(@Param("id") Long id);

    /**
     * 클릭 정보 갱신 (REQ-SEARCH-008 클릭 추적용).
     * clicked_doc_type/id/rank/clicked_at(NOW())을 한 번에 업데이트.
     */
    int updateClickInfo(
            @Param("id") Long id,
            @Param("clickedDocType") String clickedDocType,
            @Param("clickedDocId") Long clickedDocId,
            @Param("clickedRank") Integer clickedRank
    );

    /**
     * 일별 normalized_query 빈도 집계 (REQ-SEARCH-007 PopularQueryAggregateJob 용).
     * 결과: List of (locale, normalizedQuery, searchCount).
     * HAVING/result_count 필터는 호출자 책임(서비스 레이어 또는 직접 SQL)이며,
     * 본 메서드는 GROUP BY 단순 집계만 제공한다.
     */
    List<Map<String, Object>> aggregateDaily(
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("locale") String locale
    );

    /**
     * retention 정책용 보존 기간 경과 행 삭제.
     * SPEC-CMS-009 retention_policy(target_table='search_log') 트리거가 우선 동작하나,
     * 매퍼 메서드로도 직접 호출 가능.
     */
    int deleteOlderThan(@Param("months") int months);

    /** 통계용: 기간 내 검색 로그 수 */
    long countByPeriod(
            @Param("from") LocalDate from,
            @Param("to") LocalDate to
    );
}
