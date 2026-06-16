package kr.co.ircp.cms.domain.email.template.admin.integration;

import kr.co.ircp.cms.domain.auth.entity.VerificationPurpose;
import kr.co.ircp.cms.domain.auth.service.EmailServiceImpl;
import kr.co.ircp.cms.domain.email.template.admin.dto.RenderResult;
import kr.co.ircp.cms.domain.email.template.admin.exception.TemplateRenderException;
import kr.co.ircp.cms.domain.email.template.admin.service.EmailTemplateResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * EmailServiceImpl 템플릿 연동 회귀 테스트 (T8 CRITICAL).
 *
 * <p>SPEC-CMS-EMAIL-TEMPLATE-001 REQ-ET-032/033 — 템플릿 우선, 미존재/실패 시 하드코딩 fallback,
 * 예외 비전파(기존 동작 보존).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EmailServiceImpl 템플릿 fallback 회귀 (REQ-ET-032/033)")
class EmailServiceTemplateFallbackTest {

    @Mock
    JavaMailSender mailSender;

    @Mock
    EmailTemplateResolver templateResolver;

    @InjectMocks
    EmailServiceImpl emailService;

    @Test
    @DisplayName("AC-ET-011: 템플릿 미존재 시 기존 하드코딩 OTP 메일이 발송된다")
    void otp_fallsBackToHardcodedWhenNoTemplate() {
        when(templateResolver.resolveAndRender(anyString(), anyString(), any()))
                .thenReturn(Optional.empty());

        emailService.sendOtp("user@example.com", "123456", VerificationPurpose.SIGNUP);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage sent = captor.getValue();
        assertThat(sent.getSubject()).isEqualTo("[iroum-cms] 본인인증 코드");
        assertThat(sent.getText()).contains("123456");
    }

    @Test
    @DisplayName("AC-ET-010: 템플릿 존재 시 렌더링 결과가 발송에 사용된다")
    void otp_usesRenderedTemplateWhenPresent() {
        when(templateResolver.resolveAndRender(anyString(), anyString(), any()))
                .thenReturn(Optional.of(new RenderResult(
                        "맞춤 제목", "<p>HTML</p>", "맞춤 평문 123456")));

        emailService.sendOtp("user@example.com", "123456", VerificationPurpose.SIGNUP);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage sent = captor.getValue();
        assertThat(sent.getSubject()).isEqualTo("맞춤 제목");
        assertThat(sent.getText()).isEqualTo("맞춤 평문 123456");
    }

    @Test
    @DisplayName("AC-ET-011: 리졸버가 빈 결과를 주면(렌더링 내부 실패 흡수) 하드코딩으로 발송된다")
    void otp_resolverEmptyOnRenderFailureStillSends() {
        // 리졸버는 내부에서 렌더링 실패를 흡수해 Optional.empty()를 반환하는 계약.
        when(templateResolver.resolveAndRender(anyString(), anyString(), any()))
                .thenReturn(Optional.empty());

        assertThatCode(() ->
                emailService.sendOtp("user@example.com", "999000", VerificationPurpose.SIGNUP))
                .doesNotThrowAnyException();

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        assertThat(captor.getValue().getText()).contains("999000");
    }

    @Test
    @DisplayName("비밀번호 재설정도 템플릿 미존재 시 하드코딩 fallback")
    void passwordReset_fallsBack() {
        when(templateResolver.resolveAndRender(anyString(), anyString(), any()))
                .thenReturn(Optional.empty());

        emailService.sendPasswordResetNotice("user@example.com");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        assertThat(captor.getValue().getSubject()).isEqualTo("[iroum-cms] 비밀번호 재설정 완료 안내");
    }
}
