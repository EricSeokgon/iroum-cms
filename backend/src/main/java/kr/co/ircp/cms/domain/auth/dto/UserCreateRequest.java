package kr.co.ircp.cms.domain.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Set;

/**
 * 사용자 생성 요청 DTO.
 *
 * <p>SPEC-CMS-002 REQ-AUTH-006 §6.3 — POST /api/v1/users 요청 본문.
 * SUPER_ADMIN 전용.
 */
public record UserCreateRequest(

        /** 로그인 ID (3~50자, 공백 불가) */
        @NotBlank
        @Size(min = 3, max = 50)
        String username,

        /** 이메일 (공백 불가, RFC 5322 형식) */
        @NotBlank
        @Email
        String email,

        /** 평문 비밀번호 — PasswordPolicyService로 검증 후 BCrypt 해싱 */
        @NotBlank
        String password,

        /** 사용자 실명 (100자 이내) */
        @NotBlank
        @Size(max = 100)
        String name,

        /** 초기 계정 상태 (null이면 ACTIVE 기본값 적용) */
        String status,

        /** 부여할 역할 코드 집합 */
        Set<String> roleCodes
) {}
