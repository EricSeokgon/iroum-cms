package kr.co.ircp.cms.domain.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 공개 사이트(시민 사용자) 회원가입 요청 DTO.
 *
 * <p>관리자 콘솔의 {@link UserCreateRequest}와 달리 외부 비회원도 호출할 수 있는
 * {@code POST /api/v1/auth/register} 엔드포인트 전용 요청 모델이다.
 * 가입 직후 자동으로 MEMBER 역할이 부여되고 JWT가 발급된다.
 */
public record PublicRegisterRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, max = 100) String password,
        @NotBlank @Size(max = 100) String name
) {}
