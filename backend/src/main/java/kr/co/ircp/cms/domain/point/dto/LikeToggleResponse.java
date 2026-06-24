package kr.co.ircp.cms.domain.point.dto;

/**
 * 좋아요 toggle 응답 DTO.
 * SPEC-CMS-POINTS-001 REQ-PNT-004~005
 */
public record LikeToggleResponse(boolean liked, int likeCount) {}
