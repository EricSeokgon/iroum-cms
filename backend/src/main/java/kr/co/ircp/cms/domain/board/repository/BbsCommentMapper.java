package kr.co.ircp.cms.domain.board.repository;

import kr.co.ircp.cms.domain.board.entity.BbsComment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * 댓글 MyBatis 매퍼.
 * REQ-BOARD-003: 댓글 CRUD (1-depth 계층형)
 */
@Mapper
public interface BbsCommentMapper {

    /** 게시글 댓글 목록 조회 (depth 순 정렬) */
    List<BbsComment> findByPostId(@Param("postId") Long postId);

    /** ID로 단건 조회 */
    Optional<BbsComment> findById(@Param("id") Long id);

    /** 댓글 삽입 */
    void insert(BbsComment comment);

    /** 댓글 수정 */
    int update(BbsComment comment);

    /** 댓글 삭제 (소프트 삭제: status=DELETED) */
    int deleteById(@Param("id") Long id);
}
