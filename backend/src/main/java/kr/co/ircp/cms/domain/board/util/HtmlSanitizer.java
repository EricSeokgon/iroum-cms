package kr.co.ircp.cms.domain.board.util;

import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

/**
 * RICH_TEXT 콘텐츠 XSS 방어용 Jsoup sanitizer.
 *
 * <p>SPEC-CMS-SECURITY-XSS — Stored XSS 방어.
 * 사용자 입력 HTML을 안전한 화이트리스트 기반으로 정화한다.
 *
 * <p>Safelist 정책:
 * <ul>
 *   <li>relaxed 기본 + figure/figcaption/table 계열 보조 태그 허용</li>
 *   <li>class 속성 허용 (디자인 시스템 호환) — style 속성 불허 (CSS injection 방어)</li>
 *   <li>script/iframe/on* 이벤트 핸들러 제거</li>
 * </ul>
 *
 * <p>WARN: style 속성은 SPEC-CMS-SECURITY-XSS 리뷰에서 CSS injection 벡터로 판정 제거됨.
 * 에디터 스타일링은 class 속성 + 디자인 시스템 CSS로 처리한다.
 */
// @MX:ANCHOR: [AUTO] HtmlSanitizer — XSS 방어 공통 진입점 (fan_in >= 5: Post/Publication/Faq/Qna/Survey 서비스)
// @MX:REASON: 다수 서비스가 RICH_TEXT 저장 시 호출 — Safelist 변경 시 모든 게시판 영향
@Component
public class HtmlSanitizer {

    // style 속성 전역 허용 금지 — expression()/url() CSS injection 벡터 차단
    private static final Safelist SAFELIST = Safelist.relaxed()
            .addAttributes(":all", "class")
            .addTags("figure", "figcaption", "table", "thead", "tbody", "tr", "th", "td");

    /**
     * HTML 콘텐츠를 안전한 화이트리스트로 정화한다.
     *
     * @param html 원본 HTML (null 허용)
     * @return 정화된 HTML, 입력이 null이면 null 반환
     */
    public String sanitize(String html) {
        if (html == null) {
            return null;
        }
        return Jsoup.clean(html, SAFELIST);
    }
}
