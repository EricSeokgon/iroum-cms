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
 *
 * <p>SPEC-CMS-USER-APPROVAL-002 REQ-UA2-002 — {@code verifiedToken} 은 가입 전 이메일 인증
 * (OTP)으로 발급받은 단기 토큰이다. {@code REGISTRATION_EMAIL_VERIFY_REQUIRED=true} 일 때만
 * 필수이며, 기본(false)에서는 누락되어도 기존 가입 동작이 유지된다(회귀 방지). 따라서 nullable.
 */
public record PublicRegisterRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, max = 100) String password,
        @NotBlank @Size(max = 100) String name,
        String verifiedToken
) {}
