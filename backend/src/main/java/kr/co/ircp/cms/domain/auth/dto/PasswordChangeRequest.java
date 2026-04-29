package kr.co.ircp.cms.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 비밀번호 변경 요청 DTO.
 *
 * <p>SPEC-CMS-002 REQ-AUTH-009 — 현재 비밀번호 확인 후 새 비밀번호로 변경.
 * POST /api/v1/auth/password/change 요청 바디.
 */
public record PasswordChangeRequest(

        /**
         * 현재 비밀번호 (본인 확인용).
         */
        @NotBlank(message = "현재 비밀번호는 필수입니다")
        String currentPassword,

        /**
         * 변경할 새 비밀번호 (정책: 8자 이상, 3종류 이상).
         */
        @NotBlank(message = "새 비밀번호는 필수입니다")
        String newPassword
) {}
