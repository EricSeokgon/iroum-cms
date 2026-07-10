package kr.co.ircp.cms.domain.email.template.admin.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * SMTP 동적 설정 엔티티 (smtp_config) — 단일 활성 행 운용.
 *
 * <p>SPEC-CMS-EMAIL-TEMPLATE-001 REQ-ET-040/041/042.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SmtpConfig {

    private Long id;
    private String host;
    private Integer port;
    private String username;
    /** 암호화 저장된 비밀번호 (EmailEncryptionService). */
    private String passwordEnc;
    private String fromAddress;
    private String fromName;
    /** NONE|SSL|STARTTLS */
    private String encryption;
    private Boolean isActive;
    private Long updatedBy;
    private Instant updatedAt;
}
