package kr.co.ircp.cms.domain.board.repository;

import kr.co.ircp.cms.domain.board.entity.BbsPostHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 게시글 변경 이력 MyBatis 매퍼.
 * REQ-BOARD-002-D-4: 수정 직전 본문 보존
 */
@Mapper
public interface BbsPostHistoryMapper {

    /** 게시글 변경 이력 목록 조회 (버전 역순) */
    List<BbsPostHistory> findByPostId(@Param("postId") Long postId);

    /** 이력 삽입 */
    void insert(BbsPostHistory history);

    /** 다음 버전 번호 조회 */
    int nextVersionByPostId(@Param("postId") Long postId);
}
