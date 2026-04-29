package kr.co.ircp.cms.domain.auth.dto;

import java.time.Instant;
import java.util.Set;

/**
 * 사용자 상세 DTO.
 *
 * <p>SPEC-CMS-002 REQ-AUTH-006 §6.3 — GET /api/v1/users/{id}, POST, PUT 응답.
 */
public record UserDetail(
        long id,
        String uuid,
        String username,
        String email,
        String name,
        String status,
        int failCount,
        Instant lockedUntil,
        Instant lastLoginAt,
        Instant passwordChangedAt,
        Instant createdAt,
        Instant updatedAt,
        Set<String> roleCodes
) {}
