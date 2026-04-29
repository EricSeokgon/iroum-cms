package kr.co.ircp.cms.domain.auth.dto;

import java.time.Instant;
import java.util.Map;

/**
 * 조직 변경 이력 항목 DTO.
 *
 * <p>SPEC-CMS-002 REQ-AUTH-014-D-4 — GET /api/v1/organizations/{id}/history 응답.
 */
public record OrganizationHistoryEntry(
        long id,
        long orgId,
        int version,
        Map<String, Object> snapshot,
        Long changedBy,
        Instant changedAt,
        String changeSummary
) {}
