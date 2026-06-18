package kr.co.ircp.cms.domain.policy.dispatch.worker;

import kr.co.ircp.cms.domain.policy.dispatch.dto.NotificationDispatchTargetWithUser;
import kr.co.ircp.cms.domain.policy.dispatch.entity.NotificationDispatchSchedule;
import kr.co.ircp.cms.domain.policy.dispatch.executor.DispatchChannelExecutor;
import kr.co.ircp.cms.domain.policy.dispatch.repository.NotificationDispatchScheduleMapper;
import kr.co.ircp.cms.domain.policy.dispatch.repository.NotificationDispatchTargetMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 알림 발송 워커 — 주기적으로 대기 예약을 채널 실행기로 발송한다.
 *
 * <p>SPEC-CMS-NOTI-EXT-001 — findPendingBatch(FOR UPDATE SKIP LOCKED)로 멀티 인스턴스 중복을 방지하고,
 * 예약의 1순위 채널에 맞는 실행기를 선택해 대상 목록을 발송한다.
 */
// @MX:WARN: [AUTO] NotificationDispatchWorker — @Scheduled 주기 실행 + @Transactional 배치 처리
// @MX:REASON: 단일 트랜잭션에서 다수 예약/대상을 처리하므로 장시간 락 유지 가능. SKIP LOCKED + 소량 배치(10)로 완화
// @MX:SPEC: SPEC-CMS-NOTI-EXT-001
@Component
@Slf4j
public class NotificationDispatchWorker {

    private static final int BATCH_SIZE = 10;

    private final NotificationDispatchScheduleMapper scheduleMapper;
    private final NotificationDispatchTargetMapper targetMapper;
    private final List<DispatchChannelExecutor> executors;

    public NotificationDispatchWorker(NotificationDispatchScheduleMapper scheduleMapper,
                                      NotificationDispatchTargetMapper targetMapper,
                                      List<DispatchChannelExecutor> executors) {
        this.scheduleMapper = scheduleMapper;
        this.targetMapper = targetMapper;
        this.executors = executors;
    }

    @Scheduled(fixedDelayString = "${dispatch.worker.fixed-delay-ms:60000}", scheduler = "dispatchScheduler")
    @Transactional
    public void process() {
        List<NotificationDispatchSchedule> pending = scheduleMapper.findPendingBatch(BATCH_SIZE);
        for (NotificationDispatchSchedule schedule : pending) {
            scheduleMapper.markAsDispatching(schedule.getId());

            DispatchChannelExecutor executor = resolveExecutor(schedule);
            if (executor == null) {
                log.warn("지원 채널 실행기 없음: scheduleId={}, channels={}",
                        schedule.getId(), schedule.getChannels());
                scheduleMapper.updateStatus(schedule.getId(), "FAILED");
                continue;
            }

            List<NotificationDispatchTargetWithUser> targets =
                    targetMapper.findPendingTargetsWithEmail(schedule.getId());
            try {
                executor.execute(schedule, targets);
                long failedCount = targets.stream()
                        .filter(t -> "FAILED".equals(t.getStatus()))
                        .count();
                boolean allFailed = !targets.isEmpty() && failedCount == targets.size();
                scheduleMapper.updateStatus(schedule.getId(), allFailed ? "FAILED" : "COMPLETED");
            } catch (Exception e) {
                log.error("발송 처리 실패: scheduleId={}", schedule.getId(), e);
                scheduleMapper.updateStatus(schedule.getId(), "FAILED");
            }
        }
    }

    /** 예약의 1순위 채널을 지원하는 실행기를 선택한다. */
    private DispatchChannelExecutor resolveExecutor(NotificationDispatchSchedule schedule) {
        List<String> channels = schedule.getChannels();
        if (channels == null || channels.isEmpty()) {
            return null;
        }
        String primary = channels.get(0);
        return executors.stream()
                .filter(e -> e.getSupportedChannel().equals(primary))
                .findFirst()
                .orElse(null);
    }
}
