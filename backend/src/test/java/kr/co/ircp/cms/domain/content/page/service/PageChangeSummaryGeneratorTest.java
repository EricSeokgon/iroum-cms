package kr.co.ircp.cms.domain.content.page.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 페이지 변경 요약 자동 생성 단위 테스트.
 * REQ-PHIST-003 / AC-PHIST-011
 */
@DisplayName("PageChangeSummaryGenerator 단위 테스트")
class PageChangeSummaryGeneratorTest {

    @Test
    @DisplayName("제목 변경 시 '제목' 포함")
    void titleChanged_returnsCorrectSummary() {
        String result = PageChangeSummaryGenerator.generate("구제목", "신제목", "slug-a", "slug-a");
        assertThat(result).contains("제목");
    }

    @Test
    @DisplayName("슬러그 변경 시 'slug' 포함")
    void slugChanged_returnsCorrectSummary() {
        String result = PageChangeSummaryGenerator.generate("같은제목", "같은제목", "old-slug", "new-slug");
        assertThat(result).contains("slug");
    }

    @Test
    @DisplayName("둘 다 변경 시 두 항목 모두 포함")
    void bothChanged_returnsCorrectSummary() {
        String result = PageChangeSummaryGenerator.generate("구제목", "신제목", "old", "new");
        assertThat(result).contains("제목").contains("slug");
    }

    @Test
    @DisplayName("변경 없으면 비어있지 않은 기본 요약 반환")
    void nothingChanged_returnsNonBlank() {
        String result = PageChangeSummaryGenerator.generate("동일", "동일", "same", "same");
        assertThat(result).isNotBlank();
    }
}
