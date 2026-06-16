package kr.co.ircp.cms.domain.email.template.admin.exception;

import java.util.List;

/**
 * 템플릿의 필수 변수가 변수맵에 누락되었을 때 발생 (HTTP 400).
 *
 * <p>SPEC-CMS-EMAIL-TEMPLATE-001 REQ-ET-011 — 필수 변수 누락 시 렌더링 거부.
 * 누락된 변수 목록을 함께 전달한다.
 */
public class MissingTemplateVariableException extends RuntimeException {

    private final transient List<String> missingVariables;

    public MissingTemplateVariableException(List<String> missingVariables) {
        super("필수 템플릿 변수가 누락되었습니다: " + missingVariables);
        this.missingVariables = missingVariables;
    }

    public List<String> getMissingVariables() {
        return missingVariables;
    }
}
