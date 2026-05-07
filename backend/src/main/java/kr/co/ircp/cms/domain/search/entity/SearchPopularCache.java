package kr.co.ircp.cms.domain.search.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 인기 검색어 캐시 엔티티.
 *
 * <p>SPEC-CMS-010 REQ-SEARCH-006/007: 일/주/월별 정규화 쿼리 빈도 캐시.
 * 인기 검색어 집계 배치(PopularQueryAggregateDailyJob 등)가 UPSERT 한다.
 */
// @MX:NOTE: [AUTO] SPEC-CMS-010 인기 검색어 캐시 엔티티 (REQ-SEARCH-006/007)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchPopularCache {

    private Long id;
    /** DAILY | WEEKLY | MONTHLY */
    private String periodType;
    /** DAILY=대상일, WEEKLY=주 시작 월요일, MONTHLY=월 1일 */
    private LocalDate periodDate;
    /** ko | en */
    private String locale;
    /** normalized_query */
    private String query;
    private long searchCount;
    /** 1..N (period_type, period_date, locale 내 순위) */
    private int rank;
    private Instant refreshedAt;
}
