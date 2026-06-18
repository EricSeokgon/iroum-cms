package kr.co.ircp.cms.domain.policy.dispatch.executor;

import kr.co.ircp.cms.domain.policy.dispatch.dto.NotificationDispatchTargetWithUser;
import kr.co.ircp.cms.domain.policy.dispatch.entity.NotificationDispatchSchedule;
import kr.co.ircp.cms.domain.policy.dispatch.repository.NotificationDispatchTargetMapper;
import kr.co.ircp.cms.domain.policy.dispatch.repository.UserNotificationInboxMapper;
import kr.co.ircp.cms.domain.policy.subscription.repository.NotificationSubscriptionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * INAPP 채널 발송 실행기.
 *
 * <p>SPEC-CMS-NOTI-EXT-001 — user_notification_inbox에만 적재한다(admin_notification 금지).
 * 옵트아웃 대상은 SKIPPED_OPTOUT, 실패는 FAILED로 마킹한다.
 */
// @MX:NOTE: [AUTO] InappDispatchExecutor — INAPP 발송은 user_notification_inbox 전용 (admin_notification 미사용)
// @MX:SPEC: SPEC-CMS-NOTI-EXT-001
@Component
@RequiredArgsConstructor
@Slf4j
public class InappDispatchExecutor implements DispatchChannelExecutor {

    private final UserNotificationInboxMapper inboxMapper;
    private final NotificationSubscriptionMapper subscriptionMapper;
    private final NotificationDispatchTargetMapper targetMapper;

    @Override
    public String getSupportedChannel() {
        return "INAPP";
    }

    @Override
    public void execute(NotificationDispatchSchedule schedule,
                        List<NotificationDispatchTargetWithUser> targets) {
        String category = categoryFor(schedule.getDispatchType());
        String type = schedule.getDispatchType() != null ? schedule.getDispatchType() : "ANNOUNCEMENT";
        String title = "[이루움 CMS] 알림";
        String body = "새로운 알림이 도착했습니다.";

        for (NotificationDispatchTargetWithUser target : targets) {
            try {
                if (!subscriptionMapper.isOptedIn(target.getUserId(), "INAPP", category)) {
                    targetMapper.updateStatus(target.getId(), "SKIPPED_OPTOUT", "수신 거부");
                    continue;
                }
                inboxMapper.insertInbox(
                        target.getUserId(), type, title, body,
                        schedule.getPolicyId(), "POLICY");
                targetMapper.updateStatus(target.getId(), "SENT", null);
            } catch (Exception e) {
                log.error("INAPP 발송 실패: targetId={}, userId={}",
                        target.getId(), target.getUserId(), e);
                targetMapper.updateStatus(target.getId(), "FAILED", "발송 오류");
            }
        }
    }

    /** dispatch_type → 수신 동의 카테고리 매핑. */
    private String categoryFor(String dispatchType) {
        if (dispatchType == null) {
            return "ANNOUNCEMENT";
        }
        return switch (dispatchType) {
            case "APPLICATION_OPEN", "CLOSING_SOON", "RESULT_ANNOUNCED" -> "POLICY_MATCH";
            case "ANNOUNCEMENT" -> "ANNOUNCEMENT";
            case "REMINDER" -> "REMINDER";
            default -> "ANNOUNCEMENT";
        };
    }
}
