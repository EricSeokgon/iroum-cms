package kr.co.ircp.cms.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * POST /api/v1/auth/password/reset-confirm 요청 DTO.
 *
 * <p>REQ-AUTH-017-D-4 — verifiedToken + 새 비밀번호로 비밀번호 재설정.
 */
public record PasswordResetConfirmDto(
        @NotBlank String verifiedToken,
        @NotBlank String newPassword
) {}
