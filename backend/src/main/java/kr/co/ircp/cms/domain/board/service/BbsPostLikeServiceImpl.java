package kr.co.ircp.cms.domain.board.service;

import kr.co.ircp.cms.domain.board.entity.BbsPostLike;
import kr.co.ircp.cms.domain.board.mapper.BbsPostLikeMapper;
import kr.co.ircp.cms.domain.point.service.UserPointService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 게시글 좋아요 서비스 구현체.
 *
 * <p>SPEC-CMS-POINTS-001 REQ-PNT-004/008.
 *
 * <p>// @MX:NOTE: [AUTO] like()는 bbs_post_like UNIQUE 제약(user_id, post_id)으로 중복 좋아요를 차단한다.
 * 중복 시 DuplicateKeyException을 catch하여 false 반환(중복 적립 방지). 포인트 적립은 try-catch로 감싼 best-effort(REQ-PNT-008).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BbsPostLikeServiceImpl implements BbsPostLikeService {

    private final BbsPostLikeMapper bbsPostLikeMapper;
    private final UserPointService userPointService;

    @Override
    @Transactional
    public boolean like(Long postId, Long userId) {
        try {
            bbsPostLikeMapper.insert(BbsPostLike.builder()
                    .postId(postId)
                    .userId(userId)
                    .build());
        } catch (DuplicateKeyException e) {
            // 이미 좋아요한 게시글 — 중복 적립 없이 종료(REQ-PNT-004 WHILE 분기)
            return false;
        }

        // REQ-PNT-008: 포인트 적립 실패가 좋아요 등록을 롤백시키지 않도록 best-effort 처리
        try {
            userPointService.awardForLike(userId, postId);
        } catch (Exception e) {
            log.warn("포인트 적립 실패 (좋아요 - 게시글 ID: {}, 사용자 ID: {}): {}", postId, userId, e.getMessage());
        }
        return true;
    }

    @Override
    @Transactional
    public void unlike(Long postId, Long userId) {
        // REQ-PNT-004 IF 분기: 좋아요 취소 시 적립 포인트 회수 없음
        bbsPostLikeMapper.deleteByPostIdAndUserId(postId, userId);
    }

    @Override
    public boolean getLikeStatus(Long postId, Long userId) {
        return bbsPostLikeMapper.countByPostIdAndUserId(postId, userId) > 0;
    }
}
