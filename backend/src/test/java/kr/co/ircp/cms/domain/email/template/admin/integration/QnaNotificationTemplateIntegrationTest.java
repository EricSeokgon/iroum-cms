package kr.co.ircp.cms.domain.email.template.admin.integration;

import kr.co.ircp.cms.domain.auth.entity.User;
import kr.co.ircp.cms.domain.auth.repository.UserMapper;
import kr.co.ircp.cms.domain.board.entity.Qna;
import kr.co.ircp.cms.domain.board.entity.QnaNotificationLog;
import kr.co.ircp.cms.domain.board.repository.QnaMapper;
import kr.co.ircp.cms.domain.board.repository.QnaNotificationLogMapper;
import kr.co.ircp.cms.domain.board.repository.QnaNotificationOptoutMapper;
import kr.co.ircp.cms.domain.board.repository.UserNotificationInboxMapper;
import kr.co.ircp.cms.domain.board.service.QnaNotificationServiceImpl;
import kr.co.ircp.cms.domain.email.template.admin.dto.RenderResult;
import kr.co.ircp.cms.domain.email.template.admin.service.EmailTemplateResolver;
import kr.co.ircp.cms.domain.security.pii.EmailEncryptionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * QnaNotificationServiceImpl 템플릿 연동 회귀 테스트 (T8 CRITICAL).
 *
 * <p>SPEC-CMS-EMAIL-TEMPLATE-001 REQ-ET-031/033 — QNA_ANSWER 템플릿 우선,
 * 미존재 시 하드코딩 fallback.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("QnaNotification 템플릿 연동 회귀 (REQ-ET-031/033)")
class QnaNotificationTemplateIntegrationTest {

    @Mock QnaNotificationLogMapper logMapper;
    @Mock QnaNotificationOptoutMapper optoutMapper;
    @Mock UserMapper userMapper;
    @Mock QnaMapper qnaMapper;
    @Mock UserNotificationInboxMapper inboxMapper;
    @Mock JavaMailSender mailSender;
    @Mock EmailEncryptionService emailEncryptionService;
    @Mock EmailTemplateResolver templateResolver;

    @InjectMocks
    QnaNotificationServiceImpl service;

    private static final Long QNA_ID = 100L;
    private static final Long QUESTIONER_ID = 1L;
    private static final Long ANSWERER_ID = 2L;

    private void stubEmailPath() {
        when(optoutMapper.existsByUserAndChannel(QUESTIONER_ID, "EMAIL")).thenReturn(false);
        Qna qna = Qna.builder().id(QNA_ID).title("샘플 질문").build();
        when(qnaMapper.findById(QNA_ID)).thenReturn(Optional.of(qna));
        User user = new User();
        user.setId(QUESTIONER_ID);
        user.setEmail("user@example.com");
        when(userMapper.findById(QUESTIONER_ID)).thenReturn(Optional.of(user));
    }

    @Test
    @DisplayName("AC-ET-011: QNA_ANSWER 템플릿 미존재 시 하드코딩 본문이 발송된다")
    void fallbackToHardcodedWhenNoTemplate() {
        stubEmailPath();
        when(templateResolver.resolveAndRender(eq("QNA_ANSWER"), anyString(), any()))
                .thenReturn(Optional.empty());

        service.notifyAnswered(QNA_ID, QUESTIONER_ID, ANSWERER_ID);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        assertThat(captor.getValue().getSubject()).contains("Q&A 답변이 등록되었습니다");
        assertThat(captor.getValue().getText()).contains("샘플 질문");
    }

    @Test
    @DisplayName("AC-ET-010: QNA_ANSWER 템플릿 존재 시 렌더링 결과로 발송된다")
    void usesRenderedTemplateWhenPresent() {
        stubEmailPath();
        when(templateResolver.resolveAndRender(eq("QNA_ANSWER"), anyString(), any()))
                .thenReturn(Optional.of(new RenderResult("렌더 제목", "<p>본문</p>", "렌더 평문 본문")));

        service.notifyAnswered(QNA_ID, QUESTIONER_ID, ANSWERER_ID);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        assertThat(captor.getValue().getSubject()).isEqualTo("렌더 제목");
        assertThat(captor.getValue().getText()).isEqualTo("렌더 평문 본문");
    }
}
