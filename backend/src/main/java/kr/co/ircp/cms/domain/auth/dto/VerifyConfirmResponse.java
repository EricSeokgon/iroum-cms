package kr.co.ircp.cms.domain.auth.dto;

/**
 * POST /api/v1/auth/verify/confirm 응답 DTO.
 *
 * <p>REQ-AUTH-017-D-2 — verifiedToken(5분 유효) + purpose 반환.
 */
public record VerifyConfirmResponse(
        String verifiedToken,
        String purpose
) {}
