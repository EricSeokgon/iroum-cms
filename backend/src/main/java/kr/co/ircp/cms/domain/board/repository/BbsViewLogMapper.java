package kr.co.ircp.cms.domain.board.repository;

import kr.co.ircp.cms.domain.board.entity.BbsViewLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 게시글 조회 이력 MyBatis 매퍼.
 * REQ-BOARD-002-D-3: view_log dedupe 후 view_count 증가
 */
@Mapper
public interface BbsViewLogMapper {

    /**
     * 중복 조회 여부 확인.
     * 동일 postId + (userId 또는 ipHash) 조합이 최근 1시간 이내 존재하면 true
     */
    boolean existsRecentView(
            @Param("postId") Long postId,
            @Param("userId") Long userId,
            @Param("ipHash") String ipHash
    );

    /** 조회 이력 삽입 */
    void insert(BbsViewLog viewLog);
}
