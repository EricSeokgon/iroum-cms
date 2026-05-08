package kr.co.ircp.cms.domain.auth.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import kr.co.ircp.cms.domain.auth.serializer.EmailMaskSerializer;

import java.time.Instant;
import java.util.Set;

/**
 * 사용자 상세 DTO.
 *
 * <p>SPEC-CMS-002 REQ-AUTH-006 §6.3 — GET /api/v1/users/{id}, POST, PUT 응답.
 *
 * <p>REQ-PII-EMAIL-008 — email 필드에 EmailMaskSerializer 적용.
 * SUPER_ADMIN: 평문, 그 외: 마스킹. UserSelf DTO는 마스킹 미적용.
 */
public record UserDetail(
        long id,
        String uuid,
        String username,
        @JsonSerialize(using = EmailMaskSerializer.class)
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
