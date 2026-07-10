package kr.co.ircp.cms.domain.email.template.admin.service;

import kr.co.ircp.cms.domain.email.template.admin.config.EmailTemplateConfig;
import kr.co.ircp.cms.domain.email.template.admin.dto.RenderResult;
import kr.co.ircp.cms.domain.email.template.admin.exception.MissingTemplateVariableException;
import kr.co.ircp.cms.domain.email.template.admin.exception.TemplateRenderException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * EmailTemplateRenderer 단위 테스트 (RED→GREEN).
 *
 * <p>SPEC-CMS-EMAIL-TEMPLATE-001 REQ-ET-010/011 — Thymeleaf 변수 치환 + 필수 변수 검증.
 * 실제 Thymeleaf 엔진(EmailTemplateConfig 빈)을 직접 생성하여 검증한다(Docker/Spring 불필요).
 */
@DisplayName("EmailTemplateRenderer 단위 테스트 (REQ-ET-010/011)")
class EmailTemplateRendererTest {

    private EmailTemplateRenderer renderer;

    @BeforeEach
    void setUp() {
        EmailTemplateConfig config = new EmailTemplateConfig();
        renderer = new EmailTemplateRenderer(
                config.emailSubjectTemplateEngine(),
                config.emailHtmlTemplateEngine());
    }

    @Test
    @DisplayName("AC-ET-005: ${name} 변수가 제목·HTML 본문에서 치환된다")
    void render_substitutesVariables() {
        RenderResult result = renderer.render(
                "[iroum] [(${name})]님 안녕하세요",
                "<p>[(${name})]님, 코드는 <b>[(${code})]</b> 입니다.</p>",
                null,
                Map.of("name", "홍길동", "code", "123456"),
                List.of());

        assertThat(result.subject()).contains("홍길동");
        assertThat(result.bodyHtml()).contains("홍길동").contains("123456");
    }

    @Test
    @DisplayName("AC-ET-006: 필수 변수 누락 시 누락 목록과 함께 거부한다")
    void render_throwsWhenRequiredVariableMissing() {
        List<Map<String, Object>> variables = List.of(
                Map.of("name", "name", "required", true),
                Map.of("name", "code", "required", true));

        assertThatThrownBy(() -> renderer.render(
                "제목 [(${name})]",
                "<p>[(${name})]</p>",
                null,
                Map.of("name", "홍길동"), // code 누락
                variables))
                .isInstanceOf(MissingTemplateVariableException.class)
                .satisfies(ex -> assertThat(
                        ((MissingTemplateVariableException) ex).getMissingVariables())
                        .containsExactly("code"));
    }

    @Test
    @DisplayName("필수가 아닌(optional) 변수는 누락되어도 렌더링된다")
    void render_optionalVariableMayBeMissing() {
        List<Map<String, Object>> variables = List.of(
                Map.of("name", "name", "required", true),
                Map.of("name", "nickname", "required", false));

        RenderResult result = renderer.render(
                "제목 [(${name})]",
                "<p>[(${name})]</p>",
                null,
                Map.of("name", "홍길동"),
                variables);

        assertThat(result.subject()).contains("홍길동");
    }

    @Test
    @DisplayName("body_text도 함께 치환된다")
    void render_substitutesPlainTextBody() {
        RenderResult result = renderer.render(
                "제목",
                "<p>[(${name})]</p>",
                "안녕하세요 [(${name})]님",
                Map.of("name", "이몽룡"),
                List.of());

        assertThat(result.bodyText()).contains("이몽룡");
    }

    @Test
    @DisplayName("잘못된 템플릿 문법은 TemplateRenderException으로 변환된다")
    void render_invalidTemplateThrowsRenderException() {
        assertThatThrownBy(() -> renderer.render(
                "제목",
                "<p>[(${ 1 + })]</p>", // 구문상 깨진 SpEL 표현식
                null,
                Map.of(),
                List.of()))
                .isInstanceOf(TemplateRenderException.class);
    }
}
