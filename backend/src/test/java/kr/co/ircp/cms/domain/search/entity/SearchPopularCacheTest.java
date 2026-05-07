package kr.co.ircp.cms.domain.search.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SearchPopularCache 엔티티 TDD 테스트.
 * REQ-SEARCH-006/007: 인기 검색어 캐시 필드 invariants 검증.
 */
@DisplayName("SearchPopularCache 엔티티 TDD 테스트 (REQ-SEARCH-006/007)")
class SearchPopularCacheTest {

    @Test
    @DisplayName("Lombok @Builder 로 모든 필드를 한 번에 설정할 수 있다")
    void builder_should_set_all_fields() {
        Instant refreshedAt = Instant.parse("2026-05-07T04:30:00Z");
        LocalDate periodDate = LocalDate.of(2026, 5, 6);

        SearchPopularCache cache = SearchPopularCache.builder()
                .id(1L)
                .periodType("DAILY")
                .periodDate(periodDate)
                .locale("ko")
                .query("서울시 청년")
                .searchCount(1542L)
                .rank(1)
                .refreshedAt(refreshedAt)
                .build();

        assertThat(cache.getId()).isEqualTo(1L);
        assertThat(cache.getPeriodType()).isEqualTo("DAILY");
        assertThat(cache.getPeriodDate()).isEqualTo(periodDate);
        assertThat(cache.getLocale()).isEqualTo("ko");
        assertThat(cache.getQuery()).isEqualTo("서울시 청년");
        assertThat(cache.getSearchCount()).isEqualTo(1542L);
        assertThat(cache.getRank()).isEqualTo(1);
        assertThat(cache.getRefreshedAt()).isEqualTo(refreshedAt);
    }

    @Test
    @DisplayName("기본 생성자로 생성 후 setter 로 필드 설정이 가능하다")
    void no_args_constructor_with_setters() {
        SearchPopularCache cache = new SearchPopularCache();
        cache.setPeriodType("WEEKLY");
        cache.setPeriodDate(LocalDate.of(2026, 4, 27));
        cache.setLocale("en");
        cache.setQuery("youth policy");
        cache.setSearchCount(100L);
        cache.setRank(2);

        assertThat(cache.getPeriodType()).isEqualTo("WEEKLY");
        assertThat(cache.getPeriodDate()).isEqualTo(LocalDate.of(2026, 4, 27));
        assertThat(cache.getLocale()).isEqualTo("en");
        assertThat(cache.getQuery()).isEqualTo("youth policy");
        assertThat(cache.getSearchCount()).isEqualTo(100L);
        assertThat(cache.getRank()).isEqualTo(2);
    }

    @Test
    @DisplayName("동일 (period_type, period_date, locale, query) 가 같으면 equals/hashCode 가 일치한다")
    void equals_and_hashcode_should_match_for_same_unique_key() {
        Instant refreshedAt = Instant.parse("2026-05-07T04:30:00Z");
        LocalDate periodDate = LocalDate.of(2026, 5, 6);

        SearchPopularCache a = SearchPopularCache.builder()
                .id(1L).periodType("DAILY").periodDate(periodDate).locale("ko")
                .query("청년").searchCount(50L).rank(1).refreshedAt(refreshedAt).build();
        SearchPopularCache b = SearchPopularCache.builder()
                .id(1L).periodType("DAILY").periodDate(periodDate).locale("ko")
                .query("청년").searchCount(50L).rank(1).refreshedAt(refreshedAt).build();

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    @DisplayName("SearchLogPeriodType enum 의 name() 값이 DDL chk_spc_period 와 일치한다")
    void period_type_enum_should_match_ddl_check_constraint() {
        assertThat(SearchLogPeriodType.DAILY.name()).isEqualTo("DAILY");
        assertThat(SearchLogPeriodType.WEEKLY.name()).isEqualTo("WEEKLY");
        assertThat(SearchLogPeriodType.MONTHLY.name()).isEqualTo("MONTHLY");
        // DDL: CHECK (period_type IN ('DAILY','WEEKLY','MONTHLY')) — 3개 값 모두 존재
        assertThat(SearchLogPeriodType.values()).hasSize(3);
    }
}
