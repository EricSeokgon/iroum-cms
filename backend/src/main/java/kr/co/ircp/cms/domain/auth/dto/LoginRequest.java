package kr.co.ircp.cms.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 로그인 요청 DTO.
 *
 * <p>SPEC-CMS-002 REQ-AUTH-001 — POST /api/v1/auth/login 요청 바디.
 */
public record LoginRequest(

    /** 로그인 ID */
    @NotBlank
    String username,

    /**
     * 평문 비밀번호 (8자 이상 — REQ-AUTH-004).
     *
     * <p>서버에서 BCrypt 검증 후 즉시 폐기.
     */
    @NotBlank
    @Size(min = 8)
    String password
) {}
