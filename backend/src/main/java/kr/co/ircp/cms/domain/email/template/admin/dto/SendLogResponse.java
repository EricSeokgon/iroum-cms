package kr.co.ircp.cms.domain.email.template.admin.dto;

import kr.co.ircp.cms.domain.email.template.admin.entity.EmailTemplateSendLog;

import java.time.Instant;

/**
 * 발송 로그 응답 — 수신자 이메일(PII)은 노출하지 않는다.
 *
 * <p>SPEC-CMS-EMAIL-TEMPLATE-001 REQ-ET-051.
 */
public record SendLogResponse(
        Long id,
        Long templateId,
        String templateCode,
        String subject,
        String status,
        String errorMessage,
        int retryCount,
        Instant sentAt) {

    public static SendLogResponse from(EmailTemplateSendLog log) {
        return new SendLogResponse(
                log.getId(),
                log.getTemplateId(),
                log.getTemplateCode(),
                log.getSubject(),
                log.getStatus(),
                log.getErrorMessage(),
                log.getRetryCount() != null ? log.getRetryCount() : 0,
                log.getSentAt());
    }
}
