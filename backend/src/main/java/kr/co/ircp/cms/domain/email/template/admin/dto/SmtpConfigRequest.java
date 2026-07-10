package kr.co.ircp.cms.domain.email.template.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * SMTP 설정 변경 요청 (REQ-ET-041/042).
 *
 * <p>password가 null/빈 문자열이면 기존 비밀번호를 유지한다(마스킹 응답 후 미변경).
 */
public record SmtpConfigRequest(
        @NotBlank @jakarta.validation.constraints.Size(max = 200) String host,
        @NotNull @Min(1) @Max(65535) Integer port,
        String username,
        String password,
        @NotBlank @jakarta.validation.constraints.Size(max = 200) String fromAddress,
        String fromName,
        String encryption) {

    public String encryptionOrDefault() {
        return (encryption == null || encryption.isBlank()) ? "STARTTLS" : encryption;
    }
}
