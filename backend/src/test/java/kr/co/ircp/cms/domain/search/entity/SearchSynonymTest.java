package kr.co.ircp.cms.domain.search.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SearchSynonym 엔티티 TDD 테스트.
 * REQ-SEARCH-009: 동의어 사전 필드 invariants 검증.
 */
@DisplayName("SearchSynonym 엔티티 TDD 테스트 (REQ-SEARCH-009)")
class SearchSynonymTest {

    @Test
    @DisplayName("Lombok @Builder 로 모든 필드를 한 번에 설정할 수 있다")
    void builder_should_set_all_fields() {
        Instant createdAt = Instant.parse("2026-05-07T09:00:00Z");
        Instant updatedAt = Instant.parse("2026-05-07T09:30:00Z");

        SearchSynonym synonym = SearchSynonym.builder()
                .id(10L)
                .term("수도")
                .synonym("서울")
                .locale("ko")
                .status("ACTIVE")
                .description("공공기관 공식 약어")
                .createdBy(1L)
                .createdAt(createdAt)
                .updatedBy(1L)
                .updatedAt(updatedAt)
                .build();

        assertThat(synonym.getId()).isEqualTo(10L);
        assertThat(synonym.getTerm()).isEqualTo("수도");
        assertThat(synonym.getSynonym()).isEqualTo("서울");
        assertThat(synonym.getLocale()).isEqualTo("ko");
        assertThat(synonym.getStatus()).isEqualTo("ACTIVE");
        assertThat(synonym.getDescription()).isEqualTo("공공기관 공식 약어");
        assertThat(synonym.getCreatedBy()).isEqualTo(1L);
        assertThat(synonym.getCreatedAt()).isEqualTo(createdAt);
        assertThat(synonym.getUpdatedBy()).isEqualTo(1L);
        assertThat(synonym.getUpdatedAt()).isEqualTo(updatedAt);
    }

    @Test
    @DisplayName("기본 생성자로 생성 후 setter 로 필드 설정이 가능하다")
    void no_args_constructor_with_setters() {
        SearchSynonym synonym = new SearchSynonym();
        synonym.setTerm("AI");
        synonym.setSynonym("artificial intelligence");
        synonym.setLocale("en");
        synonym.setStatus("ACTIVE");

        assertThat(synonym.getTerm()).isEqualTo("AI");
        assertThat(synonym.getSynonym()).isEqualTo("artificial intelligence");
        assertThat(synonym.getLocale()).isEqualTo("en");
        assertThat(synonym.getStatus()).isEqualTo("ACTIVE");
        // description, createdBy 는 NULL 허용
        assertThat(synonym.getDescription()).isNull();
        assertThat(synonym.getCreatedBy()).isNull();
    }

    @Test
    @DisplayName("동일 필드값 두 인스턴스의 equals/hashCode 일치 (@Data)")
    void equals_and_hashcode_should_match_for_same_fields() {
        Instant t = Instant.parse("2026-05-07T09:00:00Z");
        SearchSynonym a = SearchSynonym.builder()
                .id(1L).term("수도").synonym("서울").locale("ko").status("ACTIVE")
                .createdAt(t).updatedAt(t).build();
        SearchSynonym b = SearchSynonym.builder()
                .id(1L).term("수도").synonym("서울").locale("ko").status("ACTIVE")
                .createdAt(t).updatedAt(t).build();

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    @DisplayName("SearchSynonymStatus enum 의 name() 값이 DDL chk_ss_status 와 일치한다")
    void status_enum_should_match_ddl_check_constraint() {
        // DDL: CHECK (status IN ('ACTIVE','PAUSED'))
        assertThat(SearchSynonymStatus.ACTIVE.name()).isEqualTo("ACTIVE");
        assertThat(SearchSynonymStatus.PAUSED.name()).isEqualTo("PAUSED");
        assertThat(SearchSynonymStatus.values()).hasSize(2);
    }
}
