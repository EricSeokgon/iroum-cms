package kr.co.ircp.cms.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * POST /api/v1/auth/verify/confirm 요청 DTO.
 *
 * <p>REQ-AUTH-017-D-2 — requestId(UUID) + 6자리 숫자 OTP 코드.
 */
public record VerifyConfirmRequest(
        @NotBlank @Pattern(regexp = "^[0-9a-fA-F-]{36}$") String requestId,
        @NotBlank @Pattern(regexp = "^\\d{6}$") String code
) {}
