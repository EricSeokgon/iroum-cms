package kr.co.ircp.cms.domain.point.dto;

/**
 * 포인트 정책 조회 응답 DTO.
 * SPEC-CMS-POINTS-001 REQ-PNT-001, REQ-PNT-006
 */
public record PointPolicyResponse(
        boolean enabled,
        int postCreated,
        int commentCreated,
        int likeGiven
) {}
