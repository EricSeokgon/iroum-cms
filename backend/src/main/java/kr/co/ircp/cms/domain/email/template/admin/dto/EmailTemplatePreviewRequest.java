package kr.co.ircp.cms.domain.email.template.admin.dto;

import java.util.Map;

/**
 * 미리보기 요청 (REQ-ET-020) — 샘플 변수맵으로 렌더링만 수행(실발송 없음).
 *
 * @param variables 샘플 변수맵
 */
public record EmailTemplatePreviewRequest(Map<String, Object> variables) {

    public Map<String, Object> safeVariables() {
        return variables != null ? variables : Map.of();
    }
}
