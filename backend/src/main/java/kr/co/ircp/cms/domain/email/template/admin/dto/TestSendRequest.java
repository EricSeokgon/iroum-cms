package kr.co.ircp.cms.domain.email.template.admin.dto;

import java.util.Map;

/**
 * 테스트 발송 요청 (REQ-ET-021) — 수신자는 요청 관리자 본인으로 고정(요청 본문에 받지 않음).
 *
 * @param variables 샘플 변수맵
 */
public record TestSendRequest(Map<String, Object> variables) {

    public Map<String, Object> safeVariables() {
        return variables != null ? variables : Map.of();
    }
}
