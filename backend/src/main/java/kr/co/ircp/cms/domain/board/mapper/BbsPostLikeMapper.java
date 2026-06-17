package kr.co.ircp.cms.domain.board.mapper;

import kr.co.ircp.cms.domain.board.entity.BbsPostLike;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 게시글 좋아요 MyBatis Mapper.
 *
 * <p>SPEC-CMS-POINTS-001 REQ-PNT-004 — UNIQUE(user_id, post_id) 위반 시
 * {@code insert}는 DuplicateKeyException을 던지며, 호출 측이 이를 catch하여 중복 적립을 건너뛴다.
 */
@Mapper
public interface BbsPostLikeMapper {

    /** 좋아요 1건 생성. UNIQUE 제약 위반 시 DuplicateKeyException. */
    void insert(BbsPostLike like);

    /** 좋아요 보유 여부(0 또는 1). */
    int countByPostIdAndUserId(@Param("postId") Long postId, @Param("userId") Long userId);

    /** 좋아요 취소(삭제). 포인트 회수 없음(REQ-PNT-004). */
    void deleteByPostIdAndUserId(@Param("postId") Long postId, @Param("userId") Long userId);
}
