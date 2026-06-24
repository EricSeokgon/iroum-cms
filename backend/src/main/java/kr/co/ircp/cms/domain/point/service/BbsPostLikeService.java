package kr.co.ircp.cms.domain.point.service;

import kr.co.ircp.cms.domain.point.dto.LikeToggleResponse;

/**
 * 게시글 좋아요 서비스.
 * SPEC-CMS-POINTS-001 REQ-PNT-004~005
 */
public interface BbsPostLikeService {

    /** 좋아요 toggle. 이미 했으면 취소, 안 했으면 지급. */
    LikeToggleResponse toggle(Long postId, Long userId);
}
