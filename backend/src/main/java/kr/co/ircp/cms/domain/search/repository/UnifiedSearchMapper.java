package kr.co.ircp.cms.domain.search.repository;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 통합 검색 크로스도메인 집계 매퍼.
 *
 * <p>SPEC-CMS-010 REQ-SEARCH-001/004/005: 6개 도메인(board/content/policy/safety/media/publication)
 * 통합 검색 + 자동완성. UNION ALL + ts_rank_cd × 도메인 가중치 + ts_headline 패턴.
 *
 * <p>각 도메인 인덱스는 SPEC-CMS-003/004/006/007/MEDIA-001 마이그레이션에서 선행 구축된 것을 재사용.
 */
// @MX:NOTE: [AUTO] SPEC-CMS-010 통합 검색 크로스도메인 매퍼 (REQ-SEARCH-001/005)
@Mapper
public interface UnifiedSearchMapper {

    /**
     * 통합 검색 (UNION ALL + ts_rank_cd × 도메인 가중치 + ts_headline).
     *
     * <p>반환 Map 키: docType, docId, title, snippet, rank, createdAt, url, domain.
     *
     * @param query        sanitize 된 검색어 (동의어 확장 포함, ts_query 안전 토큰)
     * @param locale       ko | en
     * @param domain       ALL | board | content | policy | safety | media | publication
     * @param weights      도메인별 가중치 맵 (boards=1.0, contents=0.9, ...)
     * @param requesterId  현재 사용자 id (비공개 콘텐츠 노출 결정, NULL = 익명)
     * @param isAdmin      ADMIN 권한 여부
     * @param offset       페이지 offset
     * @param size         페이지 size
     */
    List<Map<String, Object>> searchUnified(
            @Param("query") String query,
            @Param("locale") String locale,
            @Param("domain") String domain,
            @Param("weights") Map<String, Double> weights,
            @Param("requesterId") Long requesterId,
            @Param("isAdmin") boolean isAdmin,
            @Param("offset") int offset,
            @Param("size") int size
    );

    /**
     * 통합 검색 결과 총 건수 (페이징용).
     */
    long countUnified(
            @Param("query") String query,
            @Param("locale") String locale,
            @Param("domain") String domain,
            @Param("requesterId") Long requesterId,
            @Param("isAdmin") boolean isAdmin
    );

    /**
     * 자동완성 prefix 매칭 (콘텐츠 제목 단독 — 인기검색어는 SearchPopularCacheMapper에서 별도 조회).
     *
     * <p>반환 Map 키: term, similarity.
     */
    List<Map<String, Object>> autocomplete(
            @Param("prefix") String prefix,
            @Param("locale") String locale,
            @Param("limit") int limit
    );

    // ─── 운영자 통계 (REQ-SEARCH-008 §6.6) ─────────────────────────────────────

    /**
     * 상위 검색어 (기간 내 빈도 상위 N).
     * 반환 Map 키: query(string), searchCount(long), clickCount(long), ctr(double).
     */
    List<Map<String, Object>> topQueries(
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("limit") int limit
    );

    /** 0건 검색 비율 (0.0 ~ 1.0). */
    Double zeroResultRatio(
            @Param("from") LocalDate from,
            @Param("to") LocalDate to
    );

    /** 평균 응답시간(ms). */
    Double avgResponseMs(
            @Param("from") LocalDate from,
            @Param("to") LocalDate to
    );

    /** 총 검색 수. */
    long totalSearchCount(
            @Param("from") LocalDate from,
            @Param("to") LocalDate to
    );

    /** 기간 내 고유 검색어 수. */
    long uniqueQueriesCount(
            @Param("from") LocalDate from,
            @Param("to") LocalDate to
    );
}
