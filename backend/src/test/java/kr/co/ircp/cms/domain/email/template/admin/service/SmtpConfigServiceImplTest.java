package kr.co.ircp.cms.domain.email.template.admin.service;

import kr.co.ircp.cms.domain.email.template.admin.dto.SmtpConfigRequest;
import kr.co.ircp.cms.domain.email.template.admin.dto.SmtpConfigResponse;
import kr.co.ircp.cms.domain.email.template.admin.entity.SmtpConfig;
import kr.co.ircp.cms.domain.email.template.admin.repository.SmtpConfigMapper;
import kr.co.ircp.cms.domain.security.pii.EmailEncryptionService;
import kr.co.ircp.cms.domain.security.pii.EncryptedEmail;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SmtpConfigServiceImpl 단위 테스트 (REQ-ET-040/041/042).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SmtpConfigServiceImpl 단위 테스트 (REQ-ET-040/041)")
class SmtpConfigServiceImplTest {

    @Mock SmtpConfigMapper smtpConfigMapper;
    @Mock EmailEncryptionService emailEncryptionService;

    private final JavaMailSenderImpl mailSender = new JavaMailSenderImpl();

    private SmtpConfigServiceImpl service() {
        return new SmtpConfigServiceImpl(smtpConfigMapper, emailEncryptionService, mailSender);
    }

    @Test
    @DisplayName("AC-ET-013: 조회 시 비밀번호가 마스킹되어 노출된다")
    void getActive_masksPassword() {
        when(smtpConfigMapper.findActive()).thenReturn(Optional.of(SmtpConfig.builder()
                .id(1L).host("smtp.test").port(587).username("u")
                .passwordEnc("enc:enc:enc:1").fromAddress("from@test")
                .encryption("STARTTLS").isActive(true).build()));

        SmtpConfigResponse response = service().getActive();

        assertThat(response.passwordMasked()).isEqualTo("********");
    }

    @Test
    @DisplayName("AC-ET-012: 설정 변경 후 JavaMailSender가 재구성된다(재시작 없이)")
    void update_reconfiguresMailSender() {
        when(smtpConfigMapper.findActive()).thenReturn(Optional.empty());
        when(emailEncryptionService.encrypt(any()))
                .thenReturn(new EncryptedEmail(new byte[]{1}, new byte[12], new byte[16], 1));

        var request = new SmtpConfigRequest(
                "smtp.new.host", 2525, "newuser", "newpass", "from@new", "발신", "SSL");
        service().update(request, 5L);

        verify(smtpConfigMapper).insert(any(SmtpConfig.class));
        assertThat(mailSender.getHost()).isEqualTo("smtp.new.host");
        assertThat(mailSender.getPort()).isEqualTo(2525);
        assertThat(mailSender.getUsername()).isEqualTo("newuser");
    }

    @Test
    @DisplayName("비밀번호 미입력 시 기존 암호화 값을 유지한다")
    void update_keepsExistingPasswordWhenBlank() {
        when(smtpConfigMapper.findActive()).thenReturn(Optional.of(SmtpConfig.builder()
                .id(1L).host("old").port(587).passwordEnc("oldEnc")
                .fromAddress("from@old").encryption("STARTTLS").isActive(true).build()));

        var request = new SmtpConfigRequest(
                "smtp.new", 587, "u", null, "from@new", null, "STARTTLS");
        service().update(request, 5L);

        verify(smtpConfigMapper).update(any(SmtpConfig.class));
        // 비밀번호 미입력 → 암호화 호출 없음
        verify(emailEncryptionService, org.mockito.Mockito.never()).encrypt(any());
    }
}
