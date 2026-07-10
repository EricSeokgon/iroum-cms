package kr.co.ircp.cms.domain.board.service;

import kr.co.ircp.cms.domain.board.entity.Survey;
import kr.co.ircp.cms.domain.board.entity.SurveyNotificationLog;
import kr.co.ircp.cms.domain.board.repository.SurveyMapper;
import kr.co.ircp.cms.domain.board.repository.SurveyNotificationLogMapper;
import kr.co.ircp.cms.domain.board.repository.UserNotificationInboxMapper;
import kr.co.ircp.cms.domain.notification.admin.repository.AdminNotificationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 설문 알림 서비스 구현체.
 * SPEC-CMS-SURVEY-001 REQ-SURVEY-011~016.
 *
 * <p>멱등성: survey_notification_log UNIQUE(survey_id, type) → DuplicateKeyException 캐치 후 종료.
 * 채널: INAPP(user_notification_inbox) + 관리자(admin_notification)만. 이메일/SMS/푸시 미발송 (REQ-SURVEY-016).
 *
 * // @MX:NOTE: [AUTO] 모든 발송 메서드는 멱등 로그 INSERT 선행 → DuplicateKeyException 시 조용히 종료.
 * //                  설문 트랜잭션과 분리되어 호출되며(SurveyServiceImpl 의 try-catch), 본 서비스는 INAPP/관리자만 발송한다.
 * // @MX:SPEC: REQ-SURVEY-011~016
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class SurveyNotificationServiceImpl implements SurveyNotificationService {

    private static final String TYPE_OPENED = "SURVEY_OPENED";
    private static final String TYPE_CLOSED = "SURVEY_CLOSED";
    private static final String TYPE_LIMIT = "SURVEY_RESPONSE_LIMIT";

    private final SurveyNotificationLogMapper logMapper;
    private final UserNotificationInboxMapper inboxMapper;
    private final AdminNotificationMapper adminNotificationMapper;
    private final SurveyMapper surveyMapper;

    @Override
    public void sendSurveyPublishedNotification(Long surveyId) {
        if (!recordLog(surveyId, TYPE_OPENED)) {
            return;
        }
        Survey survey = surveyMapper.findById(surveyId).orElse(null);
        String title = "새 설문: " + (survey != null ? survey.getTitle() : "");
        String body = "새로운 설문이 시작되었습니다. 참여해 주세요.";
        inboxMapper.insertBatchForActiveSurveyOpen(surveyId, title, body);
    }

    @Override
    public void sendSurveyClosedAdminNotification(Long surveyId) {
        if (!recordLog(surveyId, TYPE_CLOSED)) {
            return;
        }
        Survey survey = surveyMapper.findById(surveyId).orElse(null);
        adminNotificationMapper.insertForAdminRoles(
                TYPE_CLOSED, "INFO",
                "설문 종료: " + (survey != null ? survey.getTitle() : ""),
                "설문이 종료되었습니다. 결과를 확인하세요.",
                surveyId);
    }

    @Override
    public void sendResponseLimitAdminNotification(Long surveyId) {
        if (!recordLog(surveyId, TYPE_LIMIT)) {
            return;
        }
        Survey survey = surveyMapper.findById(surveyId).orElse(null);
        adminNotificationMapper.insertForAdminRoles(
                TYPE_LIMIT, "INFO",
                "설문 응답 한도 도달: " + (survey != null ? survey.getTitle() : ""),
                "최대 응답 수에 도달했습니다.",
                surveyId);
    }

    /**
     * 멱등 로그 기록. 신규 기록이면 true, 이미 발송된 적이 있으면(DuplicateKeyException) false.
     */
    private boolean recordLog(Long surveyId, String type) {
        try {
            logMapper.insert(SurveyNotificationLog.builder()
                    .surveyId(surveyId)
                    .type(type)
                    .status("SENT")
                    .build());
            return true;
        } catch (DuplicateKeyException e) {
            log.warn("설문 알림 중복 발송 차단: surveyId={}, type={}", surveyId, type);
            return false;
        }
    }
}
