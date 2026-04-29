package kr.co.ircp.cms.domain.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * POST /api/v1/auth/verify/request 요청 DTO.
 *
 * <p>REQ-AUTH-017-D-1 — 본인인증 OTP 발송 요청.
 */
public record VerifyRequestRequest(
        @NotBlank String channel,
        @NotBlank @Email String target,
        @NotBlank String purpose
) {}
