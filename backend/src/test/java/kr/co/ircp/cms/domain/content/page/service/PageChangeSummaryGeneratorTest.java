package kr.co.ircp.cms.domain.content.page.service;

import kr.co.ircp.cms.domain.content.page.dto.PageUpdateRequest;
import kr.co.ircp.cms.domain.content.page.entity.Page;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SPEC-CMS-PAGE-HISTORY-001 REQ-PHIST-003 — changeSummary 자동 생성 단위 테스트.
 *
 * <p>diff 기반 자동 요약 생성 로직(순수 함수)을 검증한다.
 * AC-PHIST-009~012 매핑.
 */
// @MX:NOTE: [AUTO] PageChangeSummaryGeneratorTest — REQ-PHIST-003 자동 요약 단위 검증
// @MX:SPEC: SPEC-CMS-PAGE-HISTORY-001#REQ-PHIST-003
@DisplayName("페이지 changeSummary 자동 생성 (SPEC-CMS-PAGE-HISTORY-001 REQ-PHIST-003)")
class PageChangeSummaryGeneratorTest {

    private final PageChangeSummaryGenerator generator = new PageChangeSummaryGenerator();

    private Page page(String title, String slug) {
        return Page.builder().id(1L).title(title).slug(slug).currentVersion(1).build();
    }

    private PageUpdateRequest request(String title, String slug, String changeSummary) {
        return new PageUpdateRequest(title, slug, null, null, null, null, null, null, null, changeSummary);
    }

    @Test
    @DisplayName("AC-PHIST-009: changeSummary 생략 + 제목만 변경 → '제목 변경' 포함 자동 생성")
    void titleChangedOnly_autoGeneratesTitleChange() {
        Page current = page("원본 제목", "orig");
        PageUpdateRequest req = request("새 제목", "orig", null);

        String summary = generator.summarize(current, req);

        assertThat(summary).contains("제목 변경");
        assertThat(summary).doesNotContain("슬러그 변경");
    }

    @Test
    @DisplayName("AC-PHIST-010: changeSummary 생략 + 제목+슬러그 변경 → 두 라벨 모두 포함")
    void titleAndSlugChanged_autoGeneratesBoth() {
        Page current = page("원본 제목", "orig");
        PageUpdateRequest req = request("새 제목", "new-slug", null);

        String summary = generator.summarize(current, req);

        assertThat(summary).contains("제목 변경");
        assertThat(summary).contains("슬러그 변경");
    }

    @Test
    @DisplayName("AC-PHIST-011: changeSummary='긴급 오타 수정' 명시 → 자동 생성 미적용, 입력 그대로 사용")
    void explicitChangeSummary_takesPrecedence() {
        Page current = page("원본 제목", "orig");
        PageUpdateRequest req = request("새 제목", "new-slug", "긴급 오타 수정");

        String summary = generator.summarize(current, req);

        assertThat(summary).isEqualTo("긴급 오타 수정");
    }

    @Test
    @DisplayName("AC-PHIST-012: changeSummary 생략 + 변경 없음 → 비어있지 않은 기본 문구 기록")
    void noChange_recordsNonEmptyDefault() {
        Page current = page("동일 제목", "same");
        PageUpdateRequest req = request("동일 제목", "same", null);

        String summary = generator.summarize(current, req);

        assertThat(summary).isNotBlank();
        assertThat(summary).isEqualTo("변경 없음");
    }

    @Test
    @DisplayName("blank changeSummary(공백 문자열)도 자동 생성으로 처리한다")
    void blankChangeSummary_treatedAsAutoGenerate() {
        Page current = page("원본 제목", "orig");
        PageUpdateRequest req = request("새 제목", "orig", "   ");

        String summary = generator.summarize(current, req);

        assertThat(summary).contains("제목 변경");
    }
}
