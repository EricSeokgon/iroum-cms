package kr.co.ircp.cms.domain.search.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SearchLog 엔티티 TDD 테스트.
 * REQ-SEARCH-008: 검색 로그 적재 필드 invariants 검증.
 */
@DisplayName("SearchLog 엔티티 TDD 테스트 (REQ-SEARCH-008)")
class SearchLogTest {

    @Test
    @DisplayName("Lombok @Builder 로 모든 필드를 한 번에 설정할 수 있다")
    void builder_should_set_all_fields() {
        Instant now = Instant.parse("2026-05-07T10:00:00Z");
        SearchLog log = SearchLog.builder()
                .id(100L)
                .userId(10L)
                .sessionId("sess-xyz")
                .query("서울 청년")
                .normalizedQuery("서울 청년")
                .expandedQuery("서울 청년 | 청년 정책")
                .resultCount(42)
                .responseMs(145)
                .clickedDocType("board")
                .clickedDocId(12345L)
                .clickedAt(now)
                .clickedRank(3)
                .locale("ko")
                .domainFilter("ALL")
                .ipHash("abcdef0123456789")
                .createdAt(now)
                .build();

        assertThat(log.getId()).isEqualTo(100L);
        assertThat(log.getUserId()).isEqualTo(10L);
        assertThat(log.getSessionId()).isEqualTo("sess-xyz");
        assertThat(log.getQuery()).isEqualTo("서울 청년");
        assertThat(log.getNormalizedQuery()).isEqualTo("서울 청년");
        assertThat(log.getExpandedQuery()).isEqualTo("서울 청년 | 청년 정책");
        assertThat(log.getResultCount()).isEqualTo(42);
        assertThat(log.getResponseMs()).isEqualTo(145);
        assertThat(log.getClickedDocType()).isEqualTo("board");
        assertThat(log.getClickedDocId()).isEqualTo(12345L);
        assertThat(log.getClickedAt()).isEqualTo(now);
        assertThat(log.getClickedRank()).isEqualTo(3);
        assertThat(log.getLocale()).isEqualTo("ko");
        assertThat(log.getDomainFilter()).isEqualTo("ALL");
        assertThat(log.getIpHash()).isEqualTo("abcdef0123456789");
        assertThat(log.getCreatedAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("기본 생성자로 생성 후 setter 로 필드 설정이 가능하다 (@Data)")
    void no_args_constructor_with_setters() {
        SearchLog log = new SearchLog();
        log.setQuery("청년");
        log.setNormalizedQuery("청년");
        log.setLocale("ko");
        log.setDomainFilter("ALL");
        log.setSessionId("sess-1");

        assertThat(log.getQuery()).isEqualTo("청년");
        assertThat(log.getNormalizedQuery()).isEqualTo("청년");
        assertThat(log.getLocale()).isEqualTo("ko");
        assertThat(log.getDomainFilter()).isEqualTo("ALL");
        assertThat(log.getSessionId()).isEqualTo("sess-1");
        // 비로그인 시 userId 는 NULL 이어야 한다
        assertThat(log.getUserId()).isNull();
        // 클릭 정보는 초기에 NULL
        assertThat(log.getClickedDocType()).isNull();
        assertThat(log.getClickedDocId()).isNull();
        assertThat(log.getClickedAt()).isNull();
    }

    @Test
    @DisplayName("동일 필드값을 가진 두 인스턴스는 equals/hashCode 가 일치한다 (@Data)")
    void equals_and_hashcode_should_match_for_same_fields() {
        Instant now = Instant.parse("2026-05-07T10:00:00Z");
        SearchLog a = SearchLog.builder()
                .id(1L).sessionId("s1").query("q").normalizedQuery("q")
                .resultCount(0).responseMs(0).locale("ko").domainFilter("ALL")
                .createdAt(now).build();
        SearchLog b = SearchLog.builder()
                .id(1L).sessionId("s1").query("q").normalizedQuery("q")
                .resultCount(0).responseMs(0).locale("ko").domainFilter("ALL")
                .createdAt(now).build();

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    @DisplayName("@AllArgsConstructor 로 16개 인자 모두 받는 생성자가 동작한다")
    void all_args_constructor_should_work() {
        Instant now = Instant.parse("2026-05-07T10:00:00Z");
        SearchLog log = new SearchLog(
                1L, 10L, "sess", "원본", "정규화", "확장",
                5, 100, "board", 999L, now, 1,
                "ko", "ALL", "ipHash", now
        );

        assertThat(log.getId()).isEqualTo(1L);
        assertThat(log.getResultCount()).isEqualTo(5);
        assertThat(log.getResponseMs()).isEqualTo(100);
        assertThat(log.getClickedRank()).isEqualTo(1);
    }

    @Test
    @DisplayName("primitive 필드 resultCount/responseMs 의 기본값은 0 이다")
    void primitive_fields_default_to_zero() {
        SearchLog log = new SearchLog();
        assertThat(log.getResultCount()).isZero();
        assertThat(log.getResponseMs()).isZero();
    }
}
