package kr.co.ircp.cms.domain.email.template.admin.service;

import kr.co.ircp.cms.domain.email.template.admin.dto.RenderResult;
import kr.co.ircp.cms.domain.email.template.admin.exception.MissingTemplateVariableException;
import kr.co.ircp.cms.domain.email.template.admin.exception.TemplateRenderException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 이메일 템플릿 렌더러 — Thymeleaf 변수 치환 + 필수 변수 검증.
 *
 * <p>SPEC-CMS-EMAIL-TEMPLATE-001 REQ-ET-010/011.
 *
 * <ol>
 *   <li>템플릿의 {@code variables} 정의에서 {@code required=true}인 변수가 변수맵에 모두
 *       존재하는지 검증한다(누락 시 {@link MissingTemplateVariableException}).</li>
 *   <li>제목은 TEXT 엔진, HTML 본문/평문 본문은 각각 HTML/TEXT 엔진으로 치환한다.</li>
 * </ol>
 */
// @MX:NOTE: [AUTO] SPEC-CMS-EMAIL-TEMPLATE-001 — 필수 변수 검증 후 Thymeleaf 치환. DB 문자열을 입력으로 받음
// @MX:SPEC: SPEC-CMS-EMAIL-TEMPLATE-001#REQ-ET-010
@Component
public class EmailTemplateRenderer {

    private final ITemplateEngine subjectEngine;
    private final ITemplateEngine htmlEngine;

    public EmailTemplateRenderer(
            @Qualifier("emailSubjectTemplateEngine") ITemplateEngine subjectEngine,
            @Qualifier("emailHtmlTemplateEngine") ITemplateEngine htmlEngine) {
        this.subjectEngine = subjectEngine;
        this.htmlEngine = htmlEngine;
    }

    /**
     * 템플릿을 변수맵으로 치환하여 렌더링한다.
     *
     * @param subjectTemplate 제목 템플릿(평문)
     * @param bodyHtml        HTML 본문 템플릿
     * @param bodyText        평문 본문 템플릿(null 허용)
     * @param vars            치환 변수맵
     * @param variableDefs    템플릿의 변수 정의 목록({@code [{name, required}]}, null/빈 목록 허용)
     * @return 렌더링 결과
     * @throws MissingTemplateVariableException 필수 변수 누락 시
     * @throws TemplateRenderException          Thymeleaf 치환 실패 시
     */
    public RenderResult render(String subjectTemplate,
                               String bodyHtml,
                               String bodyText,
                               Map<String, Object> vars,
                               List<Map<String, Object>> variableDefs) {
        Map<String, Object> safeVars = vars != null ? vars : Map.of();
        validateRequired(variableDefs, safeVars);

        try {
            Context context = new Context();
            safeVars.forEach(context::setVariable);

            String subject = subjectEngine.process(nullToEmpty(subjectTemplate), context);
            String html = htmlEngine.process(nullToEmpty(bodyHtml), context);
            String text = bodyText != null
                    ? subjectEngine.process(bodyText, context)
                    : null;

            return new RenderResult(subject, html, text);
        } catch (Exception e) {
            throw new TemplateRenderException("이메일 템플릿 렌더링 실패: " + e.getMessage(), e);
        }
    }

    /** 필수 변수가 변수맵에 모두 존재하는지 검증한다. */
    private void validateRequired(List<Map<String, Object>> variableDefs, Map<String, Object> vars) {
        if (variableDefs == null || variableDefs.isEmpty()) {
            return;
        }
        List<String> missing = new ArrayList<>();
        for (Map<String, Object> def : variableDefs) {
            Object required = def.get("required");
            boolean isRequired = Boolean.TRUE.equals(required)
                    || "true".equalsIgnoreCase(String.valueOf(required));
            if (!isRequired) {
                continue;
            }
            Object name = def.get("name");
            if (name != null && !vars.containsKey(name.toString())) {
                missing.add(name.toString());
            }
        }
        if (!missing.isEmpty()) {
            throw new MissingTemplateVariableException(missing);
        }
    }

    private String nullToEmpty(String s) {
        return s != null ? s : "";
    }
}
