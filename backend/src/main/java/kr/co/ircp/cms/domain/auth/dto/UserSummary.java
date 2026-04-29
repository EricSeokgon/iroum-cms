package kr.co.ircp.cms.domain.auth.dto;

import java.time.Instant;

/**
 * 사용자 목록 요약 DTO.
 *
 * <p>SPEC-CMS-002 REQ-AUTH-006 §6.3 — GET /api/v1/users 목록 항목.
 */
public record UserSummary(
        long id,
        String uuid,
        String username,
        String email,
        String name,
        String status,
        Instant lastLoginAt,
        Instant createdAt
) {}
