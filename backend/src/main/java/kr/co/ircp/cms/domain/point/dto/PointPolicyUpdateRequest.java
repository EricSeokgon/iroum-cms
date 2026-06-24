package kr.co.ircp.cms.domain.point.dto;

/**
 * 포인트 정책 수정 요청 DTO.
 * SPEC-CMS-POINTS-001 REQ-PNT-006
 */
public record PointPolicyUpdateRequest(
        Boolean enabled,
        Integer postCreated,
        Integer commentCreated,
        Integer likeGiven
) {}
