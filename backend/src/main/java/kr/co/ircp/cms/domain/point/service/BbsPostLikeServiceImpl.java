package kr.co.ircp.cms.domain.point.service;

import kr.co.ircp.cms.domain.point.dto.LikeToggleResponse;
import kr.co.ircp.cms.domain.point.entity.BbsPostLike;
import kr.co.ircp.cms.domain.point.mapper.BbsPostLikeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 게시글 좋아요 서비스 구현체.
 * SPEC-CMS-POINTS-001 REQ-PNT-004~005
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BbsPostLikeServiceImpl implements BbsPostLikeService {

    private final BbsPostLikeMapper likeMapper;
    private final UserPointService pointService;

    @Override
    @Transactional
    public LikeToggleResponse toggle(Long postId, Long userId) {
        boolean liked;
        if (likeMapper.findByUserIdAndPostId(userId, postId).isPresent()) {
            likeMapper.deleteByUserIdAndPostId(userId, postId);
            liked = false;
        } else {
            likeMapper.insert(BbsPostLike.builder()
                    .userId(userId)
                    .postId(postId)
                    .build());
            liked = true;
            // best-effort 포인트 지급 (REQ-PNT-008)
            try {
                pointService.awardPoints(userId, "LIKE_GIVEN", "POST", postId);
            } catch (Exception e) {
                log.warn("좋아요 포인트 지급 실패 userId={} postId={}: {}", userId, postId, e.getMessage());
            }
        }
        int likeCount = likeMapper.countByPostId(postId);
        return new LikeToggleResponse(liked, likeCount);
    }
}
