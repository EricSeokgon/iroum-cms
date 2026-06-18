package kr.co.ircp.cms.domain.policy.dispatch.executor;

import kr.co.ircp.cms.domain.policy.dispatch.dto.NotificationDispatchTargetWithUser;
import kr.co.ircp.cms.domain.policy.dispatch.entity.NotificationDispatchSchedule;
import kr.co.ircp.cms.domain.policy.dispatch.repository.NotificationDispatchTargetMapper;
import kr.co.ircp.cms.domain.policy.subscription.repository.NotificationSubscriptionMapper;
import kr.co.ircp.cms.domain.security.pii.EmailEncryptionService;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * EmailDispatchExecutor 단위 테스트 (RED → GREEN).
 *
 * <p>SPEC-CMS-NOTI-EXT-001 — 옵트아웃 스킵 / 정상 발송 / 채널 식별.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EmailDispatchExecutor (SPEC-CMS-NOTI-EXT-001)")
class EmailDispatchExecutorTest {

    @Mock private JavaMailSender mailSender;
    @Mock private EmailEncryptionService emailEncryptionService;
    @Mock private NotificationSubscriptionMapper subscriptionMapper;
    @Mock private NotificationDispatchTargetMapper targetMapper;

    @InjectMocks private EmailDispatchExecutor executor;

    private NotificationDispatchSchedule schedule() {
        return NotificationDispatchSchedule.builder()
                .id(1L).dispatchType("ANNOUNCEMENT")
                .channels(List.of("EMAIL")).status("PROCESSING")
                .build();
    }

    private NotificationDispatchTargetWithUser target(Long id, Long userId) {
        return NotificationDispatchTargetWithUser.builder()
                .id(id).scheduleId(1L).userId(userId).channel("EMAIL").status("PENDING")
                .emailEncrypted(new byte[]{1}).emailIv(new byte[12])
                .emailTag(new byte[16]).keyVersion(1)
                .build();
    }

    @Test
    @DisplayName("getSupportedChannel — EMAIL 반환")
    void supportsEmail() {
        assertThat(executor.getSupportedChannel()).isEqualTo("EMAIL");
    }

    @Test
    @DisplayName("execute — 옵트인 대상에게 메일 발송 + SENT 마킹")
    void execute_sendsToOptedInTarget() {
        when(subscriptionMapper.isOptedIn(eq(7L), eq("EMAIL"), anyString())).thenReturn(true);
        when(emailEncryptionService.decrypt(any())).thenReturn("user@example.com");
        when(mailSender.createMimeMessage()).thenReturn(org.mockito.Mockito.mock(MimeMessage.class));

        executor.execute(schedule(), List.of(target(10L, 7L)));

        verify(mailSender).send(any(MimeMessage.class));
        verify(targetMapper).updateStatus(eq(10L), eq("SENT"), org.mockito.ArgumentMatchers.isNull());
    }

    @Test
    @DisplayName("execute — 옵트아웃 대상은 발송하지 않고 SKIPPED_OPTOUT 마킹")
    void execute_skipsOptedOutTarget() {
        when(subscriptionMapper.isOptedIn(eq(7L), eq("EMAIL"), anyString())).thenReturn(false);

        executor.execute(schedule(), List.of(target(10L, 7L)));

        verify(mailSender, never()).send(any(MimeMessage.class));
        verify(targetMapper).updateStatus(eq(10L), eq("SKIPPED_OPTOUT"), any());
    }
}
