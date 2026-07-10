package kr.co.ircp.cms.domain.policy.dispatch.executor;

import kr.co.ircp.cms.domain.policy.dispatch.dto.NotificationDispatchTargetWithUser;
import kr.co.ircp.cms.domain.policy.dispatch.entity.NotificationDispatchSchedule;
import kr.co.ircp.cms.domain.policy.dispatch.repository.NotificationDispatchTargetMapper;
import kr.co.ircp.cms.domain.policy.dispatch.repository.PolicyDispatchInboxMapper;
import kr.co.ircp.cms.domain.policy.subscription.repository.NotificationSubscriptionMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * InappDispatchExecutor 단위 테스트 (RED → GREEN).
 *
 * <p>SPEC-CMS-NOTI-EXT-001 — user_notification_inbox에만 적재 / 옵트아웃 스킵 / 채널 식별.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("InappDispatchExecutor (SPEC-CMS-NOTI-EXT-001)")
class InappDispatchExecutorTest {

    @Mock private PolicyDispatchInboxMapper inboxMapper;
    @Mock private NotificationSubscriptionMapper subscriptionMapper;
    @Mock private NotificationDispatchTargetMapper targetMapper;

    @InjectMocks private InappDispatchExecutor executor;

    private NotificationDispatchSchedule schedule() {
        return NotificationDispatchSchedule.builder()
                .id(1L).policyId(55L).dispatchType("ANNOUNCEMENT")
                .channels(List.of("INAPP")).status("PROCESSING")
                .build();
    }

    private NotificationDispatchTargetWithUser target(Long id, Long userId) {
        return NotificationDispatchTargetWithUser.builder()
                .id(id).scheduleId(1L).userId(userId).channel("INAPP").status("PENDING")
                .build();
    }

    @Test
    @DisplayName("getSupportedChannel — INAPP 반환")
    void supportsInapp() {
        assertThat(executor.getSupportedChannel()).isEqualTo("INAPP");
    }

    @Test
    @DisplayName("execute — 옵트인 대상에게 inbox 적재 + SENT 마킹")
    void execute_insertsInboxForOptedIn() {
        when(subscriptionMapper.isOptedIn(eq(7L), eq("INAPP"), anyString())).thenReturn(true);

        executor.execute(schedule(), List.of(target(10L, 7L)));

        verify(inboxMapper).insertInbox(eq(7L), anyString(), anyString(), any(), anyLong(), anyString());
        verify(targetMapper).updateStatus(eq(10L), eq("SENT"), org.mockito.ArgumentMatchers.isNull());
    }

    @Test
    @DisplayName("execute — 옵트아웃 대상은 inbox 미적재 + SKIPPED_OPTOUT 마킹")
    void execute_skipsOptedOut() {
        when(subscriptionMapper.isOptedIn(eq(7L), eq("INAPP"), anyString())).thenReturn(false);

        executor.execute(schedule(), List.of(target(10L, 7L)));

        verify(inboxMapper, never()).insertInbox(anyLong(), anyString(), anyString(), any(), any(), anyString());
        verify(targetMapper).updateStatus(eq(10L), eq("SKIPPED_OPTOUT"), any());
    }
}
