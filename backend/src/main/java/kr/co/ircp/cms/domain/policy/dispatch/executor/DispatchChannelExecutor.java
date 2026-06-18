package kr.co.ircp.cms.domain.policy.dispatch.executor;

import kr.co.ircp.cms.domain.policy.dispatch.dto.NotificationDispatchTargetWithUser;
import kr.co.ircp.cms.domain.policy.dispatch.entity.NotificationDispatchSchedule;

import java.util.List;

/**
 * 발송 채널 실행기 — 채널별(EMAIL/INAPP) 발송 전략.
 *
 * <p>SPEC-CMS-NOTI-EXT-001 — Spring이 구현체를 자동 수집하여 채널에 맞는 실행기를 선택한다.
 */
public interface DispatchChannelExecutor {

    /** 지원 채널 식별자 (예: "EMAIL", "INAPP"). */
    String getSupportedChannel();

    /**
     * 대상 목록을 발송한다. 대상별 옵트아웃·멱등성·실패를 개별 처리하고
     * 각 대상의 status를 갱신한다(SENT / FAILED / SKIPPED_OPTOUT).
     */
    void execute(NotificationDispatchSchedule schedule, List<NotificationDispatchTargetWithUser> targets);
}
