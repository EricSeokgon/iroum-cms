package kr.co.ircp.cms.domain.board.repository;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * Q&A 답변 알림 옵트아웃 MyBatis 매퍼.
 * REQ-BOARD-014-D-4: 사용자·채널별 옵트아웃 등록·조회·해제
 */
@Mapper
public interface QnaNotificationOptoutMapper {

    /** 옵트아웃 등록 (ON CONFLICT DO UPDATE). */
    void upsert(@Param("userId") Long userId, @Param("channel") String channel);

    /** 특정 사용자·채널 옵트아웃 여부 확인. */
    boolean existsByUserAndChannel(@Param("userId") Long userId, @Param("channel") String channel);

    /** 옵트아웃 해제. */
    void delete(@Param("userId") Long userId, @Param("channel") String channel);
}
