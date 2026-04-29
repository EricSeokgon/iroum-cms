package kr.co.ircp.cms.domain.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * POST /api/v1/auth/password/reset-request 요청 DTO.
 *
 * <p>REQ-AUTH-017-D-3 — 비밀번호 재설정 이메일 발송 요청.
 * 사용자 존재 여부를 응답으로 노출하지 않는다 (enumeration 방지).
 */
public record PasswordResetRequestDto(
        @NotBlank @Email String email
) {}
