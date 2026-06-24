package kr.co.ircp.cms.domain.point.mapper;

import kr.co.ircp.cms.domain.point.entity.BbsPostLike;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Optional;

/**
 * 게시글 좋아요 MyBatis Mapper.
 * SPEC-CMS-POINTS-001 REQ-PNT-004~005
 */
@Mapper
public interface BbsPostLikeMapper {

    Optional<BbsPostLike> findByUserIdAndPostId(
            @Param("userId") Long userId,
            @Param("postId") Long postId);

    void insert(BbsPostLike like);

    void deleteByUserIdAndPostId(
            @Param("userId") Long userId,
            @Param("postId") Long postId);

    int countByPostId(@Param("postId") Long postId);
}
