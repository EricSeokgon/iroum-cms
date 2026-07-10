package kr.co.ircp.cms.domain.board.service;

import kr.co.ircp.cms.domain.board.entity.Survey;
import kr.co.ircp.cms.domain.board.entity.SurveyNotificationLog;
import kr.co.ircp.cms.domain.board.repository.SurveyMapper;
import kr.co.ircp.cms.domain.board.repository.SurveyNotificationLogMapper;
import kr.co.ircp.cms.domain.board.repository.UserNotificationInboxMapper;
import kr.co.ircp.cms.domain.notification.admin.repository.AdminNotificationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SurveyNotificationServiceImpl 단위 테스트 (RED→GREEN).
 * SPEC-CMS-SURVEY-001 REQ-SURVEY-011~016: INAPP/관리자 알림 + 멱등성.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SurveyNotificationService 단위 테스트 (REQ-SURVEY-011~016)")
class SurveyNotificationServiceImplTest {

    @Mock
    private SurveyNotificationLogMapper logMapper;
    @Mock
    private UserNotificationInboxMapper inboxMapper;
    @Mock
    private AdminNotificationMapper adminNotificationMapper;
    @Mock
    private SurveyMapper surveyMapper;

    private SurveyNotificationService service;

    @BeforeEach
    void setUp() {
        service = new SurveyNotificationServiceImpl(
                logMapper, inboxMapper, adminNotificationMapper, surveyMapper);
    }

    private Survey stubSurvey(long id) {
        Instant now = Instant.now();
        return Survey.builder()
                .id(id)
                .title("만족도 조사 " + id)
                .status("OPEN")
                .startAt(now.minusSeconds(3600))
                .endAt(now.plusSeconds(3600))
                .build();
    }

    // ─── 발행 → 시민 INAPP (REQ-SURVEY-011) ────────────────────────────────

    @Test
    @DisplayName("sendSurveyPublishedNotification — 로그 기록 + 전체 활성 사용자 INAPP 일괄 발송")
    void sendSurveyPublishedNotification_success() {
        when(surveyMapper.findById(1L)).thenReturn(Optional.of(stubSurvey(1L)));

        service.sendSurveyPublishedNotification(1L);

        verify(logMapper).insert(any(SurveyNotificationLog.class));
        verify(inboxMapper).insertBatchForActiveSurveyOpen(eq(1L), anyString(), anyString());
    }

    @Test
    @DisplayName("sendSurveyPublishedNotification — 중복 발송 시 DuplicateKeyException 차단 + INAPP 미발송")
    void sendSurveyPublishedNotification_idempotent() {
        doThrow(new DuplicateKeyException("uq_survey_notification_log"))
                .when(logMapper).insert(any(SurveyNotificationLog.class));

        assertThatCode(() -> service.sendSurveyPublishedNotification(1L))
                .doesNotThrowAnyException();

        verify(inboxMapper, never()).insertBatchForActiveSurveyOpen(any(), anyString(), anyString());
    }

    // ─── 종료 → 관리자 알림 (REQ-SURVEY-012) ───────────────────────────────

    @Test
    @DisplayName("sendSurveyClosedAdminNotification — 로그 기록 + 관리자 일괄 알림")
    void sendSurveyClosedAdminNotification_success() {
        when(surveyMapper.findById(2L)).thenReturn(Optional.of(stubSurvey(2L)));

        service.sendSurveyClosedAdminNotification(2L);

        verify(logMapper).insert(any(SurveyNotificationLog.class));
        verify(adminNotificationMapper).insertForAdminRoles(
                eq("SURVEY_CLOSED"), eq("INFO"), anyString(), anyString(), eq(2L));
    }

    @Test
    @DisplayName("sendSurveyClosedAdminNotification — 중복 발송 차단")
    void sendSurveyClosedAdminNotification_idempotent() {
        doThrow(new DuplicateKeyException("uq")).when(logMapper).insert(any());

        assertThatCode(() -> service.sendSurveyClosedAdminNotification(2L))
                .doesNotThrowAnyException();

        verify(adminNotificationMapper, never())
                .insertForAdminRoles(anyString(), anyString(), anyString(), anyString(), any());
    }

    // ─── 한도 도달 → 관리자 알림 (REQ-SURVEY-013) ──────────────────────────

    @Test
    @DisplayName("sendResponseLimitAdminNotification — 로그 기록 + 관리자 일괄 알림")
    void sendResponseLimitAdminNotification_success() {
        when(surveyMapper.findById(3L)).thenReturn(Optional.of(stubSurvey(3L)));

        service.sendResponseLimitAdminNotification(3L);

        verify(logMapper).insert(any(SurveyNotificationLog.class));
        verify(adminNotificationMapper).insertForAdminRoles(
                eq("SURVEY_RESPONSE_LIMIT"), eq("INFO"), anyString(), anyString(), eq(3L));
    }

    @Test
    @DisplayName("sendResponseLimitAdminNotification — 중복 발송 차단")
    void sendResponseLimitAdminNotification_idempotent() {
        doThrow(new DuplicateKeyException("uq")).when(logMapper).insert(any());

        assertThatCode(() -> service.sendResponseLimitAdminNotification(3L))
                .doesNotThrowAnyException();

        verify(adminNotificationMapper, never())
                .insertForAdminRoles(anyString(), anyString(), anyString(), anyString(), any());
        verify(surveyMapper, never()).findById(any());
    }

    // ─── 이메일 미발송 (REQ-SURVEY-016) — 본 서비스는 메일 의존성 자체가 없음 ──
    // 생성자에 JavaMailSender 가 주입되지 않음을 setUp()이 보장 (컴파일 타임 검증).
}
