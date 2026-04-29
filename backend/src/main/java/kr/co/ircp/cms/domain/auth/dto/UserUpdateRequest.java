package kr.co.ircp.cms.domain.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

import java.util.Set;

/**
 * 사용자 수정 요청 DTO.
 *
 * <p>SPEC-CMS-002 REQ-AUTH-006 §6.3 — PUT /api/v1/users/{id} 요청 본문.
 * 비밀번호는 별도 API(/password-reset)로 변경하므로 제외.
 * SUPER_ADMIN 전용.
 */
public record UserUpdateRequest(

        /** 이메일 (RFC 5322 형식, null이면 변경 없음) */
        @Email
        String email,

        /** 사용자 실명 (100자 이내, null이면 변경 없음) */
        @Size(max = 100)
        String name,

        /** 계정 상태 (null이면 변경 없음) */
        String status,

        /** 역할 코드 집합 (null이면 변경 없음, 빈 Set이면 전체 제거) */
        Set<String> roleCodes
) {}
