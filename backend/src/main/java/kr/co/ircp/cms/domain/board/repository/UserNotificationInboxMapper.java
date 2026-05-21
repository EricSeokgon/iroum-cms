package kr.co.ircp.cms.domain.board.repository;

import kr.co.ircp.cms.domain.board.entity.UserNotificationInbox;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 사용자 인앱 알림 수신함 MyBatis 매퍼.
 * REQ-BOARD-014-D-2
 */
@Mapper
public interface UserNotificationInboxMapper {

    /** 알림 항목 삽입 (id 자동 생성). */
    void insert(UserNotificationInbox inbox);

    /** 알림 읽음 처리. */
    void markRead(@Param("id") Long id, @Param("userId") Long userId);
}
