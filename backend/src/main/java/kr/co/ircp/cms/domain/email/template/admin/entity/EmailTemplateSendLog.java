package kr.co.ircp.cms.domain.email.template.admin.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 이메일 템플릿 발송 로그 엔티티 (email_template_send_log).
 *
 * <p>SPEC-CMS-EMAIL-TEMPLATE-001 REQ-ET-050/051 — 실발송마다 1행 기록, 이력 보존.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailTemplateSendLog {

    private Long id;
    private Long templateId;
    private String templateCode;
    /** 암호화된 수신자 이메일 (PII). */
    private String recipientEnc;
    /** 수신자 이메일 HMAC. */
    private String recipientHmac;
    private String subject;
    /** SUCCESS|FAILED */
    private String status;
    private String errorMessage;
    private Integer retryCount;
    private Instant sentAt;
}
