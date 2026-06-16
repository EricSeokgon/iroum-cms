package kr.co.ircp.cms.domain.email.template.admin.dto;

import kr.co.ircp.cms.domain.email.template.admin.entity.SmtpConfig;

import java.time.Instant;

/**
 * SMTP 설정 응답 — 비밀번호는 마스킹(REQ-ET-040)하여 노출한다.
 */
public record SmtpConfigResponse(
        Long id,
        String host,
        Integer port,
        String username,
        String passwordMasked,
        String fromAddress,
        String fromName,
        String encryption,
        boolean isActive,
        Instant updatedAt) {

    /** 비밀번호 설정 여부만 마스킹 문자열로 노출. */
    private static final String MASK = "********";

    public static SmtpConfigResponse from(SmtpConfig c) {
        boolean hasPassword = c.getPasswordEnc() != null && !c.getPasswordEnc().isBlank();
        return new SmtpConfigResponse(
                c.getId(),
                c.getHost(),
                c.getPort(),
                c.getUsername(),
                hasPassword ? MASK : null,
                c.getFromAddress(),
                c.getFromName(),
                c.getEncryption(),
                Boolean.TRUE.equals(c.getIsActive()),
                c.getUpdatedAt());
    }
}
