package kr.co.ircp.cms.domain.board.service;

/**
 * 설문 알림 서비스 인터페이스.
 * SPEC-CMS-SURVEY-001 REQ-SURVEY-011~016: 발행→시민 INAPP, 종료/한도→관리자 알림.
 *
 * <p>모든 메서드는 멱등(survey_notification_log UNIQUE) + best-effort(설문 트랜잭션과 분리) 보장.
 * 이메일/SMS/푸시는 호출하지 않으며 INAPP(user_notification_inbox)·관리자(admin_notification)만 발송한다.
 *
 * // @MX:ANCHOR: [AUTO] SurveyNotificationService — 설문 알림 진입점 (인터페이스 계약)
 * // @MX:REASON: SurveyServiceImpl(3개 호출 지점) + SurveyNotificationServiceImplTest 로 fan_in >= 3
 * // @MX:SPEC: REQ-SURVEY-011~016
 */
public interface SurveyNotificationService {

    /** 설문 발행(DRAFT→OPEN) 시 전체 활성 사용자에게 INAPP 알림 일괄 발송 (REQ-SURVEY-011). */
    void sendSurveyPublishedNotification(Long surveyId);

    /** 설문 종료(→CLOSED) 시 모든 관리자에게 운영 알림 발송 (REQ-SURVEY-012). */
    void sendSurveyClosedAdminNotification(Long surveyId);

    /** 응답 한도 도달 시 모든 관리자에게 운영 알림 발송 (REQ-SURVEY-013). */
    void sendResponseLimitAdminNotification(Long surveyId);
}
