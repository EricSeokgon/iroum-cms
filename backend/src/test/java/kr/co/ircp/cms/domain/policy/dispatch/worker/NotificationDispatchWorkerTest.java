package kr.co.ircp.cms.domain.policy.dispatch.worker;

import kr.co.ircp.cms.domain.policy.dispatch.dto.NotificationDispatchTargetWithUser;
import kr.co.ircp.cms.domain.policy.dispatch.entity.NotificationDispatchSchedule;
import kr.co.ircp.cms.domain.policy.dispatch.executor.DispatchChannelExecutor;
import kr.co.ircp.cms.domain.policy.dispatch.repository.NotificationDispatchScheduleMapper;
import kr.co.ircp.cms.domain.policy.dispatch.repository.NotificationDispatchTargetMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * NotificationDispatchWorker 단위 테스트 (RED → GREEN).
 *
 * <p>SPEC-CMS-NOTI-EXT-001 — 대기 배치 조회 → 채널 실행기 선택 → 상태 전이.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationDispatchWorker (SPEC-CMS-NOTI-EXT-001)")
class NotificationDispatchWorkerTest {

    @Mock private NotificationDispatchScheduleMapper scheduleMapper;
    @Mock private NotificationDispatchTargetMapper targetMapper;
    @Mock private DispatchChannelExecutor emailExecutor;

    private NotificationDispatchSchedule schedule(Long id, String channel) {
        return NotificationDispatchSchedule.builder()
                .id(id).dispatchType("ANNOUNCEMENT")
                .channels(List.of(channel)).status("PENDING")
                .build();
    }

    @Test
    @DisplayName("process — 대기 건을 PROCESSING 마킹 후 채널 실행기로 발송, COMPLETED 전이")
    void process_dispatchesAndCompletes() {
        when(emailExecutor.getSupportedChannel()).thenReturn("EMAIL");
        var worker = new NotificationDispatchWorker(scheduleMapper, targetMapper, List.of(emailExecutor));

        var sch = schedule(1L, "EMAIL");
        when(scheduleMapper.findPendingBatch(anyInt())).thenReturn(List.of(sch));
        var sent = NotificationDispatchTargetWithUser.builder()
                .id(10L).scheduleId(1L).userId(7L).channel("EMAIL").status("SENT").build();
        when(targetMapper.findPendingTargetsWithEmail(1L)).thenReturn(List.of(sent));

        worker.process();

        verify(scheduleMapper).markAsDispatching(1L);
        verify(emailExecutor).execute(eq(sch), any());
        verify(scheduleMapper).updateStatus(1L, "COMPLETED");
    }

    @Test
    @DisplayName("process — 지원 실행기 없으면 FAILED 전이")
    void process_noExecutor_marksFailed() {
        when(emailExecutor.getSupportedChannel()).thenReturn("EMAIL");
        var worker = new NotificationDispatchWorker(scheduleMapper, targetMapper, List.of(emailExecutor));

        var sch = schedule(2L, "SMS"); // 지원 실행기 없음
        when(scheduleMapper.findPendingBatch(anyInt())).thenReturn(List.of(sch));

        worker.process();

        verify(scheduleMapper).markAsDispatching(2L);
        verify(scheduleMapper).updateStatus(2L, "FAILED");
    }
}
