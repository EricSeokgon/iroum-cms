package kr.co.ircp.cms.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 관리자 비밀번호 강제 초기화 요청 DTO.
 *
 * <p>SUPER_ADMIN이 대상 사용자의 비밀번호를 직접 변경한다.
 * POST /api/v1/users/{id}/reset-password 요청 바디.
 */
public record AdminPasswordResetRequest(

        /**
         * 새 비밀번호 (정책: 8자 이상, 3종류 이상).
         */
        @NotBlank(message = "새 비밀번호는 필수입니다")
        String newPassword
) {}
