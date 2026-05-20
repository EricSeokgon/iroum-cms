package kr.co.ircp.cms.domain.auth.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import kr.co.ircp.cms.domain.auth.serializer.EmailMaskSerializer;

import java.time.Instant;

/**
 * 사용자 목록 요약 DTO.
 *
 * <p>SPEC-CMS-002 REQ-AUTH-006 §6.3 — GET /api/v1/users 목록 항목.
 *
 * <p>REQ-PII-EMAIL-008 — email 필드에 EmailMaskSerializer 적용.
 * SUPER_ADMIN: 평문, 그 외: 마스킹. UserSelf DTO는 마스킹 미적용.
 */
public record UserSummary(
        Long id,
        String uuid,
        String username,
        @JsonSerialize(using = EmailMaskSerializer.class)
        String email,
        String name,
        String status,
        Instant lastLoginAt,
        Instant createdAt
) {}
