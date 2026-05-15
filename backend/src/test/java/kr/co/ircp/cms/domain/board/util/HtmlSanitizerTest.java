package kr.co.ircp.cms.domain.board.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HtmlSanitizer 단위 테스트.
 *
 * <p>SPEC-CMS-SECURITY-XSS — Stored XSS 방어 동작을 검증한다.
 * Safelist 정책에 따라 script/iframe/on* 이벤트 핸들러는 제거되어야 하며,
 * class 속성은 유지되고 style 속성은 CSS injection 방어를 위해 제거된다.
 */
@DisplayName("HtmlSanitizer — Stored XSS 방어 동작 검증")
class HtmlSanitizerTest {

    private final HtmlSanitizer sanitizer = new HtmlSanitizer();

    @Test
    @DisplayName("sanitize(null) — null 입력 시 null 반환 (NPE 방지)")
    void sanitize_null_returnsNull() {
        assertThat(sanitizer.sanitize(null)).isNull();
    }

    @Test
    @DisplayName("sanitize 빈 문자열 — 빈 문자열 그대로 반환")
    void sanitize_emptyString_returnsEmpty() {
        assertThat(sanitizer.sanitize("")).isEmpty();
    }

    @Test
    @DisplayName("sanitize — script 태그가 제거된다")
    void sanitize_removesScriptTag() {
        String input = "<p>안녕</p><script>alert('xss')</script>";
        String result = sanitizer.sanitize(input);
        // script 태그와 내부 본문 모두 제거되어야 함
        assertThat(result).doesNotContain("<script>");
        assertThat(result).doesNotContain("alert");
        assertThat(result).contains("<p>안녕</p>");
    }

    @Test
    @DisplayName("sanitize — iframe 태그가 제거된다")
    void sanitize_removesIframeTag() {
        String input = "<p>내용</p><iframe src=\"http://evil.com\"></iframe>";
        String result = sanitizer.sanitize(input);
        assertThat(result).doesNotContain("<iframe");
        assertThat(result).doesNotContain("evil.com");
        assertThat(result).contains("<p>내용</p>");
    }

    @Test
    @DisplayName("sanitize — onclick 등 on* 이벤트 핸들러가 제거된다")
    void sanitize_removesEventHandlers() {
        // Jsoup relaxed Safelist는 상대 경로 href 를 보호하지 않으므로
        // 절대 URL로 href 보존을 확인한다 (onclick 제거가 본 테스트의 핵심).
        String input = "<a href=\"http://example.com/safe\" onclick=\"alert('xss')\">링크</a>";
        String result = sanitizer.sanitize(input);
        assertThat(result).doesNotContain("onclick");
        assertThat(result).doesNotContain("alert");
        assertThat(result).contains("href=\"http://example.com/safe\"");
    }

    @Test
    @DisplayName("sanitize — javascript: URL이 제거된다")
    void sanitize_removesJavascriptUrl() {
        String input = "<a href=\"javascript:alert('xss')\">위험</a>";
        String result = sanitizer.sanitize(input);
        assertThat(result).doesNotContain("javascript:");
        assertThat(result).doesNotContain("alert");
    }

    @Test
    @DisplayName("sanitize — 일반 텍스트와 안전한 태그는 보존된다")
    void sanitize_preservesSafeHtml() {
        String input = "<p>본문</p><strong>강조</strong><em>이태릭</em>";
        String result = sanitizer.sanitize(input);
        assertThat(result).contains("<p>본문</p>");
        assertThat(result).contains("<strong>강조</strong>");
        assertThat(result).contains("<em>이태릭</em>");
    }

    @Test
    @DisplayName("sanitize — class 속성은 유지되고 style 속성은 CSS injection 방어로 제거된다")
    void sanitize_preservesClassRemovesStyle() {
        String input = "<p class=\"intro\" style=\"color: red;\">스타일 본문</p>";
        String result = sanitizer.sanitize(input);
        assertThat(result).contains("class=\"intro\"");
        // style 속성은 CSS injection 벡터로 판정, 제거되어야 함
        assertThat(result).doesNotContain("style=");
        assertThat(result).doesNotContain("color: red");
    }

    @Test
    @DisplayName("sanitize — table 계열 태그(figure/table/thead/tbody/tr/td)는 보존된다")
    void sanitize_preservesTableTags() {
        // Jsoup 은 보기 좋게(pretty-print) 출력 시 줄바꿈/공백을 삽입하므로
        // 태그 단위로 존재 여부만 검증한다 (구조 보존이 본 테스트의 핵심).
        String input = "<figure><figcaption>표 1</figcaption>"
                + "<table><thead><tr><th>헤더</th></tr></thead>"
                + "<tbody><tr><td>데이터</td></tr></tbody></table></figure>";
        String result = sanitizer.sanitize(input);
        assertThat(result).contains("<figure>");
        assertThat(result).contains("<figcaption>");
        assertThat(result).contains("표 1");
        assertThat(result).contains("<table>");
        assertThat(result).contains("<thead>");
        assertThat(result).contains("<tbody>");
        assertThat(result).contains("<td>");
        assertThat(result).contains("데이터");
    }

    @Test
    @DisplayName("sanitize — img 태그의 onerror 핸들러는 제거된다")
    void sanitize_removesImgOnError() {
        String input = "<img src=\"x\" onerror=\"alert('xss')\">";
        String result = sanitizer.sanitize(input);
        assertThat(result).doesNotContain("onerror");
        assertThat(result).doesNotContain("alert");
    }
}
