package kr.co.ircp.cms.domain.auth.dto;

/**
 * 권한 목록 응답 DTO.
 *
 * <p>SPEC-CMS-002 REQ-AUTH-013 — GET /api/v1/permissions 응답.
 *
 * @param code        권한 코드 (e.g. USER:READ)
 * @param resource    리소스 유형 (e.g. USER)
 * @param action      액션 유형 (e.g. READ)
 * @param description 권한 설명
 */
public record PermissionSummary(
        String code,
        String resource,
        String action,
        String description
) {}
