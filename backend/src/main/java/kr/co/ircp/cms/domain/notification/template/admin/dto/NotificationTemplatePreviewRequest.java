package kr.co.ircp.cms.domain.notification.template.admin.dto;

import java.util.Map;

/**
 * 알림 템플릿 미리보기 요청.
 *
 * <p>SPEC-CMS-NOTI-EXT-001 — sampleVariables로 치환 결과를 반환한다.
 */
public record NotificationTemplatePreviewRequest(
        Long templateId,
        Map<String, String> sampleVariables) {

    /** null 안전 샘플 변수. */
    public Map<String, String> safeVariables() {
        return sampleVariables != null ? sampleVariables : Map.of();
    }
}
