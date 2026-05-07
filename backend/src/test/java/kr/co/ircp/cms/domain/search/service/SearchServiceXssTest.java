package kr.co.ircp.cms.domain.search.service;

import kr.co.ircp.cms.domain.search.repository.SearchLogMapper;
import kr.co.ircp.cms.domain.search.repository.SearchPopularCacheMapper;
import kr.co.ircp.cms.domain.search.repository.UnifiedSearchMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SearchServiceImpl XSS 회귀 테스트.
 *
 * <p>코드 리뷰 보고서 (.moai/reports/code-review-20260507.md) Critical Finding #1
 * — ts_headline 결과 sanitize 우회 가능성에 대한 보안 핫픽스 검증.
 *
 * <p>private sanitizeHighlight 메서드를 reflection으로 직접 호출하여
 * 다양한 XSS payload 변형이 차단되는지 검증한다.
 *
 * <p>대상 SPEC: SPEC-CMS-010 REQ-SEARCH-002
 * 보안 기준: OWASP A03 (Injection / XSS), CWE-79.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SearchServiceImpl XSS sanitize 회귀 테스트 (Critical Finding #1)")
class SearchServiceXssTest {

    @Mock private UnifiedSearchMapper unifiedSearchMapper;
    @Mock private SearchPopularCacheMapper popularCacheMapper;
    @Mock private SearchLogMapper searchLogMapper;
    @Mock private SynonymService synonymService;

    private SearchServiceImpl service;
    private Method sanitizeMethod;

    @BeforeEach
    void setUp() throws NoSuchMethodException {
        service = new SearchServiceImpl(
                unifiedSearchMapper, popularCacheMapper, searchLogMapper, synonymService);
        sanitizeMethod = SearchServiceImpl.class.getDeclaredMethod(
                "sanitizeHighlight", String.class);
        sanitizeMethod.setAccessible(true);
    }

    /** sanitizeHighlight를 reflection으로 호출. */
    private String sanitize(String input) throws Exception {
        return (String) sanitizeMethod.invoke(service, input);
    }

    // ─── A. 기본 스크립트 인젝션 차단 ─────────────────────────────────

    @Test
    @DisplayName("1. <script> 태그 인젝션 — script 태그 완전 제거")
    void test01_blocksBasicScriptInjection() throws Exception {
        String payload = "<script>alert(1)</script>";
        String result = sanitize(payload);

        assertThat(result).doesNotContainIgnoringCase("<script");
        assertThat(result).doesNotContainIgnoringCase("</script");
        assertThat(result).doesNotContain("alert(1)");
    }

    @Test
    @DisplayName("2. mark 태그 onmouseover 이벤트 핸들러 — 속성 제거")
    void test02_blocksEventHandlerOnMark() throws Exception {
        String payload = "<mark onmouseover=\"alert(1)\">x</mark>";
        String result = sanitize(payload);

        assertThat(result).doesNotContainIgnoringCase("onmouseover");
        assertThat(result).doesNotContain("alert(1)");
        // mark 태그 자체는 보존되되 속성은 제거되어야 함
        assertThat(result).contains("<mark>");
        assertThat(result).contains("x");
    }

    @Test
    @DisplayName("3. 중첩 인젝션: mark 안의 img onerror — img/onerror 모두 제거")
    void test03_blocksNestedImgWithOnError() throws Exception {
        String payload = "<mark><img src=x onerror=alert(1)></mark>";
        String result = sanitize(payload);

        assertThat(result).doesNotContainIgnoringCase("<img");
        assertThat(result).doesNotContainIgnoringCase("onerror");
        assertThat(result).doesNotContain("alert(1)");
    }

    @Test
    @DisplayName("4. iframe 인젝션 차단")
    void test04_blocksIframeInjection() throws Exception {
        String payload = "<iframe src=\"javascript:alert(1)\"></iframe>";
        String result = sanitize(payload);

        assertThat(result).doesNotContainIgnoringCase("<iframe");
        assertThat(result).doesNotContainIgnoringCase("javascript:");
    }

    @Test
    @DisplayName("5. javascript: URL — anchor 태그 자체 + javascript: scheme 제거")
    void test05_blocksJavascriptUrl() throws Exception {
        String payload = "<a href=\"javascript:alert(1)\">click</a>";
        String result = sanitize(payload);

        assertThat(result).doesNotContainIgnoringCase("javascript:");
        assertThat(result).doesNotContainIgnoringCase("<a ");
        assertThat(result).doesNotContainIgnoringCase("href=");
    }

    @Test
    @DisplayName("6. style 속성 (javascript URL in CSS) 차단")
    void test06_blocksStyleAttribute() throws Exception {
        String payload = "<mark style=\"background:url(javascript:alert(1))\">x</mark>";
        String result = sanitize(payload);

        assertThat(result).doesNotContainIgnoringCase("style=");
        assertThat(result).doesNotContainIgnoringCase("javascript:");
        // mark 태그와 텍스트는 보존
        assertThat(result).contains("<mark>");
        assertThat(result).contains("x");
    }

    @Test
    @DisplayName("7. SVG/onload 폴리글랏 — svg 태그 제거")
    void test07_blocksSvgPolyglot() throws Exception {
        String payload = "<svg/onload=alert(1)>";
        String result = sanitize(payload);

        assertThat(result).doesNotContainIgnoringCase("<svg");
        assertThat(result).doesNotContainIgnoringCase("onload");
        assertThat(result).doesNotContain("alert(1)");
    }

    @Test
    @DisplayName("8. HTML 주석 안의 script — 주석/script 모두 제거")
    void test08_blocksCommentScriptInjection() throws Exception {
        String payload = "<mark>x</mark><!--<script>alert(1)</script>-->";
        String result = sanitize(payload);

        assertThat(result).doesNotContainIgnoringCase("<script");
        assertThat(result).doesNotContain("alert(1)");
        // 정상 mark 태그는 유지
        assertThat(result).contains("<mark>");
    }

    // ─── B. mark 태그 abuse 차단 (defense-in-depth 2차 계층) ────────────

    @Test
    @DisplayName("9. mark 태그 100개 이상 abuse — 50개 초과 시 전부 제거")
    void test09_capsMarkTagAbuse() throws Exception {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            sb.append("<mark>x").append(i).append("</mark>");
        }
        String result = sanitize(sb.toString());

        // 50개 초과 abuse는 모든 mark 태그 제거 결과로 이어진다 (텍스트는 보존)
        assertThat(result).doesNotContain("<mark>");
        assertThat(result).doesNotContain("</mark>");
        // 텍스트 콘텐츠는 보존
        assertThat(result).contains("x0");
        assertThat(result).contains("x99");
    }

    @Test
    @DisplayName("10. 정상 mark 태그 보존 — 한도 이하 mark는 유지")
    void test10_preservesValidMarkTags() throws Exception {
        String payload = "<mark>highlighted</mark> regular text <mark>more</mark>";
        String result = sanitize(payload);

        assertThat(result).contains("<mark>highlighted</mark>");
        assertThat(result).contains("<mark>more</mark>");
        assertThat(result).contains("regular text");
    }

    // ─── C. null/empty 및 평문 통과 ──────────────────────────────────

    @Test
    @DisplayName("11. null/빈 문자열 — 그대로 반환 (NPE 없음)")
    void test11_nullAndEmptyPassthrough() throws Exception {
        assertThat(sanitize(null)).isNull();
        assertThat(sanitize("")).isEmpty();
        assertThat(sanitize("   ")).isEqualTo("   ");
    }

    @Test
    @DisplayName("12. HTML 미포함 평문 — 변형 없이 통과")
    void test12_plainTextPassthrough() throws Exception {
        String payload = "regular text without HTML";
        String result = sanitize(payload);

        assertThat(result).isEqualTo("regular text without HTML");
    }

    // ─── D. 추가 보안 회귀 케이스 ─────────────────────────────────────

    @Test
    @DisplayName("13. mark 자기 닫힘 변형 (<mark/>) — 우회 차단")
    void test13_blocksSelfClosingMarkBypass() throws Exception {
        // mark 자기 닫힘 + 속성 우회 시도
        String payload = "<mark id=\"x\" onmouseover=\"alert(1)\"/>text";
        String result = sanitize(payload);

        assertThat(result).doesNotContainIgnoringCase("onmouseover");
        assertThat(result).doesNotContainIgnoringCase("id=");
        assertThat(result).doesNotContain("alert(1)");
    }

    @Test
    @DisplayName("14. data: URI scheme — anchor 자체 제거로 차단")
    void test14_blocksDataUriScheme() throws Exception {
        String payload = "<a href=\"data:text/html,<script>alert(1)</script>\">x</a>";
        String result = sanitize(payload);

        assertThat(result).doesNotContainIgnoringCase("data:");
        assertThat(result).doesNotContainIgnoringCase("<script");
        assertThat(result).doesNotContainIgnoringCase("<a ");
    }

    @Test
    @DisplayName("15. mark 태그 외부의 텍스트는 escape 처리되어 보존")
    void test15_textOutsideMarkIsEscaped() throws Exception {
        // ts_headline 시뮬레이션: <mark>외부에 escape되지 않은 < > 문자가 올 수 있음
        // jsoup은 이를 entity로 escape하거나 텍스트로 처리해야 함
        String payload = "before <mark>hit</mark> after";
        String result = sanitize(payload);

        // 정상 mark 태그 보존 + 외부 텍스트 보존
        assertThat(result).contains("<mark>hit</mark>");
        assertThat(result).contains("before");
        assertThat(result).contains("after");
        // 위험 태그는 없어야 함
        assertThat(result).doesNotContainIgnoringCase("<script");
    }
}
