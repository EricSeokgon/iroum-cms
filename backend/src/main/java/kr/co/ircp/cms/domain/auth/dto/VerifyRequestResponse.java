package kr.co.ircp.cms.domain.auth.dto;

import java.time.Instant;

/**
 * POST /api/v1/auth/verify/request 응답 DTO.
 *
 * <p>REQ-AUTH-017-D-1 — requestId, 만료 시각, 쿨다운 초를 반환.
 */
public record VerifyRequestResponse(
        String requestId,
        Instant expiresAt,
        long cooldownSeconds
) {}
