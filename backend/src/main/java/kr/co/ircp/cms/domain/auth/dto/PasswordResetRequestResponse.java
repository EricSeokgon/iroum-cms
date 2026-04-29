package kr.co.ircp.cms.domain.auth.dto;

/**
 * POST /api/v1/auth/password/reset-request 응답 DTO.
 *
 * <p>REQ-AUTH-017-D-3 — 사용자 존재 여부와 무관하게 항상 동일 메시지를 반환한다.
 */
public record PasswordResetRequestResponse(String message) {}
