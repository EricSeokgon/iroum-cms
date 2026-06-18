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

    /**
     * 설문 발행 시 전체 활성 사용자에게 INAPP 알림 일괄 INSERT (REQ-SURVEY-011).
     *
     * <p>단일 INSERT...SELECT 로 활성(status='ACTIVE') 사용자 수만큼 행을 생성한다.
     *
     * // @MX:WARN: [AUTO] insertBatchForActiveSurveyOpen — 활성 사용자 전체 대상 대량 INSERT
     * // @MX:REASON: 사용자 수에 비례한 단일 트랜잭션 대량 쓰기 — 사용자 급증 시 성능·락 점유 주의
     * // @MX:SPEC: REQ-SURVEY-011
     */
    void insertBatchForActiveSurveyOpen(
            @Param("surveyId") Long surveyId,
            @Param("title") String title,
            @Param("body") String body
    );
}
