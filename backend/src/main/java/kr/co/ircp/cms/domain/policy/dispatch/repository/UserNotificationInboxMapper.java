package kr.co.ircp.cms.domain.policy.dispatch.repository;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 사용자 인앱 알림 수신함 매퍼 (user_notification_inbox).
 *
 * <p>SPEC-CMS-NOTI-EXT-001 — INAPP 채널 발송은 user_notification_inbox에만 적재한다
 * (admin_notification에는 절대 적재하지 않음).
 */
// @MX:SPEC: SPEC-CMS-NOTI-EXT-001
@Mapper
public interface UserNotificationInboxMapper {

    /** 인앱 알림 1건 적재. */
    void insertInbox(
            @Param("userId") Long userId,
            @Param("type") String type,
            @Param("title") String title,
            @Param("body") String body,
            @Param("refId") Long refId,
            @Param("refType") String refType);
}
